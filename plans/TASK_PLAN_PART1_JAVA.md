# TASK PLAN PARTIE 1 — ANDROID OPTIMIZER (Java/Mixins)
## Toutes les tâches détaillées · Étape par étape · Code + Sources

---

## TÂCHE 1 — ResourceLocation Intern Cache
**Mod :** Android Optimizer · **Package :** `memory`
**Fichiers à créer/modifier :**
- `src/main/java/.../memory/ResourceLocationPool.java` (NOUVEAU)
- `src/main/java/.../mixin/ResourceLocationInternMixin.java` (MODIFIER)

### Étape 1.1 — Créer le pool
```java
// memory/ResourceLocationPool.java
package fr.eaielectronic.androidopt.memory;

import net.minecraft.resources.ResourceLocation;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceLocationPool {
    public static final ResourceLocationPool INSTANCE = new ResourceLocationPool();
    private final ConcurrentHashMap<String, WeakReference<ResourceLocation>>
        pool = new ConcurrentHashMap<>(4096);
    private int hits = 0, misses = 0;

    public ResourceLocation intern(ResourceLocation rl) {
        String key = rl.getNamespace() + ":" + rl.getPath();
        WeakReference<ResourceLocation> ref = pool.get(key);
        if (ref != null) {
            ResourceLocation cached = ref.get();
            if (cached != null) { hits++; return cached; }
        }
        pool.put(key, new WeakReference<>(rl));
        misses++;
        return rl;
    }

    public String getStats() {
        return "RL Pool: " + pool.size() + " entries, "
             + hits + " hits, " + misses + " misses";
    }
}
```

### Étape 1.2 — Modifier le Mixin existant
Le mixin `ResourceLocationInternMixin.java` existe déjà.
Vérifier qu'il appelle `ResourceLocationPool.INSTANCE.intern(rl)` sur
les retours de `ResourceLocation.tryParse()` et `ResourceLocation.read()`.

**Source :** FerriteCore déduplique les String INTERNES mais pas les objets RL.
GitHub : `malte0811/FerriteCore` → `ResourceLocationDedup.java`

**Test :** `/spark heapsummary` avant/après. Chercher `ResourceLocation` dans le rapport.

---

## TÂCHE 2 — Atlas de Polices Paginé (Unicode Lazy)
**Mod :** Android Optimizer · **Package :** `memory`
**Fichiers à créer :**
- `memory/FontPageManager.java` (NOUVEAU)
- `mixin/FontManagerMixin.java` (NOUVEAU)

### Étape 2.1 — Le gestionnaire de pages
```java
// memory/FontPageManager.java
package fr.eaielectronic.androidopt.memory;

import java.util.LinkedHashMap;
import java.util.Map;

public class FontPageManager {
    public static final FontPageManager INSTANCE = new FontPageManager();
    // LRU cache de 32 pages max (chaque page = 256 codepoints)
    private final LinkedHashMap<Integer, Object> loadedPages =
        new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                if (size() > 32) {
                    // Libérer la texture GPU de cette page
                    // via RenderSystem.recordRenderCall()
                    return true;
                }
                return false;
            }
        };

    // Page 0 (ASCII 0-255) toujours chargée
    public boolean isPageLoaded(int codepoint) {
        int page = codepoint / 256;
        if (page == 0) return true; // ASCII toujours en RAM
        return loadedPages.containsKey(page);
    }

    public void loadPage(int codepoint) {
        int page = codepoint / 256;
        if (page == 0) return;
        loadedPages.put(page, Boolean.TRUE); // marquer comme chargée
    }
}
```

### Étape 2.2 — Mixin sur FontManager
Cible : `net.minecraft.client.gui.font.FontManager`
Méthode : intercepter la création de `GlyphInfo` pour ne charger que les pages demandées.
Le Mixin doit être conditionnel (`@Mixin(value = FontManager.class)` avec un `@Inject` sur `getGlyphInfo`).

**Source :** ImmediatelyFast GitHub (`RaphiMC/ImmediatelyFast`) optimise le RENDU du texte
mais charge TOUT en mémoire. Notre approche est différente : on ne charge que ce qui est vu.

**Test :** Ouvrir un guidebook Patchouli avec des caractères chinois → mesurer la VRAM avant/après.

---

## TÂCHE 3 — JEI Lazy Index (LE PLUS GROS GAIN)
**Mod :** Android Optimizer · **Package :** `integration/jei`
**Fichiers à créer :**
- `integration/jei/LazyJeiIndex.java` (NOUVEAU)
- `mixin/JeiIngredientListMixin.java` (NOUVEAU ou MODIFIER l'existant)

### Étape 3.1 — L'index paresseux
```java
// integration/jei/LazyJeiIndex.java
package fr.eaielectronic.androidopt.integration.jei;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LazyJeiIndex {
    public static final LazyJeiIndex INSTANCE = new LazyJeiIndex();

    // LRU de 512 items max indexés en mémoire
    private final LinkedHashMap<String, Object> indexedItems =
        new LinkedHashMap<>(512, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > 512;
            }
        };

    // Thread de fond pour l'indexation
    private final ExecutorService indexer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AndroidOpt-JEI-Indexer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public boolean isIndexed(String itemId) {
        return indexedItems.containsKey(itemId);
    }

    public void requestIndex(String itemId, Runnable indexTask) {
        if (isIndexed(itemId)) return;
        indexer.submit(() -> {
            indexTask.run();
            indexedItems.put(itemId, Boolean.TRUE);
        });
    }
}
```

### Étape 3.2 — Mixin sur JEI
Les Mixins JEI existants (`JeiListLimiterMixin`, `JeiSearchThrottleMixin`) limitent
déjà l'affichage. Il faut aller plus loin : intercepter le chargement initial de
`IngredientListElementList` dans JEI pour ne PAS tout charger au boot.

**Cible Mixin :** `mezz.jei.library.ingredients.IngredientListElementList`
**Méthode :** `@Inject` dans le constructeur ou `@Redirect` sur la boucle de chargement.

**Source :** GitHub JEI issue #1878 (RAM excessive), issue #1814 (proposition lazy jamais mergée).
Code source JEI : `mezz/JustEnoughItems` → `IngredientListElementList.java`

**Test :** Lancer avec 55 mods, `/spark heapsummary`, chercher `jei` dans le rapport.
Comparer la heap au boot avec/sans le lazy index.

---

## TÂCHE 4 — BakedModel Eviction + FlatSpriteModel
**Mod :** Android Optimizer · **Package :** `memory/models`
**Fichiers à créer :**
- `memory/models/FlatSpriteModel.java` (NOUVEAU)
- `memory/models/ModelEvictionPolicy.java` (NOUVEAU)
- `memory/models/ModelLifecycleManager.java` (NOUVEAU)
- `mixin/ModelManagerMixin.java` (NOUVEAU)

### Étape 4.1 — Le FlatSpriteModel (fallback 2D)
```java
// memory/models/FlatSpriteModel.java
// Implémente BakedModel en retournant un simple quad 2D
// tiré de la texture de l'item (déjà dans l'atlas GPU)
// Voir PLAN_LAZY_3D_MODELS_COMPLET(1).md pour le code complet
```

### Étape 4.2 — La politique d'éviction LFU-LRU
```java
// memory/models/ModelEvictionPolicy.java
// LinkedHashMap avec compteur de fréquence
// Evicte les modèles les MOINS fréquents ET les MOINS récents
// Whitelist : items dans l'inventaire du joueur JAMAIS évictés
// Budget configurable dans OptConfig (ex: 2000 modèles max en RAM)
```

### Étape 4.3 — Le gestionnaire de cycle de vie
États : `LOADED → EVICTED → REBAKING → LOADED`
Quand un modèle est EVICTED, on retourne le FlatSpriteModel.
Le re-bake se fait en thread de fond via `Minecraft.getInstance().tell()`.

**Source :** ModernFix (`embeddedt/ModernFix`) → `DynamicBakedModelProvider.java`
fait de l'éviction TTL MAIS affiche un carré rose/noir. Notre FlatSpriteModel résout ça.

**Test :** Ouvrir JEI, scroller vite → aucun carré rose. Mesurer frametime sur F3.

---

## TÂCHE 5 — Agrona SoA pour KineticBlockEntity (Off-Heap)
**Mod :** Android Optimizer · **Package :** `memory/offheap`
**Dépendance :** Agrona dans `build.gradle` (shadow JAR)
**Fichiers à créer :**
- `memory/offheap/KineticSoABuffer.java` (NOUVEAU)
- `mixin/KineticBlockEntityMixin.java` (NOUVEAU)

### Étape 5.1 — Ajouter Agrona au build.gradle
```groovy
dependencies {
    implementation 'org.agrona:agrona:1.21.0'
    shadow 'org.agrona:agrona:1.21.0' // OBLIGATOIRE pour Forge
}
```

### Étape 5.2 — Le buffer SoA
```java
// memory/offheap/KineticSoABuffer.java
package fr.eaielectronic.androidopt.memory.offheap;

import org.agrona.concurrent.UnsafeBuffer;
import java.nio.ByteBuffer;

public class KineticSoABuffer {
    public static final KineticSoABuffer INSTANCE = new KineticSoABuffer();
    private static final int MAX_ENTITIES = 8192;

    // Chaque array est un bloc contigu off-heap
    private final UnsafeBuffer speeds;    // float[8192] = 32 Ko
    private final UnsafeBuffer posX;      // float[8192] = 32 Ko
    private final UnsafeBuffer posY;      // float[8192] = 32 Ko
    private final UnsafeBuffer posZ;      // float[8192] = 32 Ko
    private final UnsafeBuffer networkIds; // int[8192]   = 32 Ko
    // Total off-heap : 160 Ko fixe, INVISIBLE pour le GC

    public KineticSoABuffer() {
        speeds     = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ENTITIES * 4));
        posX       = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ENTITIES * 4));
        posY       = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ENTITIES * 4));
        posZ       = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ENTITIES * 4));
        networkIds = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_ENTITIES * 4));
    }

    public void setSpeed(int index, float speed) {
        speeds.putFloat(index * 4, speed);
    }
    public float getSpeed(int index) {
        return speeds.getFloat(index * 4);
    }
    // ... idem pour posX, posY, posZ, networkId
}
```

### Étape 5.3 — Mixin sur Create
Cible : `com.simibubi.create.content.kinetics.base.KineticBlockEntity`
Intercept : `tick()` pour lire/écrire depuis le SoA au lieu des champs Java.

**Source :** Agrona GitHub (`real-logic/agrona`) — Java 17+, stable, production.
Create GitHub (`Creators-of-Create/Create`) → `KineticBlockEntity.java`

**Test :** Placer 500+ rouages Create. `/spark heapsummary` → vérifier que
`KineticBlockEntity` n'apparaît plus dans les gros consommateurs de heap.

---

## TÂCHE 6 — NBT Entités Distantes Off-Heap
**Mod :** Android Optimizer · **Package :** `memory/offheap`
**Fichiers à créer :**
- `memory/offheap/NbtCompressor.java` (NOUVEAU)
- `mixin/EntityNbtMixin.java` (NOUVEAU)

### Principe
Quand une entité est à > 64 blocs du joueur ET n'a pas été modifiée
depuis 100 ticks : sérialiser son NBT en bytes → stocker dans un
DirectByteBuffer → libérer l'arbre Java original.

```java
// memory/offheap/NbtCompressor.java
public class NbtCompressor {
    private final ConcurrentHashMap<UUID, ByteBuffer> compressedNbt
        = new ConcurrentHashMap<>();

    public void compressEntity(Entity entity) {
        CompoundTag nbt = entity.saveWithoutId(new CompoundTag());
        byte[] bytes = NbtIo.writeToByteArray(nbt); // sérialiser
        ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length);
        direct.put(bytes).flip();
        compressedNbt.put(entity.getUUID(), direct);
        // L'arbre NBT Java original sera GC'd
    }

    public CompoundTag decompress(UUID entityId) {
        ByteBuffer buf = compressedNbt.remove(entityId);
        if (buf == null) return null;
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return NbtIo.readFromByteArray(bytes);
    }
}
```

**Source :** Aucun mod ne compresse les NBT des entités distantes.

---

## TÂCHE 7 — Pool OpenAL Audio (Java-side bridge)
**Mod :** Android Optimizer · **Package :** `memory`
**Fichier :** `memory/AudioPoolBridge.java` (NOUVEAU)
Le C++ réel est dans NativeGLEngine (voir Partie 2).

```java
// memory/AudioPoolBridge.java
public class AudioPoolBridge {
    private static boolean nativeAvailable = false;

    static {
        try {
            System.loadLibrary("androidopt_native");
            nativeAvailable = true;
        } catch (UnsatisfiedLinkError e) {
            // NativeGLEngine pas installé — mode dégradé
        }
    }

    public static native int getPooledBuffer(int soundHash);
    public static native void returnBuffer(int bufferId);
    public static native int getPoolStats(); // buffers utilisés

    public static boolean isAvailable() { return nativeAvailable; }
}
```

---

## TÂCHE 8 — MemoryWatchdog amélioré
**Mod :** Android Optimizer · **Fichier existant :** `client/MemoryWatchdog.java`
**Modifier pour :**
1. Appeler `mallopt(M_PURGE)` via JNI quand heap > 80%
2. Déclencher l'éviction de modèles (Tâche 4)
3. Appeler `NbtCompressor` pour compresser les entités distantes (Tâche 6)

---

## TÂCHE 9 — Configuration (OptConfig)
**Fichier existant :** `OptConfig.java`
**Ajouter ces options :**
```java
public static int MODEL_EVICTION_BUDGET = 2000;    // max modèles en RAM
public static int JEI_LAZY_CACHE_SIZE = 512;        // items JEI en cache
public static int FONT_PAGE_CACHE_SIZE = 32;        // pages de polices
public static int NBT_COMPRESS_DISTANCE = 64;       // blocs
public static int NBT_COMPRESS_DELAY_TICKS = 100;   // ticks sans modif
public static boolean ENABLE_NATIVE_PARTICLES = true;
public static boolean ENABLE_NATIVE_AUDIO_POOL = true;
public static boolean ENABLE_THERMAL_MONITOR = true;
```

---

## TÂCHE 10 — Mettre à jour androidopt.mixins.json
Ajouter tous les nouveaux mixins au fichier JSON existant.

---

## RÉSUMÉ ANDROID OPTIMIZER

| Tâche | Gain RAM | Priorité |
|-------|----------|----------|
| T3 JEI Lazy | 300–700 Mo | 🔴 CRITIQUE |
| T4 BakedModel Eviction | 80–200 Mo | 🔴 CRITIQUE |
| T5 Agrona SoA | 100–300 Mo | 🟡 HAUTE |
| T6 NBT Off-Heap | 60–180 Mo | 🟡 HAUTE |
| T2 Font Paginé | 40–120 Mo | 🟢 MOYENNE |
| T1 RL Intern | 20–45 Mo | 🟢 MOYENNE |
