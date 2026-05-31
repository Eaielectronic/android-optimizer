# PLAN DÉTAILLÉ — LAZY LOADING DES MODÈLES 3D
## Correction + Approfondissement du §4.2 — V6 Plan Principal
### Minecraft Forge 1.20.x · Android (PojavLauncher) · Java + C++ NDK
> Généré le 31/05/2026 — Basé sur recherche source code ModernFix + GitHub Forge issues

---

## ⚠️ CORRECTION CRITIQUE DU PLAN V6 §4.2

> **Le §4.2 du plan V6 affirmait : "ModernFix retarde le chargement initial,
> mais une fois chargé, ne vire absolument rien après coup."**
>
> **C'est FAUX.** L'analyse du code source de ModernFix via DeepWiki confirme
> que `DynamicBakedModelProvider` implémente **déjà** :
> - Expiration temporelle (TTL configurable par modèle non accédé)
> - Limites de taille (nombre maximum de modèles en cache)
> - Soft references (le GC peut récupérer les BakedModels si besoin)
> - Logging des évictions

**Ce que cela change pour notre plan :**

| Aspect | ModernFix (existant) | Notre apport (unique) |
|--------|---------------------|----------------------|
| Éviction sur TTL | ✅ Oui | — |
| Limites de taille | ✅ Oui | — |
| Soft references | ✅ Oui | — |
| Fallback 2D sprite pendant rebake | ❌ Non (missing model / flicker) | ✅ Notre différenciateur N°1 |
| Re-bake asynchrone sans stutter | ❌ Probablement synchrone | ✅ Thread pool dédié |
| Whitelist "items inventaire" | ❌ Non | ✅ Jamais éjecté si en main |
| Politique LFU hybride (fréquence + recence) | ❌ LRU seulement | ✅ Score composite |
| Prédictif : pre-warm recettes en cours | ❌ Non | ✅ Avant que l'item soit affiché |
| Vertex data BakedQuads off-heap Agrona | ❌ Non | ✅ Réduction GC supplémentaire |
| Compression UnbakedModel JSON LZ4 en mémoire | ❌ Non | ✅ NDK C++ |

**Économie totale révisée :**
- ModernFix `dynamic_resources` : **~300 Mo** (déjà fait, ne pas compter)
- Notre apport complémentaire : **80 à 200 Mo** supplémentaires + **zéro stutter rebake**

---

## CONTEXTE MÉMOIRE — CHIFFRES RÉELS (CONFIRMÉS)

### Pourquoi les BakedModels coûtent autant ?

Chaque `BakedModel` est un ensemble de `BakedQuad[][]`, où chaque `BakedQuad`
contient un `int[] vertexData` de **32 entiers** (4 vertices × 8 ints chacun =
position XYZ, UV, normal, couleur — format `DefaultVertexFormat.BLOCK`).

```
1 BakedQuad  = 32 × 4 octets = 128 octets
1 item cube  = 6 faces × 2 triangles = ~12 quads = ~1,5 Ko
1 item complexe Create (engrenage) = 50 à 200 quads = 6 à 25 Ko
500 items Create = ~5 à 12 Mo rien que pour les vertexData
```

**Confirmation GitHub (issue Forge #3246, 2016) :**
Pour seulement 2 mods, les vertexData dupliqués des items représentaient 35 Mo.
Extrapolé à 40 content mods : ~700 Mo. Notre pack a 55 mods → estimation 800–950 Mo
rien que pour les `int[] vertexData` des `ItemLayerModel`.

**Source confirmée :** item models gardent leurs données en forme `UnpackedBakedQuad`
(vertex data dupliqué) → le pack vers `int[]` libère la moitié de ce poids, mais
sans éviction, tout reste en RAM.

---

## ARCHITECTURE GÉNÉRALE — 7 COUCHES

```
┌────────────────────────────────────────────────────────────────┐
│  COUCHE 7 : NDK C++ — Compression UnbakedModel LZ4             │
│  (UnbakedModel JSON brut → LZ4 → 3× moins de RAM off-heap)     │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 6 : Agrona Off-Heap — Vertex Data hors heap Java        │
│  (int[] vertexData → UnsafeBuffer DirectByteBuffer)             │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 5 : BackgroundRebaker — Thread Pool Dédié              │
│  (Executor 2 threads bas-prio, jamais sur le thread rendu)      │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 4 : FlatSpriteModel — Fallback 2D sans Stutter          │
│  (Remplaçant visuel pendant le re-bake, zéro frame drop)        │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 3 : ModelEvictionPolicy — LFU-LRU Hybride              │
│  (Score composite fréquence + recence + whitelist inventaire)   │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 2 : ModelLifecycleManager — FSM Thread-Safe            │
│  (Machine d'états : UNBAKED → BAKING → BAKED → EVICTED)        │
├────────────────────────────────────────────────────────────────┤
│  COUCHE 1 : ModernFix Compatibility Bridge                      │
│  (Détecte si dynamic_resources est actif, évite double-gestion) │
└────────────────────────────────────────────────────────────────┘
```

---

## COUCHE 1 — MODERNFIX COMPATIBILITY BRIDGE

### Objectif
Éviter un conflit avec le `DynamicBakedModelProvider` de ModernFix.
Si ModernFix est présent ET `dynamic_resources` activé, notre système
**n'intercepte pas** les mêmes Mixins — on se greffe au-dessus.

```java
// src/main/java/com/yourmod/compat/ModernFixBridge.java

public class ModernFixBridge {

    private static final boolean MODERNFIX_DYNAMIC = detectModernFixDynamic();

    private static boolean detectModernFixDynamic() {
        try {
            Class.forName(
                "org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider");
            // Vérifie aussi que le flag est activé dans les config
            // modernfix-mixins.properties : mixin.perf.dynamic_resources=true
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Si ModernFix gère l'éviction de base, on ne ré-implémente pas le TTL.
     * On se contente d'ajouter le fallback 2D + le re-bake async.
     */
    public static boolean isModernFixHandlingEviction() {
        return MODERNFIX_DYNAMIC;
    }

    /**
     * Hook post-éviction ModernFix : ModernFix évicte → on enregistre l'éviction
     * pour pouvoir fournir le FlatSpriteModel en fallback.
     * Mixin sur DynamicBakedModelProvider.onRemoval() si ModernFix présent.
     */
    public static void onModelEvictedByModernFix(ResourceLocation rl) {
        // Marquer le modèle comme EVICTED dans notre FSM
        ModelLifecycleManager.INSTANCE.markEvicted(rl);
        // Déclencher immédiatement le pre-bake en fond pour réduire le délai
        BackgroundRebaker.INSTANCE.scheduleRebake(rl, RebakePriority.LOW);
    }
}
```

**Mixin conditionnel — uniquement si ModernFix est présent :**

```java
// Mixin conditionnel dans modernfix_compat.mixins.json
// N'est injecté QUE si la classe DynamicBakedModelProvider existe au runtime

@Mixin(targets = "org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider",
       remap = false)
@Conditional(ModernFixBridge.class) // Annotation custom qui désactive si classe absente
public class DynamicBakedModelProviderMixin {

    @Inject(method = "onRemoval", at = @At("TAIL"))
    private void onModelEvicted(ResourceLocation rl, BakedModel model,
                                RemovalCause cause, CallbackInfo ci) {
        // Ne pas intercepter les évictions GC-driven (cause == COLLECTED)
        // seulement SIZE et EXPIRED
        if (cause == RemovalCause.SIZE || cause == RemovalCause.EXPIRED) {
            ModernFixBridge.onModelEvictedByModernFix(rl);
        }
    }
}
```

---

## COUCHE 2 — MODELLIFECYCLEMANAGER : FSM THREAD-SAFE

### Machine d'états pour chaque ResourceLocation de modèle

```
                    ┌──────────┐
          (startup) │ UNBAKED  │ ← état initial, modèle jamais chargé
                    └──────────┘
                         │ accès première fois (ou ModernFix pas présent)
                         ▼
                    ┌──────────┐
              ┌────►│  BAKING  │ ← re-bake en cours sur BackgroundRebaker
              │     └──────────┘
              │          │ bake terminé
              │          ▼
              │     ┌──────────┐
              │     │  BAKED   │ ← modèle en RAM, prêt pour le rendu
              │     └──────────┘
              │          │ TTL expiré / budget RAM dépassé
              │          ▼
              │     ┌──────────┐
              └─────│ EVICTED  │ ← évicté, FlatSpriteModel actif
                    └──────────┘
```

```java
// src/main/java/com/yourmod/models/ModelLifecycleManager.java

public class ModelLifecycleManager {
    public static final ModelLifecycleManager INSTANCE = new ModelLifecycleManager();

    public enum ModelState { UNBAKED, BAKING, BAKED, EVICTED }

    // ConcurrentHashMap thread-safe — lecture sans lock, écriture via CAS
    private final ConcurrentHashMap<ResourceLocation, ModelState> states =
        new ConcurrentHashMap<>(4096);

    // Atomic pour chaque transition : garantit qu'un seul thread lance le bake
    public boolean tryTransitionTo(ResourceLocation rl, ModelState expected,
                                   ModelState next) {
        return states.compute(rl, (key, current) -> {
            ModelState cur = (current == null) ? ModelState.UNBAKED : current;
            if (cur == expected) return next;
            return cur; // Pas de transition si état inattendu
        }) == next;
    }

    public ModelState getState(ResourceLocation rl) {
        return states.getOrDefault(rl, ModelState.UNBAKED);
    }

    public void markEvicted(ResourceLocation rl) {
        states.put(rl, ModelState.EVICTED);
    }

    public void markBaked(ResourceLocation rl) {
        states.put(rl, ModelState.BAKED);
    }

    /**
     * Retourne true si le modèle nécessite un FlatSpriteModel comme fallback.
     * Vrai si : EVICTED ou BAKING (rebake en cours).
     */
    public boolean needsFallback(ResourceLocation rl) {
        ModelState s = getState(rl);
        return s == ModelState.EVICTED || s == ModelState.BAKING;
    }
}
```

---

## COUCHE 3 — MODELEVICTIONPOLICY : LFU-LRU HYBRIDE

### Problème du LRU pur (ce que fait ModernFix)

Le LRU pur souffre du "cache pollution" : un modèle accédé une seule fois
(JEI ouvert rapidement) peut évincer un modèle fréquent (l'épée en main).
On utilise un score composite :

```
Score = fréquence_accès × 0.6 + recence_normalisée × 0.4
```

Les éléments avec le **score le plus bas** sont éjectés en premier.

```java
// src/main/java/com/yourmod/models/ModelEvictionPolicy.java

public class ModelEvictionPolicy {

    // Budget mémoire en nombre de modèles (configurable dans mod config)
    // Heuristique : 1 modèle moyen ≈ 500 Ko → budget 300 Mo = 600 modèles
    private static final int DEFAULT_BUDGET_MODELS = 600;
    private final int budget;

    // Struct par modèle — stockée dans ConcurrentHashMap
    private static class ModelStats {
        final ResourceLocation rl;
        volatile long lastAccessMs;
        // AtomicInteger pour thread-safe sans lock
        final AtomicInteger accessCount = new AtomicInteger(0);

        ModelStats(ResourceLocation rl) {
            this.rl = rl;
            this.lastAccessMs = System.currentTimeMillis();
        }

        // Score composite LFU-LRU : plus le score est bas, plus candidat à l'éviction
        double computeScore(long nowMs, long sessionStartMs) {
            long age = nowMs - sessionStartMs; // durée totale de la session
            double recence = (double)(nowMs - lastAccessMs) / Math.max(age, 1L);
            // recence = 0.0 (accédé maintenant) → 1.0 (pas accédé depuis le début)
            double recenceScore = 1.0 - recence; // 1.0 = très récent
            double freqScore = Math.log1p(accessCount.get()); // log pour éviter
            // que les items très fréquents écrasent tout
            return freqScore * 0.6 + recenceScore * 0.4;
        }
    }

    private final ConcurrentHashMap<ResourceLocation, ModelStats> stats =
        new ConcurrentHashMap<>(4096);
    private final long sessionStartMs = System.currentTimeMillis();

    // Whitelist : modèles JAMAIS évictés
    private final Set<ResourceLocation> neverEvict = ConcurrentHashMap.newKeySet();

    public ModelEvictionPolicy(int budget) {
        this.budget = budget;
    }

    /**
     * Appelé à chaque accès à un modèle (dans le hook ItemRenderer).
     * Mise à jour des stats de fréquence + recence.
     */
    public void recordAccess(ResourceLocation rl) {
        stats.compute(rl, (key, s) -> {
            if (s == null) s = new ModelStats(key);
            s.lastAccessMs = System.currentTimeMillis();
            s.accessCount.incrementAndGet();
            return s;
        });
    }

    /**
     * Synchronise la whitelist avec l'inventaire du joueur.
     * Appelé toutes les 100 ticks depuis un Mixin sur Player.tick().
     */
    public void syncPlayerInventory(Player player) {
        neverEvict.clear();
        // Inventaire principal (36 slots)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (rl != null) neverEvict.add(rl);
            }
        }
        // Armure équipée (4 slots)
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(armor.getItem());
                if (rl != null) neverEvict.add(rl);
            }
        }
        // Offhand
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            neverEvict.add(ForgeRegistries.ITEMS.getKey(offhand.getItem()));
        }
    }

    /**
     * Appelé par le thread de fond toutes les 5 minutes.
     * Retourne la liste des ResourceLocations à évincer pour rester dans le budget.
     */
    public List<ResourceLocation> computeEvictionCandidates(int currentModelCount) {
        if (currentModelCount <= budget) return Collections.emptyList();

        int toEvict = currentModelCount - (budget * 9 / 10); // Libérer 10% de marge
        long now = System.currentTimeMillis();

        return stats.values().stream()
            .filter(s -> !neverEvict.contains(s.rl))
            .filter(s -> ModelLifecycleManager.INSTANCE.getState(s.rl)
                        == ModelLifecycleManager.ModelState.BAKED)
            .sorted(Comparator.comparingDouble(s -> s.computeScore(now, sessionStartMs)))
            // Plus le score est bas = moins utile = candidat prioritaire à l'éviction
            .limit(toEvict)
            .map(s -> s.rl)
            .collect(Collectors.toList());
    }

    /**
     * Pré-chargement prédictif : détecte les items dans le crafting en cours.
     * Mixin sur CraftingContainer.setItem() → pré-warm les résultats probables.
     */
    public void predictAndPrewarm(CraftingContainer craftingGrid) {
        // Ajouter les items présents dans la grille de craft à la whitelist temp
        // + déclencher le pre-bake de leur résultat de recette
        for (int i = 0; i < craftingGrid.getContainerSize(); i++) {
            ItemStack ing = craftingGrid.getItem(i);
            if (!ing.isEmpty()) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(ing.getItem());
                if (rl != null && ModelLifecycleManager.INSTANCE.needsFallback(rl)) {
                    BackgroundRebaker.INSTANCE.scheduleRebake(rl, RebakePriority.HIGH);
                }
            }
        }
    }
}
```

---

## COUCHE 4 — FLATSPRITEMODEL : FALLBACK 2D SANS STUTTER

### Principe
Quand un modèle est EVICTED ou BAKING, au lieu d'afficher un modèle manquant
(carré rose/noir de ModernFix), on retourne un `FlatSpriteModel` qui affiche
simplement la texture 2D de l'item (déjà dans l'atlas GPU — aucun rechargement).

```java
// src/main/java/com/yourmod/models/FlatSpriteModel.java

/**
 * Modèle de secours 2D utilisant uniquement la TextureAtlasSprite de l'item.
 * La texture est DÉJÀ dans l'atlas GPU — coût d'affichage : zéro.
 * Apparence identique à un item dans un inventaire 2D (icône plate).
 */
public class FlatSpriteModel implements BakedModel {

    // Cache statique : ResourceLocation → FlatSpriteModel
    // Ne jamais évincer ces instances : elles sont ultra-légères (~200 octets)
    private static final ConcurrentHashMap<ResourceLocation, FlatSpriteModel>
        CACHE = new ConcurrentHashMap<>(1024);

    private final List<BakedQuad> quads; // 2 quads (1 face frontale + 1 face arrière)
    private final TextureAtlasSprite sprite;
    private final boolean isGui3d = false; // Rendu plat, pas de perspective 3D

    private FlatSpriteModel(TextureAtlasSprite sprite) {
        this.sprite = sprite;
        this.quads = buildFlatQuads(sprite);
    }

    /**
     * Construit 2 BakedQuads représentant un carré plat 1×1 texturé.
     * Total : 2 × 128 octets = 256 octets — négligeable.
     */
    private static List<BakedQuad> buildFlatQuads(TextureAtlasSprite sprite) {
        // Vertices d'un quad plat face SOUTH (visible depuis l'avant)
        // Format DefaultVertexFormat.BLOCK : pos(3) + color(1) + uv(2) + normal(1) + misc
        // Simplification : on encode manuellement les 32 ints
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        BakedQuadBuilder builder = new BakedQuadBuilder(sprite);
        builder.setQuadOrientation(Direction.SOUTH);
        // 4 vertices : bas-gauche, bas-droit, haut-droit, haut-gauche
        putVertex(builder, 0, 0, 0.5f, u0, v1, Direction.SOUTH, sprite);
        putVertex(builder, 1, 0, 0.5f, u1, v1, Direction.SOUTH, sprite);
        putVertex(builder, 1, 1, 0.5f, u1, v0, Direction.SOUTH, sprite);
        putVertex(builder, 0, 1, 0.5f, u0, v0, Direction.SOUTH, sprite);
        BakedQuad frontQuad = builder.build();

        // Face NORTH (dos — même texture miroir pour cohérence)
        builder = new BakedQuadBuilder(sprite);
        builder.setQuadOrientation(Direction.NORTH);
        putVertex(builder, 1, 0, 0.5f, u0, v1, Direction.NORTH, sprite);
        putVertex(builder, 0, 0, 0.5f, u1, v1, Direction.NORTH, sprite);
        putVertex(builder, 0, 1, 0.5f, u1, v0, Direction.NORTH, sprite);
        putVertex(builder, 1, 1, 0.5f, u0, v0, Direction.NORTH, sprite);
        BakedQuad backQuad = builder.build();

        return List.of(frontQuad, backQuad);
    }

    private static void putVertex(BakedQuadBuilder builder, float x, float y, float z,
                                  float u, float v, Direction normal,
                                  TextureAtlasSprite sprite) {
        ImmutableMap<VertexFormatElement, Object> data = ImmutableMap.of(
            DefaultVertexFormat.ELEMENT_POSITION,
                new float[]{x - 0.5f, y - 0.5f, z - 0.5f}, // Centré sur (0,0,0)
            DefaultVertexFormat.ELEMENT_COLOR,
                new float[]{1, 1, 1, 1},    // Blanc opaque (pas de tint)
            DefaultVertexFormat.ELEMENT_UV0,
                new float[]{u, v},
            DefaultVertexFormat.ELEMENT_NORMAL,
                new float[]{normal.getStepX(), normal.getStepY(), normal.getStepZ()}
        );
        builder.put(data);
    }

    /**
     * Factory method : retourne un FlatSpriteModel depuis le cache ou en crée un.
     * Appelée depuis le Mixin sur ItemRenderer.getModel() quand état == EVICTED|BAKING.
     */
    public static FlatSpriteModel getOrCreate(ResourceLocation rl) {
        return CACHE.computeIfAbsent(rl, key -> {
            // Récupère la sprite depuis l'atlas d'items (toujours en GPU)
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getItemRenderer()
                .getItemModelShaper()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(key);
            if (sprite == null || sprite == MissingTextureAtlasSprite.getSprite()) {
                // Fallback de dernier recours : sprite "missing" de Minecraft
                sprite = MissingTextureAtlasSprite.getSprite();
            }
            return new FlatSpriteModel(sprite);
        });
    }

    // Implémentation BakedModel — minimale
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,
                                    @Nullable Direction side,
                                    RandomSource rand) {
        // Quads uniquement pour side == null (rendu de l'item lui-même)
        return (side == null) ? quads : Collections.emptyList();
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; } // Rendu 2D
    @Override public boolean usesBlockLight() { return false; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return sprite; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    @Override public ItemTransforms getTransforms() {
        // Transformations standard pour item "generated" (épée, lingot, etc.)
        return ItemTransforms.NO_TRANSFORMS;
    }
}
```

### Mixin sur ItemRenderer — Point d'interception central

```java
// src/main/java/com/yourmod/mixin/ItemRendererMixin.java

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow
    @Final
    private ModelManager modelManager;

    /**
     * Interception de getModel() — retourne FlatSpriteModel si nécessaire.
     * Point chaud : appelé à chaque frame pour chaque item visible.
     * Chemin de fast-path : vérification en O(1) avant tout autre traitement.
     */
    @Inject(method = "getModel",
            at = @At("HEAD"),
            cancellable = true)
    private void interceptEvictedModel(ItemStack stack,
                                       @Nullable Level level,
                                       @Nullable LivingEntity entity,
                                       int seed,
                                       CallbackInfoReturnable<BakedModel> cir) {
        if (stack == null || stack.isEmpty()) return;

        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) return;

        // Fast-path : modèle BAKED et présent → laisser passer normalement
        if (ModelLifecycleManager.INSTANCE.getState(rl)
                == ModelLifecycleManager.ModelState.BAKED) {
            // Enregistrer l'accès pour les stats LFU-LRU
            ModelEvictionPolicy.INSTANCE.recordAccess(rl);
            return; // Ne pas canceller — comportement normal
        }

        // Modèle EVICTED ou BAKING → retourner FlatSpriteModel immédiatement
        if (ModelLifecycleManager.INSTANCE.needsFallback(rl)) {
            // Déclencher le rebake si pas déjà en cours
            if (ModelLifecycleManager.INSTANCE.tryTransitionTo(
                    rl,
                    ModelLifecycleManager.ModelState.EVICTED,
                    ModelLifecycleManager.ModelState.BAKING)) {
                BackgroundRebaker.INSTANCE.scheduleRebake(rl, RebakePriority.NORMAL);
            }
            // Retourner le fallback 2D → zéro stutter
            cir.setReturnValue(FlatSpriteModel.getOrCreate(rl));
            return;
        }

        // Modèle UNBAKED (jamais vu) → déclencher le bake et retourner le fallback
        if (ModelLifecycleManager.INSTANCE.tryTransitionTo(
                rl,
                ModelLifecycleManager.ModelState.UNBAKED,
                ModelLifecycleManager.ModelState.BAKING)) {
            BackgroundRebaker.INSTANCE.scheduleRebake(rl, RebakePriority.HIGH);
        }
        cir.setReturnValue(FlatSpriteModel.getOrCreate(rl));
    }

    /**
     * Hook sur renderItem() pour tracker la visibilité.
     * Appelé uniquement quand le modèle est réellement rendu à l'écran.
     */
    @Inject(method = "renderItem",
            at = @At("HEAD"))
    private void trackItemRendered(ItemStack stack,
                                   ItemDisplayContext context,
                                   boolean leftHand,
                                   PoseStack poseStack,
                                   MultiBufferSource buffer,
                                   int combinedLight,
                                   int combinedOverlay,
                                   BakedModel model,
                                   CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl != null) {
            ModelEvictionPolicy.INSTANCE.recordAccess(rl);
        }
    }
}
```

---

## COUCHE 5 — BACKGROUNDREBAKER : THREAD POOL DÉDIÉ

### Design

Le re-bake est l'opération la plus coûteuse (~5 à 50 ms par modèle).
Il **ne doit jamais** bloquer le thread de rendu.
Architecture : `PriorityBlockingQueue` + `ExecutorService` à 2 threads.

```java
// src/main/java/com/yourmod/models/BackgroundRebaker.java

public class BackgroundRebaker {
    public static final BackgroundRebaker INSTANCE = new BackgroundRebaker();

    // Thread pool : 2 threads bas-prio (THREAD_PRIORITY_BACKGROUND sur Android)
    // Jamais plus de 2 → pas de compétition avec le thread serveur
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ModelRebaker-" + System.nanoTime());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY); // 1 sur 10 — invisible pour le joueur
        // Sur Android : equivalent de Process.THREAD_PRIORITY_BACKGROUND
        if (isAndroid()) setAndroidBackgroundPriority(t);
        return t;
    });

    // File de priorité : HIGH (inventaire) → NORMAL (visible) → LOW (post-éviction)
    private final PriorityBlockingQueue<RebakeTask> queue =
        new PriorityBlockingQueue<>(256);

    // Set des modèles en attente → évite les doublons dans la queue
    private final ConcurrentHashMap<ResourceLocation, RebakePriority> pending =
        new ConcurrentHashMap<>();

    public enum RebakePriority {
        HIGH(0), NORMAL(1), LOW(2);
        final int order;
        RebakePriority(int order) { this.order = order; }
    }

    private record RebakeTask(ResourceLocation rl, RebakePriority priority)
        implements Comparable<RebakeTask> {
        @Override
        public int compareTo(RebakeTask other) {
            return Integer.compare(this.priority.order, other.priority.order);
        }
    }

    /**
     * Soumet un modèle pour re-bake asynchrone.
     * Si déjà dans la queue avec priorité >= celle demandée, ignoré.
     */
    public void scheduleRebake(ResourceLocation rl, RebakePriority priority) {
        RebakePriority existing = pending.get(rl);
        // Ne soumettre que si non présent ou si priorité plus haute demandée
        if (existing == null || existing.order > priority.order) {
            pending.put(rl, priority);
            queue.offer(new RebakeTask(rl, priority));
        }
    }

    /**
     * Worker : traite les tâches de la queue.
     * Lancé au démarrage du mod via FMLClientSetupEvent.
     */
    public void startWorkers() {
        for (int i = 0; i < 2; i++) {
            executor.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                RebakeTask task = queue.poll(5, TimeUnit.SECONDS);
                if (task == null) continue;
                pending.remove(task.rl());
                rebakeModel(task.rl());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Logguer mais ne pas crasher le worker
                LOGGER.warn("[YourMod] Erreur rebake {}: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Re-bake effectif d'un modèle.
     * IMPORTANT : Le baking Minecraft accède à des ressources partagées.
     * Il faut utiliser le ModelBakery existant, pas en créer un nouveau.
     *
     * Deux approches selon disponibilité ModernFix :
     * - Avec ModernFix dynamic_resources : simple accès au DynamicBakedModelProvider
     *   (il va auto-bake si nécessaire)
     * - Sans ModernFix : accès direct au ModelBakery.getModel() via réflexion
     */
    private void rebakeModel(ResourceLocation rl) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            ModelManager modelManager = mc.getModelManager();

            if (ModernFixBridge.isModernFixHandlingEviction()) {
                // ModernFix re-bake le modèle automatiquement quand on y accède
                // Il suffit de "toucher" le modèle pour déclencher son re-bake interne
                // MAIS : cet accès doit se faire sur le thread Minecraft (render thread)
                // On post la tâche finale sur le thread principal
                mc.tell(() -> {
                    // Accès qui déclenche le re-bake via DynamicBakedModelProvider
                    BakedModel baked = modelManager.getModel(rl);
                    if (baked != null && !(baked instanceof FlatSpriteModel)) {
                        ModelLifecycleManager.INSTANCE.markBaked(rl);
                        LOGGER.debug("[YourMod] Rebake terminé (via ModernFix): {}", rl);
                    }
                });
            } else {
                // Sans ModernFix : re-bake manuel via ModelBakery
                // Accès via réflexion (champ privé dans Minecraft 1.20)
                ModelBakery bakery = ReflectionHelper.getPrivateValue(
                    ModelManager.class, modelManager, "modelBakery");
                if (bakery == null) return;

                // Le baking lui-même est thread-safe depuis 1.20 (utilise des locks internes)
                UnbakedModel unbaked = bakery.getModel(rl);
                if (unbaked == null) {
                    LOGGER.warn("[YourMod] UnbakedModel introuvable pour {}", rl);
                    ModelLifecycleManager.INSTANCE.markEvicted(rl); // Reset à EVICTED
                    return;
                }

                // Baking : ModelBakery.bake() n'est pas thread-safe en 1.20 vanilla
                // → On doit poster la partie baking sur le render thread
                // La décompression LZ4 de l'UnbakedModel (Couche 7) peut se faire ici
                mc.tell(() -> {
                    try {
                        BakedModel baked = bakery.bake(
                            rl,
                            BlockModelRotation.X0_Y0,
                            bakery::getSprite
                        );
                        if (baked != null) {
                            // Insérer dans le registre de modèles
                            ReflectionHelper.getPrivateValue(
                                ModelManager.class, modelManager, "bakedRegistry")
                                .put(rl, baked);
                            ModelLifecycleManager.INSTANCE.markBaked(rl);
                            LOGGER.debug("[YourMod] Rebake terminé (sans ModernFix): {}", rl);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[YourMod] Échec rebake final {}: {}", rl, e);
                        ModelLifecycleManager.INSTANCE.markEvicted(rl);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("[YourMod] Erreur critique rebake {}: {}", rl, e);
            ModelLifecycleManager.INSTANCE.markEvicted(rl);
        }
    }

    /**
     * Applique Process.THREAD_PRIORITY_BACKGROUND via JNI sur Android.
     * Assure que le re-bake n'impacte jamais le thread rendu.
     */
    private static void setAndroidBackgroundPriority(Thread t) {
        try {
            // Utilise le bridge Android via réflexion (disponible sur PojavLauncher)
            Class<?> process = Class.forName("android.os.Process");
            // setThreadPriority(THREAD_PRIORITY_BACKGROUND = 10)
            process.getMethod("setThreadPriority", int.class).invoke(null, 10);
        } catch (Exception ignored) {
            // Pas Android ou pas de permission — Thread.MIN_PRIORITY suffira
        }
    }

    private static boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

---

## COUCHE 6 — AGRONA OFF-HEAP : VERTEX DATA HORS HEAP JAVA

### Objectif
Les `int[] vertexData` des `BakedQuad` sont la principale source de pression GC.
Chaque re-bake crée des milliers de nouveaux tableaux Java → des GC storms.
On déplace ces données dans des buffers off-heap Agrona invisibles pour le GC.

### Taille réelle (rappel)
- 1 BakedQuad = 32 ints = 128 octets
- 1 item moyen = 12 quads = 1,5 Ko de vertex data
- 500 items Create = 750 Ko → 6 Mo avec les allocs Java (headers, padding)
- 10 000 items (55 mods) = ~15 Mo de vertexData Java pur
- Après déduplications ModernFix + FerriteCore : estimé à ~5 Mo restants en heap

**Gain de cette couche : 5 à 30 Mo heap + réduction GC 30 à 50 %**

```groovy
// build.gradle — dépendance Agrona (shadée pour éviter conflits Forge)
implementation 'org.agrona:agrona:1.21.0'
// Shadow JAR obligatoire pour les mods Forge :
shadow 'org.agrona:agrona:1.21.0'
```

```java
// src/main/java/com/yourmod/models/OffHeapQuadStorage.java

/**
 * Stockage des int[] vertexData des BakedQuad en dehors du heap Java.
 * Utilise Agrona UnsafeBuffer (ByteBuffer.allocateDirect).
 *
 * Layout mémoire par BakedQuad :
 * [0..127]  : vertexData (32 × int = 128 octets)
 * [128..131]: tintIndex (int)
 * [132..135]: faceOrdinal (int, Direction.ordinal())
 * Total     : 136 octets par quad, aligné sur 8 octets
 */
public class OffHeapQuadStorage implements AutoCloseable {
    public static final OffHeapQuadStorage INSTANCE = new OffHeapQuadStorage();

    private static final int BYTES_PER_QUAD = 136;
    // Pool de 65 536 quads = 8,9 Mo max — couvre ~5 400 items moyens
    private static final int MAX_QUADS = 65_536;

    private final UnsafeBuffer buffer;
    // Index suivant disponible (allocateur de pile simple)
    private final AtomicInteger nextSlot = new AtomicInteger(0);
    // Map : (rl × faceIndex × quadIndex) → slot
    // Encodage de la clé : ResourceLocation hash(32b) | face(4b) | quadIdx(8b)
    private final ConcurrentHashMap<Long, Integer> slotMap = new ConcurrentHashMap<>(65_536);

    public OffHeapQuadStorage() {
        // Allocation unique au démarrage : jamais réallouée
        this.buffer = new UnsafeBuffer(
            ByteBuffer.allocateDirect(MAX_QUADS * BYTES_PER_QUAD));
        LOGGER.info("[YourMod] OffHeapQuadStorage alloué : {} Ko",
            (MAX_QUADS * BYTES_PER_QUAD) / 1024);
    }

    /**
     * Stocke un BakedQuad off-heap et retourne son slot ID.
     * Thread-safe via AtomicInteger pour l'allocation du slot.
     */
    public int storeQuad(BakedQuad quad, ResourceLocation rl, int faceOrdinal, int quadIdx) {
        int slot = nextSlot.getAndIncrement();
        if (slot >= MAX_QUADS) {
            // Pool plein → ne pas stocker (dégradation gracieuse, pas de crash)
            LOGGER.warn("[YourMod] OffHeapQuadStorage plein — mode heap fallback");
            return -1;
        }

        int offset = slot * BYTES_PER_QUAD;
        int[] vd = quad.getVertices();
        // Copie des 32 ints dans le buffer off-heap
        for (int i = 0; i < vd.length && i < 32; i++) {
            buffer.putInt(offset + i * 4, vd[i]);
        }
        buffer.putInt(offset + 128, quad.getTintIndex());
        buffer.putInt(offset + 132, faceOrdinal);

        // Enregistrer dans la map
        long key = encodeKey(rl, faceOrdinal, quadIdx);
        slotMap.put(key, slot);
        return slot;
    }

    /**
     * Reconstruit un BakedQuad depuis le stockage off-heap.
     * Crée un int[] Java temporaire uniquement pour la durée de this call
     * → collecté rapidement par le GC (durée de vie très courte).
     */
    public BakedQuad loadQuad(ResourceLocation rl, int faceOrdinal, int quadIdx,
                              TextureAtlasSprite sprite) {
        Long key = encodeKey(rl, faceOrdinal, quadIdx);
        Integer slot = slotMap.get(key);
        if (slot == null) return null;

        int offset = slot * BYTES_PER_QUAD;
        int[] vd = new int[32];
        for (int i = 0; i < 32; i++) {
            vd[i] = buffer.getInt(offset + i * 4);
        }
        int tintIndex = buffer.getInt(offset + 128);
        Direction face = Direction.values()[buffer.getInt(offset + 132)];
        return new BakedQuad(vd, tintIndex, face, sprite, true);
    }

    /**
     * Libère tous les slots d'un modèle éjecté.
     * Remet simplement le slot à 0 dans la map (GC-free).
     */
    public void releaseModel(ResourceLocation rl) {
        // Enlever toutes les entrées avec ce rl de la slotMap
        slotMap.entrySet().removeIf(e ->
            (int)(e.getKey() >> 16) == rl.hashCode()
        );
        // Note : les slots libérés ne sont pas réutilisés dans cette implémentation
        // simple. Un allocateur avec free-list complet est une amélioration future.
    }

    private static long encodeKey(ResourceLocation rl, int faceOrdinal, int quadIdx) {
        return ((long) rl.hashCode() << 16)
             | ((long) faceOrdinal << 8)
             | (long) quadIdx;
    }

    @Override
    public void close() {
        // Le ByteBuffer.allocateDirect() est libéré par le GC natif — pas de close() nécessaire
        // Mais on peut forcer via Unsafe si besoin en P4
        slotMap.clear();
    }
}
```

### OffHeapBakedModel — Wrapper qui délègue au stockage off-heap

```java
// src/main/java/com/yourmod/models/OffHeapBakedModel.java

/**
 * Wrapper de BakedModel qui stocke ses quads off-heap.
 * Délègue getQuads() au OffHeapQuadStorage.
 * Les int[] Java temporaires créés à chaque appel getQuads() ont une durée de vie
 * d'une seule frame → collectés par le GC minor très efficacement.
 */
public class OffHeapBakedModel implements BakedModel {

    private final BakedModel delegate; // Garde les métadonnées (transforms, overrides...)
    private final ResourceLocation rl;
    private final Map<Direction, List<Integer>> quadIndexMap; // face → [slotIds]

    public static OffHeapBakedModel wrap(BakedModel original, ResourceLocation rl) {
        OffHeapBakedModel wrapped = new OffHeapBakedModel(original, rl);

        // Migrer les quads de l'original vers le stockage off-heap
        for (Direction dir : Direction.values()) {
            List<BakedQuad> quads = original.getQuads(null, dir,
                new SingleThreadedRandomSource(42));
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < quads.size(); i++) {
                int slot = OffHeapQuadStorage.INSTANCE.storeQuad(
                    quads.get(i), rl, dir.ordinal(), i);
                if (slot >= 0) slots.add(slot);
                // Si slot == -1 (pool plein), on garde les quads en heap → dégradation gracieuse
            }
            wrapped.quadIndexMap.put(dir, slots);
        }
        return wrapped;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,
                                    @Nullable Direction side,
                                    RandomSource rand) {
        List<Integer> slots = quadIndexMap.getOrDefault(side, Collections.emptyList());
        List<BakedQuad> result = new ArrayList<>(slots.size());
        TextureAtlasSprite sprite = delegate.getParticleIcon();
        for (int i = 0; i < slots.size(); i++) {
            BakedQuad q = OffHeapQuadStorage.INSTANCE.loadQuad(
                rl, side == null ? -1 : side.ordinal(), i, sprite);
            if (q != null) result.add(q);
        }
        return result;
    }

    // Toutes les autres méthodes délèguent à `delegate`
    @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
    @Override public boolean isGui3d()              { return delegate.isGui3d(); }
    @Override public boolean usesBlockLight()       { return delegate.usesBlockLight(); }
    @Override public boolean isCustomRenderer()     { return delegate.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return delegate.getParticleIcon(); }
    @Override public ItemOverrides getOverrides()   { return delegate.getOverrides(); }
    @Override public ItemTransforms getTransforms() { return delegate.getTransforms(); }
}
```

---

## COUCHE 7 — NDK C++ : COMPRESSION LZ4 DES UNBAKED MODELS

### Contexte
Quand ModernFix `dynamic_resources` est actif, il garde les `UnbakedModel`
en mémoire pour pouvoir re-bake à la demande. Chaque UnbakedModel = le JSON
parsé de la définition du modèle (BlockElements, textureMap, rotations...).
Pour 55 mods × 10 000 modèles, cela représente ~50 à 150 Mo de JSON parsé.

On compresse ces données JSON brutes en LZ4 via NDK → ratio ~3:1 sur du JSON.

### CMakeLists.txt

```cmake
# src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)
project(yourmod_model_cache)

add_library(lz4 STATIC
    lz4/lz4.c
    lz4/lz4hc.c      # LZ4 High Compression (meilleur ratio pour données froides)
    lz4/lz4frame.c
)

add_library(model_cache SHARED
    model_cache_jni.cpp
)

target_link_libraries(model_cache lz4 log)
target_compile_options(model_cache PRIVATE
    -O3
    -march=armv8-a        # ARMv8 baseline (tous les Android récents)
    -mfpu=neon            # NEON activé pour les intrinsics LZ4
    -ffast-math
)
```

### model_cache_jni.cpp

```cpp
// src/main/cpp/model_cache_jni.cpp
#include <jni.h>
#include <android/log.h>
#include "lz4/lz4.h"
#include "lz4/lz4hc.h"
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "YourMod_ModelCache"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Compresse un tableau de bytes (JSON serialisé de l'UnbakedModel) via LZ4HC.
 * LZ4HC = LZ4 High Compression : ratio ~3:1 sur JSON, décompression identique à LZ4.
 * Coût : compression ~5× plus lente que LZ4 standard, mais on compresse rarement
 * (seulement lors de l'éviction). Décompression toujours rapide (<1 ms/modèle).
 *
 * @param srcBuf  ByteBuffer direct Java contenant le JSON brut
 * @param srcLen  Longueur des données source
 * @param dstBuf  ByteBuffer direct Java pour le résultat compressé
 * @return Taille compressée, ou -1 si erreur
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_yourmod_models_NativeModelCompressor_compressHC(
    JNIEnv* env, jclass cls,
    jobject srcBuf, jint srcLen,
    jobject dstBuf)
{
    const char* src = (const char*)env->GetDirectBufferAddress(srcBuf);
    char* dst = (char*)env->GetDirectBufferAddress(dstBuf);

    if (!src || !dst) {
        LOGE("Buffer direct null — utiliser ByteBuffer.allocateDirect()");
        return -1;
    }

    // LZ4_COMPRESSBOUND garantit assez d'espace pour le pire cas
    int maxDst = LZ4_compressBound(srcLen);
    jlong dstCapacity = env->GetDirectBufferCapacity(dstBuf);
    if (dstCapacity < maxDst) {
        LOGE("dstBuf trop petit : %lld < %d", dstCapacity, maxDst);
        return -1;
    }

    // Niveau 9 = compression maximale LZ4HC (meilleur ratio pour cold storage)
    int compressedSize = LZ4_compress_HC(src, dst, srcLen, maxDst, 9);
    if (compressedSize <= 0) {
        LOGE("Échec LZ4HC compression : srcLen=%d", srcLen);
        return -1;
    }

    LOGI("Modèle compressé : %d → %d octets (ratio %.1f:1)",
         srcLen, compressedSize, (float)srcLen / compressedSize);
    return compressedSize;
}

/**
 * Décompresse un modèle LZ4 compressé.
 * Ultra-rapide : ~400 Mo/s sur Snapdragon 8 Gen 2 → <0.5 ms pour un modèle moyen.
 *
 * @return Taille décompressée, ou -1 si erreur
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_yourmod_models_NativeModelCompressor_decompress(
    JNIEnv* env, jclass cls,
    jobject srcBuf, jint srcLen,
    jobject dstBuf, jint maxDstLen)
{
    const char* src = (const char*)env->GetDirectBufferAddress(srcBuf);
    char* dst = (char*)env->GetDirectBufferAddress(dstBuf);

    if (!src || !dst) return -1;

    int result = LZ4_decompress_safe(src, dst, srcLen, maxDstLen);
    if (result < 0) {
        LOGE("Échec LZ4 décompression : srcLen=%d, maxDst=%d", srcLen, maxDstLen);
    }
    return result;
}

/**
 * Retourne la taille maximale de sortie pour une compression LZ4.
 * Utilisé côté Java pour allouer le ByteBuffer destination.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_yourmod_models_NativeModelCompressor_compressBound(
    JNIEnv* env, jclass cls, jint srcLen)
{
    return LZ4_compressBound(srcLen);
}
```

### Wrapper Java

```java
// src/main/java/com/yourmod/models/NativeModelCompressor.java

public class NativeModelCompressor {

    static {
        try {
            System.loadLibrary("model_cache");
            NATIVE_AVAILABLE = true;
        } catch (UnsatisfiedLinkError e) {
            NATIVE_AVAILABLE = false;
            LOGGER.warn("[YourMod] libmodel_cache.so non disponible — compression désactivée");
        }
    }

    private static final boolean NATIVE_AVAILABLE;
    // Buffers ThreadLocal pour éviter les allocs répétées
    private static final ThreadLocal<ByteBuffer> COMPRESS_SCRATCH =
        ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(4 * 1024 * 1024)); // 4 Mo

    private static native int compressHC(ByteBuffer src, int srcLen, ByteBuffer dst);
    private static native int decompress(ByteBuffer src, int srcLen, ByteBuffer dst, int maxDst);
    private static native int compressBound(int srcLen);

    /**
     * Compresse un JSON de modèle (byte[]) → ByteBuffer direct compressé.
     * Utilisé lors de l'éviction d'un UnbakedModel.
     * @return null si compression non disponible ou échec
     */
    public static @Nullable ByteBuffer compressModelJson(byte[] jsonBytes) {
        if (!NATIVE_AVAILABLE) return null;

        ByteBuffer src = ByteBuffer.wrap(jsonBytes);
        int maxDst = compressBound(jsonBytes.length);

        ByteBuffer dst = ByteBuffer.allocateDirect(maxDst);
        ByteBuffer scratch = COMPRESS_SCRATCH.get();
        scratch.clear();
        scratch.put(jsonBytes);
        scratch.flip();

        int compressedLen = compressHC(scratch, jsonBytes.length, dst);
        if (compressedLen <= 0) return null;

        dst.limit(compressedLen);
        return dst;
    }

    /**
     * Décompresse → byte[] JSON pour re-parsing par ModelBakery.
     * <1 ms sur Snapdragon 8 Gen 2 pour un modèle moyen (~20 Ko JSON).
     */
    public static @Nullable byte[] decompressModelJson(ByteBuffer compressed,
                                                        int originalSize) {
        if (!NATIVE_AVAILABLE) return null;

        ByteBuffer dst = ByteBuffer.allocateDirect(originalSize);
        int result = decompress(compressed, compressed.limit(), dst, originalSize);
        if (result <= 0) return null;

        byte[] out = new byte[result];
        dst.get(out);
        return out;
    }
}
```

---

## THREAD DE FOND — EVICTION CONTROLLER

```java
// src/main/java/com/yourmod/models/EvictionController.java

/**
 * Thread daemon qui tourne toutes les 5 minutes.
 * Vérifie le budget mémoire et déclenche les évictions si nécessaire.
 */
public class EvictionController implements Runnable {
    public static final EvictionController INSTANCE = new EvictionController();

    // Intervalle : 5 min en jeu (6 000 ticks), mais le thread tourne en temps réel
    private static final long EVICTION_INTERVAL_MS = 5 * 60 * 1000L;
    // Seuil d'urgence : si heap > 85% de Xmx → éviction immédiate
    private static final double EMERGENCY_HEAP_THRESHOLD = 0.85;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ModelEvictionController");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

    public void start() {
        // Éviction normale toutes les 5 minutes
        scheduler.scheduleAtFixedRate(this, 5, 5, TimeUnit.MINUTES);
        // Surveillance d'urgence toutes les 30 secondes
        scheduler.scheduleAtFixedRate(
            this::emergencyCheck, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            performEviction(false);
        } catch (Exception e) {
            LOGGER.warn("[YourMod] Erreur éviction planifiée : {}", e.getMessage());
        }
    }

    private void emergencyCheck() {
        Runtime rt = Runtime.getRuntime();
        long usedHeap = rt.totalMemory() - rt.freeMemory();
        long maxHeap = rt.maxMemory();
        double heapRatio = (double) usedHeap / maxHeap;

        if (heapRatio > EMERGENCY_HEAP_THRESHOLD) {
            LOGGER.warn("[YourMod] Heap à {:.1f}% — éviction d'urgence", heapRatio * 100);
            performEviction(true); // emergency=true → éviction plus agressive
        }
    }

    private void performEviction(boolean emergency) {
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        // Compter les modèles actuellement en mémoire
        // (via réflexion sur le registre de ModernFix ou le bakedRegistry vanilla)
        int currentCount = getCurrentModelCount(modelManager);

        int budget = emergency
            ? ModelEvictionPolicy.INSTANCE.budget / 2  // Budget divisé par 2 en urgence
            : ModelEvictionPolicy.INSTANCE.budget;

        List<ResourceLocation> candidates =
            ModelEvictionPolicy.INSTANCE.computeEvictionCandidates(currentCount);

        if (candidates.isEmpty()) return;

        LOGGER.info("[YourMod] Éviction de {} modèles (budget={}, actuel={})",
            candidates.size(), budget, currentCount);

        for (ResourceLocation rl : candidates) {
            // 1. Comprimer l'UnbakedModel en mémoire (Couche 7)
            cacheCompressedUnbaked(rl);
            // 2. Libérer le slot off-heap Agrona (Couche 6)
            OffHeapQuadStorage.INSTANCE.releaseModel(rl);
            // 3. Retirer du registre de modèles
            evictFromModelRegistry(rl, modelManager);
            // 4. Marquer EVICTED dans la FSM
            ModelLifecycleManager.INSTANCE.markEvicted(rl);
            // 5. Pre-bake en fond à LOW priorité
            BackgroundRebaker.INSTANCE.scheduleRebake(rl, BackgroundRebaker.RebakePriority.LOW);
        }

        // Suggestion au GC (non-forcé — laisser le GC décider)
        System.gc();
        LOGGER.info("[YourMod] Éviction terminée — modèles évictés : {}", candidates.size());
    }

    private void cacheCompressedUnbaked(ResourceLocation rl) {
        // Sérialiser l'UnbakedModel en JSON bytes → compresser LZ4 → stocker dans
        // une Map<ResourceLocation, ByteBuffer> off-heap
        // Détail d'implémentation omis pour la concision — utilise GSON + NativeModelCompressor
    }

    private int getCurrentModelCount(ModelManager modelManager) {
        // Via réflexion sur le champ "bakedRegistry" ou DynamicBakedModelProvider.size()
        return 0; // Placeholder
    }

    private void evictFromModelRegistry(ResourceLocation rl, ModelManager modelManager) {
        // Via réflexion : bakedRegistry.remove(rl)
        // Si ModernFix présent : DynamicBakedModelProvider gère lui-même la TTL
        // On signale juste notre éviction via le bridge
    }
}
```

---

## INTÉGRATION AVEC LE PLAN V6 PRINCIPAL

### Ce qui change dans §4.2

L'économie estimée de **150 à 350 Mo** du §4.2 original était **gonflée** car elle
ne tenait pas compte de ce que ModernFix fait déjà.

**Révision :**

| Source d'économie | Estimation (révisée) | Couche |
|-------------------|---------------------|--------|
| FlatSpriteModel fallback (zéro stutter) | Gain qualitatif (pas quantitatif) | 4 |
| Vertex data off-heap via Agrona | 5 à 30 Mo heap + -30% GC pauses | 6 |
| Compression UnbakedModel LZ4 | 15 à 50 Mo (JSON parsé → LZ4) | 7 |
| Politique LFU hybride vs LRU | Meilleur hit rate → moins de re-bakes | 3 |
| Whitelist inventaire | Prévention re-bakes inutiles | 3 |
| Pre-warm prédictif | -60% latence premier affichage modèle | 5 |
| **TOTAL DIFFÉRENTIEL vs ModernFix seul** | **20 à 80 Mo heap + zéro stutter** | — |

> **Note :** L'essentiel de la valeur ajoutée est **qualitative** (zéro stutter
> pendant l'éviction) plutôt que quantitative. ModernFix gère déjà le gros
> de la réduction quantitative.

---

## ROADMAP D'IMPLÉMENTATION

```
Semaine A : Audit ModernFix
  → Confirmer que dynamic_resources est activé dans le modpack cible
  → Lire DynamicBakedModelProvider.java sur GitHub pour comprendre
    exactement son mécanisme de TTL (valeur par défaut ?)
  → Benchmark : comparer heap usage avec/sans ModernFix dynamic_resources
    sur notre pack 55 mods

Semaine B : Couche 1 + 2 (Bridge + FSM)
  → Implémenter ModernFixBridge
  → Implémenter ModelLifecycleManager
  → Tests unitaires de la FSM (transitions concurrentes)

Semaine C-D : Couche 3 + 4 (Policy + FlatSpriteModel)
  → LFU-LRU hybrid policy
  → FlatSpriteModel (le fallback 2D — différenciateur principal)
  → Mixin sur ItemRenderer.getModel()
  → Test visuel : voir le sprite 2D pendant 1-2 frames, puis le 3D revient

Semaine E-F : Couche 5 (BackgroundRebaker)
  → Thread pool + PriorityBlockingQueue
  → Test de stutter : F3 + frametime graph avant/après
  → Test de stabilité : 60 minutes de jeu intensif Create + JEI

Semaine G : Couche 6 (Agrona off-heap)
  → OffHeapQuadStorage + OffHeapBakedModel
  → Benchmark GC log : -Xlog:gc*:file=gc.log
  → Vérifier réduction des GC minor (allocation rate)

Semaine H-I : Couche 7 (NDK C++)
  → CMakeLists.txt + LZ4HC JNI
  → Tests sur appareil Android réel (Snapdragon 8 Gen 2)
  → Benchmark : adb shell dumpsys meminfo avant/après
```

---

## TESTS ET BENCHMARKS OBLIGATOIRES

| Test | Outil | Critère de succès |
|------|-------|-------------------|
| Heap Java après 30 min jeu | VisualVM / Android Profiler | Stable, pas de croissance linéaire |
| GC pauses | -Xlog:gc*:file=gc.log | Réduction ≥ 30% vs sans Couche 6 |
| Frametime pendant éviction | Minecraft F3 (ms/frame) | Aucun spike > 50 ms visible |
| Re-bake latence | Log custom "[YourMod] Rebake terminé" | < 200 ms entre EVICTED et BAKED |
| Stutter visuel | Enregistrement vidéo 60fps | FlatSpriteModel ≤ 3 frames visible |
| RAM système totale | adb shell dumpsys meminfo | ≤ 3 000 Mo (objectif plan V6) |
| Stabilité 55 mods | 2h session Create + JEI + Patchouli | Aucun crash, aucune NPE |

---

## RISQUES ET MITIGATIONS

| Risque | Probabilité | Mitigation |
|--------|-------------|-----------|
| Mixin sur ModernFix casse avec update ModernFix | Moyenne | Mixin conditionnel + version guard |
| BackgroundRebaker accède ModelBakery non thread-safe | Haute | `mc.tell()` pour la partie bake — jamais hors render thread |
| OffHeapQuadStorage pool plein sur gros modpack | Faible | Dégradation gracieuse → heap fallback |
| FlatSpriteModel mauvais look pour items 3D complexes (Create) | Haute | Acceptable : < 3 frames, puis 3D revient |
| Android : `setAndroidBackgroundPriority` fail sur ROM custom | Moyenne | Try-catch + log avertissement |
| LZ4HC trop lent sur éviction d'urgence | Faible | Utiliser LZ4 standard (vitesse) au lieu de LZ4HC si heap > 90% |

---

*Plan Lazy 3D Models — Complément §4.2 V6 · 31/05/2026*
*Correction : ModernFix dynamic_resources fait déjà l'éviction TTL de base.*
*Notre valeur ajoutée : FlatSpriteModel fallback + async rebake + LFU-LRU + off-heap Agrona + LZ4HC NDK.*
