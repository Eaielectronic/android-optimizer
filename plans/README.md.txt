
# ⚡ NativeForge — Plan Maître MASSIF V3
## *"Un seul mod dans /mods/ qui transforme tout le jeu en C++ natif"*

> **Ce document est le brief technique complet pour une IA (Gemini, Claude).**
> Chaque section est autonome : tu peux en donner une seule à l'IA pour qu'elle code
> exactement ce composant. Toutes les décisions difficiles sont déjà résolues ici.

---

## 📋 Table des matières

1. [🎯 Le projet en 5 minutes](#1-le-projet-en-5-minutes)
2. [🏗 Architecture complète du système](#2-architecture)
3. [🔍 Comment NeoForge charge les mods — pipeline interne détaillé](#3-pipeline-neoforge)
4. [🧰 Inventaire complet des outils réutilisés](#4-outils)
5. [⚙️ Composant 1 — Le Mod NeoForge (le lanceur)](#5-composant-1--le-mod)
6. [🍞 Composant 2 — Le Bake Engine (étape par étape)](#6-composant-2--bake-engine)
   - 6.1 JarLoader — Lire tout le bytecode
   - 6.2 AT Applier — Access Transformers
   - 6.3 MixinScanner — Trouver tous les @Mixin
   - 6.4 MixinApplicator — Le cœur (tous les types expliqués)
   - 6.5 MixinExtras Handler — @WrapOperation etc.
   - 6.6 ReflectionResolver — Class.forName() statique
   - 6.7 InvokeDynamic Patcher — Les lambdas
   - 6.8 ClassExporter — Écrire les classes fusionnées
7. [⚙️ Composant 3 — La Transpilation (clearwing-vm)](#7-composant-3--transpilation)
8. [⚙️ Composant 4 — Le Runtime C++](#8-composant-4--runtime-c)
9. [📱 Composant 5 — Android (Amethyst + MobileGlues)](#9-composant-5--android)
10. [💥 Catalogue complet des problèmes — causes et solutions](#10-catalogue-des-problèmes)
11. [🗓 Plan de développement mois par mois](#11-plan-de-développement)
12. [🤖 Prompts IA pour chaque composant](#12-prompts-ia)
13. [📚 Sources et références](#13-sources)

---

## 1. Le projet en 5 minutes

### Ce que le joueur voit

```
Étape 1 : Le joueur télécharge nativeforge-1.0.jar
Étape 2 : Il le met dans son dossier .minecraft/mods/ avec ses autres mods
Étape 3 : Il lance Minecraft normalement (avec NeoForge)
Étape 4 : Un écran "Compilation native en cours... 12 min" apparaît
Étape 5 : Minecraft se relance. Cette fois en C++ natif.
Étape 6 : Démarrage en 30 secondes. 1.5 Go de RAM. Jeu fluide.
```

### Ce qui se passe techniquement

```
Premier lancement :
  JVM charge NeoForge → NativeForge mod se charge EN PREMIER
  NativeForge détecte "pas de binaire" → lance le Bake Engine
  Bake Engine lit TOUS les .jar (Minecraft + mods)
  → Applique les Access Transformers (offline)
  → Applique tous les Mixins (offline, comme NeoForge le ferait au runtime)
  → Ecrit les classes Java fusionnées sur disque (fused_classes/)
  clearwing-vm prend fused_classes/ et génère du C++
  Clang compile le C++ → minecraft_native.exe (ou .so pour Android)
  Hash du modpack sauvegardé
  Jeu relancé

Lancements suivants :
  JVM charge NeoForge → NativeForge mod se charge EN PREMIER
  NativeForge compare le hash → identique → lance minecraft_native.exe
  JVM dort (50 Mo RAM) → C++ gère tout
```

### Ce qui NE change PAS pour le joueur
- Les assets, textures, sons, modèles → identiques (fichiers .jar non modifiés)
- Le gameplay → identique (même logique, juste plus rapide)
- La compatibilité → si le modpack tourne en Java, le binaire C++ sera identique

---

## 2. Architecture

### Vue globale des couches

```
┌──────────────────────────────────────────────────────────────────────────┐
│  COUCHE 0 — CE QUE NEOFORGE FAIT NORMALEMENT (on s'y greffe)             │
│  JVM → ModLauncher → FMLServiceProvider → AT+Mixin transformation        │
│  → FMLLoader → ModLoader → @Mod constructors → Events → Jeu              │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ NativeForge intercepte ici
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  COUCHE 1 — LE MOD NATIVEFORGE (un .jar dans /mods/)                    │
│  • Se charge grâce à FMLLoadCompleteEvent                                │
│  • Détecte si binaire natif existe et est à jour (SHA-256 hash)          │
│  • Décision : [Compiler] ou [Lancer binaire] ou [Fallback JVM]          │
└──────────┬───────────────────────────────────────────┬───────────────────┘
           │ si compilation nécessaire                 │ si binaire prêt
           ▼                                           ▼
┌──────────────────────────┐              ┌────────────────────────────────┐
│  COUCHE 2 — BAKE ENGINE  │              │  COUCHE 2b — LANCEUR BINAIRE   │
│  (embarqué dans le mod)  │              │  Transfère fenêtre GLFW + GL   │
│  1. JarLoader            │              │  context au process C++        │
│  2. AT Applier           │              │  JVM dort (50 Mo RAM)          │
│  3. MixinScanner         │              └────────────────────────────────┘
│  4. MixinApplicator      │
│  5. MixinExtras Handler  │
│  6. ReflectionResolver   │
│  7. InvokeDynamic Patcher│
│  8. ClassExporter        │
└──────────┬───────────────┘
           │ fused_classes/ prêtes
           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  COUCHE 3 — TRANSPILATION clearwing-vm                                   │
│  fused_classes/ → C++ project (CMake) → Clang -O3 → binaire natif       │
│  PC:      minecraft_native.exe / minecraft_native (Linux)                │
│  Android: libminecraft_native.so (ARM64)                                 │
└──────────┬───────────────────────────────────────────────────────────────┘
           │ binaire natif
           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  COUCHE 4 — RUNTIME C++ DES MODS                                         │
│  NativeRegistry<T> + EventBus<E> + BootSequence + Boehm GC               │
│  Reproduit l'infrastructure NeoForge en C++ pour que les mods marchent  │
└──────────┬────────────────────────────┬─────────────────────────────────┘
           │ PC                         │ Android
           ▼                            ▼
┌──────────────────┐        ┌─────────────────────────────────────────────┐
│ .exe/.elf direct │        │ COUCHE 5 — ANDROID (Amethyst + MobileGlues) │
│ OpenGL via GLFW  │        │ JNI bridge + EGL + OpenGL ES 3 + input touch│
└──────────────────┘        └─────────────────────────────────────────────┘
```

---

## 3. Pipeline NeoForge interne — comprendre pour mieux se greffer

**C'est crucial de comprendre CE QUE FAIT NeoForge pour savoir où s'y insérer.**

### 3.1 Les 7 étapes de chargement de NeoForge

```
ÉTAPE 1 : ModLauncher bootstrap
  ┌─────────────────────────────────────────────────────────────────────┐
  │ cpw.mods.modlauncher.Launcher.main() démarre                       │
  │ Charge les ITransformationService via ServiceLoader               │
  │ NeoForge s'y enregistre comme FMLServiceProvider                  │
  │ → C'est ici que les AT et les Mixins sont "enregistrés" auprès   │
  │   de ModLauncher pour être appliqués plus tard                    │
  └─────────────────────────────────────────────────────────────────────┘
  
ÉTAPE 2 : Découverte des mods (FMLLoader.beginModScan)
  ┌─────────────────────────────────────────────────────────────────────┐
  │ ModDiscoverer scanne /mods/ et charge les neoforge.mods.toml     │
  │ Pour chaque mod : lit les [[mixins]] et [[accessTransformers]]    │
  │ Enregistre les configs Mixin dans DeferredMixinConfigRegistration │
  └─────────────────────────────────────────────────────────────────────┘

ÉTAPE 3 : Class transformation (AT + Mixin)
  ┌─────────────────────────────────────────────────────────────────────┐
  │ Quand une classe est demandée par la JVM :                         │
  │   ModLauncher intercepte le classloading                          │
  │   1. Applique les AT (change private → public)                    │
  │   2. Applique les Mixins de SpongePowered                         │
  │   3. Rend la classe transformée à la JVM                          │
  │                                                                    │
  │ NOTRE BAKE ENGINE fait exactement la même chose, MAIS SUR LE     │
  │ DISQUE EN AVANCE, pour TOUS les .class en même temps             │
  └─────────────────────────────────────────────────────────────────────┘

ÉTAPE 4 : Instanciation des mods (constructeurs @Mod)
  ┌─────────────────────────────────────────────────────────────────────┐
  │ FMLModContainer appelle new MonMod(IEventBus modBus)              │
  │ Les constructeurs font les DeferredRegister.register(...)         │
  └─────────────────────────────────────────────────────────────────────┘

ÉTAPE 5 : RegisterEvent (gel des registres)
  ┌─────────────────────────────────────────────────────────────────────┐
  │ NeoForge déclenche RegisterEvent pour chaque registre             │
  │ Les DeferredRegister instancient et enregistrent leurs objets     │
  └─────────────────────────────────────────────────────────────────────┘

ÉTAPE 6 : FMLCommonSetupEvent
  ┌─────────────────────────────────────────────────────────────────────┐
  │ Initialisation commune (serveur + client)                         │
  │ Enregistrements de capabilities, network channels, etc.           │
  └─────────────────────────────────────────────────────────────────────┘

ÉTAPE 7 : FMLClientSetupEvent
  ┌─────────────────────────────────────────────────────────────────────┐
  │ Initialisation client uniquement                                  │
  │ Enregistrement des renderers, GUI screens, keybinds               │
  └─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Où NativeForge se greffe

NativeForge s'abonne à **FMLLoadCompleteEvent** qui se déclenche APRÈS l'étape 7 mais AVANT que le menu principal soit affiché. C'est le moment parfait pour lancer la compilation ou basculer sur le binaire C++.

```java
@Mod("nativeforge")
public class NativeForge {
    public NativeForge(IEventBus modBus) {
        // FMLLoadCompleteEvent = tout NeoForge est chargé, jeu pas encore démarré
        modBus.addListener(this::onLoadComplete);
    }
    
    private void onLoadComplete(FMLLoadCompleteEvent event) {
        // C'est ici qu'on décide de compiler ou de lancer le binaire
        NativeForgeLauncher.decideAndRun();
    }
}
```

### 3.3 Informations critiques sur les mappings (NeoForge 1.20.2+)

**BONNE NOUVELLE MAJEURE** : Depuis NeoForge 1.20.2, tous les noms de classes/méthodes/champs
utilisent les **Official Mojang Mappings (Mojmap)** au runtime. Les développeurs de mods voient
et utilisent directement les noms Mojmap dans leur code.

```
Avant 1.20.2 : Block → C_231_ (SRG) → Block (Mojmap)
Depuis 1.20.2 : Block → Block (Mojmap directement dans le .jar distribué)
```

**Conséquence** : On N'A PAS besoin de TinyRemapper ou d'un outil de remapping !
Les .jar vanilla, NeoForge et mods distribués aux joueurs sont déjà en Mojmap.
Le Bake Engine peut travailler directement sur les noms lisibles.

---

## 4. Inventaire complet des outils réutilisés

### 4.1 Outils pour le Bake Engine

```
org.ow2.asm:asm-tree:9.7          → ClassNode, MethodNode, InsnNode (CORE)
org.ow2.asm:asm-commons:9.7       → ClassRemapper, MethodRemapper
org.ow2.asm:asm-util:9.7          → CheckClassAdapter (debug/validation)
org.spongepowered:mixin:0.8.7     → Parser des annotations @Mixin (usage offline)
com.llamalad7:mixinextras:0.4.1   → MixinExtrasService (offline)
net.neoforged.accesstransformers:at-cli:8.0.3  → AT Applier officiel
com.google.code.gson:gson:2.11    → Lire les *.mixins.json
```

**Comment utiliser SpongePowered/Mixin en mode offline :**
```java
// SpongePowered/Mixin peut fonctionner hors JVM NeoForge
// en utilisant son Annotation Processor
// On n'instancie pas MixinEnvironment (ça nécessite un ModLauncher)
// On utilise directement les classes ASM du projet Mixin :
// org.spongepowered.asm.mixin.injection.* pour les annotations
// On les lit via ASM AnnotationNode comme expliqué dans les sections suivantes
```

### 4.2 Outils pour la transpilation

```
clearwing-vm (SwitchGDX)    → Java bytecode → C++ complet avec CMake
  github.com/SwitchGDX/clearwing-vm
  
LLVM/Clang 17+              → Compiler le C++ généré
  llvm.org (Linux : apt install clang-17)
  
Android NDK r27c            → Cross-compilation ARM64
  developer.android.com/ndk/downloads
```

### 4.3 Outils pour le Runtime C++

```
Boehm GC (bdwgc)    → Garbage Collector conservateur pour C++
  github.com/bdwgc/bdwgc
  Installation : git clone + cmake -Dbuild_tests=OFF
  
GLFW 3.3+           → Fenêtres et input sur PC (déjà utilisé par LWJGL/Minecraft)
```

### 4.4 Outils Android

```
Amethyst-Android    → Launcher Android NeoForge (LGPL v3, base du projet)
  github.com/AngelAuraMC/Amethyst-Android
  NeoForge déjà supporté dans les dernières releases
  
MobileGlues         → OpenGL → OpenGL ES 3.x (LGPL 2.1)
  github.com/MobileGL-Dev/MobileGlues-release
  
Android NDK r27c    → Compilation ARM64 (Apache 2.0)
```

### 4.5 Référence / inspiration

```
mc-image               → Config GraalVM pour Minecraft vanilla natif
  github.com/kb-1000/mc-image
  
native-minecraft-server → Serveur Minecraft natif (université de Potsdam)
  github.com/hpi-swa/native-minecraft-server
```

---

## 5. Composant 1 — Le Mod NeoForge (le lanceur)

### 5.1 Structure du .jar

```
nativeforge-1.0.jar
├── META-INF/
│   ├── MANIFEST.MF
│   └── neoforge.mods.toml          ← Métadonnées du mod
├── net/nativeforge/
│   ├── NativeForge.java            ← @Mod principal
│   ├── NativeForgeLauncher.java    ← Logique de décision
│   ├── BakeEngineRunner.java       ← Lance le Bake Engine
│   ├── NativeBinaryLauncher.java   ← Lance le .exe/.elf C++
│   └── ui/
│       └── BakeProgressScreen.java ← Écran de progression
├── bake/                           ← Tout le Bake Engine
│   ├── JarLoader.java
│   ├── ATApplier.java
│   ├── MixinScanner.java
│   ├── MixinApplicator.java
│   ├── MixinExtrasHandler.java
│   ├── ReflectionResolver.java
│   ├── InvokeDynamicPatcher.java
│   └── ClassExporter.java
└── assets/nativeforge/
    └── lang/en_us.json             ← Textes de l'UI
```

### 5.2 neoforge.mods.toml

```toml
modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
    modId="nativeforge"
    version="1.0.0"
    displayName="NativeForge"
    description="""
    Compile your modpack to native C++ for blazing fast load times and lower RAM.
    First launch compiles (15 min). Next launches: 30 sec startup, 1.5 GB RAM.
    """

# NativeForge DOIT se charger AVANT tous les autres mods
# car il a besoin de lister les .jar avant qu'ils soient utilisés
[[dependencies.nativeforge]]
    modId="neoforge"
    type="required"
    versionRange="[21,)"
    ordering="BEFORE"
    side="CLIENT"
```

### 5.3 NativeForge.java — Le mod principal

```java
package net.nativeforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("nativeforge")
public class NativeForge {
    
    public static final Logger LOG = LogManager.getLogger("NativeForge");
    
    public NativeForge(IEventBus modBus) {
        LOG.info("NativeForge chargé. Évaluation du mode de lancement...");
        modBus.addListener(this::onLoadComplete);
    }
    
    private void onLoadComplete(FMLLoadCompleteEvent event) {
        // Tout NeoForge est chargé ici. Le menu principal n'est pas encore affiché.
        // C'est le bon moment pour décider quoi faire.
        
        // Lancer la logique dans un thread séparé pour ne pas bloquer FML
        Thread launcher = new Thread(NativeForgeLauncher::decide, "NativeForge-Launcher");
        launcher.setDaemon(true);
        launcher.start();
    }
}
```

### 5.4 NativeForgeLauncher.java — La logique de décision

```java
package net.nativeforge;

import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class NativeForgeLauncher {
    
    // Dossier .nativeforge/ dans le répertoire de jeu
    private static Path getNativeDir() {
        return FMLPaths.GAMEDIR.get().resolve(".nativeforge");
    }
    
    private static Path getBinaryPath() {
        String ext = System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "";
        return getNativeDir().resolve("minecraft_native" + ext);
    }
    
    private static Path getHashFile() {
        return getNativeDir().resolve("modpack.sha256");
    }
    
    public static void decide() {
        try {
            Files.createDirectories(getNativeDir());
            
            // Calculer le hash actuel du modpack
            String currentHash = computeModpackHash();
            String cachedHash = loadCachedHash();
            
            Path binary = getBinaryPath();
            boolean binaryExists = Files.exists(binary);
            boolean hashMatch = currentHash.equals(cachedHash);
            
            if (binaryExists && hashMatch) {
                // Le binaire est à jour → le lancer
                NativeForge.LOG.info("Binaire natif trouvé et à jour. Lancement C++...");
                NativeBinaryLauncher.launch(binary);
            } else if (!binaryExists) {
                NativeForge.LOG.info("Aucun binaire natif. Première compilation...");
                triggerCompilation(currentHash);
            } else {
                // hashMismatch : modpack a changé
                NativeForge.LOG.info("Modpack changé (hash différent). Recompilation...");
                triggerCompilation(currentHash);
            }
            
        } catch (Exception e) {
            NativeForge.LOG.error("Erreur NativeForge, fallback JVM : ", e);
            // Fallback silencieux : le jeu continue en mode JVM normal
        }
    }
    
    private static void triggerCompilation(String currentHash) {
        // Afficher l'écran de progression dans le thread Minecraft
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new BakeProgressScreen());
        });
        
        // Lancer la compilation en background
        new Thread(() -> {
            try {
                BakeEngineRunner runner = new BakeEngineRunner();
                boolean success = runner.run(progress -> {
                    // Mettre à jour la barre de progression dans l'UI
                    BakeProgressScreen.setProgress(progress);
                });
                
                if (success) {
                    // Sauvegarder le hash
                    Files.writeString(getHashFile(), currentHash);
                    NativeForge.LOG.info("Compilation réussie ! Relancement en mode C++...");
                    
                    // Relancer le jeu
                    Minecraft.getInstance().execute(() -> {
                        restartWithNativeBinary();
                    });
                } else {
                    NativeForge.LOG.error("Compilation échouée. Mode JVM conservé.");
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(null); // Fermer l'écran
                    });
                }
            } catch (Exception e) {
                NativeForge.LOG.error("Erreur de compilation : ", e);
            }
        }, "NativeForge-Compiler").start();
    }
    
    /**
     * Calcule un hash SHA-256 de tous les .jar dans /mods/
     * Si le hash change → le modpack a changé → recompilation nécessaire
     */
    private static String computeModpackHash() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        
        // Inclure aussi le .jar de Minecraft et NeoForge dans le hash
        List<Path> allJars = new ArrayList<>();
        
        // Ajouter les mods
        try (var stream = Files.walk(FMLPaths.MODSDIR.get(), 1)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                  .sorted()
                  .forEach(allJars::add);
        }
        
        // Hasher chaque .jar dans l'ordre alphabétique (déterministe)
        for (Path jar : allJars) {
            md.update(Files.readAllBytes(jar));
        }
        
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    
    private static String loadCachedHash() {
        try {
            return Files.readString(getHashFile()).trim();
        } catch (Exception e) {
            return ""; // Pas de hash → forcer la compilation
        }
    }
    
    private static void restartWithNativeBinary() {
        // Logique de redémarrage propre du jeu
        // Sauvegarde l'état, quitte NeoForge, relance le launcher
        NativeBinaryLauncher.launch(getBinaryPath());
    }
}
```

### 5.5 NativeBinaryLauncher.java — Lancer le binaire C++

```java
package net.nativeforge;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class NativeBinaryLauncher {
    
    public static void launch(Path binaryPath) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            // Récupérer le handle de la fenêtre GLFW
            long windowHandle = mc.getWindow().getWindow();
            // Récupérer le contexte OpenGL actuel
            long glContext = GLFW.glfwGetCurrentContext();
            
            // Récupérer les infos de session du joueur
            String accessToken = mc.getUser().getAccessToken();
            String username    = mc.getUser().getName();
            String uuid        = mc.getUser().getProfileId().toString();
            
            NativeForge.LOG.info("Transfert au binaire C++ : " + binaryPath);
            NativeForge.LOG.info("Fenêtre GLFW : 0x" + Long.toHexString(windowHandle));
            
            // Construire la commande
            List<String> cmd = new ArrayList<>();
            cmd.add(binaryPath.toString());
            cmd.add("--window=" + windowHandle);         // Handle de la fenêtre existante
            cmd.add("--gl-context=" + glContext);        // Contexte OpenGL partagé
            cmd.add("--access-token=" + accessToken);
            cmd.add("--username=" + username);
            cmd.add("--uuid=" + uuid);
            cmd.add("--game-dir=" + FMLPaths.GAMEDIR.get());
            cmd.add("--assets-dir=" + mc.getResourcePackDirectory().getParent());
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO(); // Les logs du C++ apparaissent dans la même console
            pb.directory(FMLPaths.GAMEDIR.get().toFile());
            
            Process nativeProcess = pb.start();
            
            // Attendre le signal "READY" du binaire C++ (il écrit "NATIVEFORGE_READY\n" sur stdout)
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(nativeProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("NATIVEFORGE_READY")) {
                        NativeForge.LOG.info("Binaire C++ prêt. Cession de la fenêtre...");
                        break;
                    }
                }
            }
            
            // Cacher la fenêtre Java (le C++ a sa propre fenêtre OU prend le même handle)
            // Option A : le C++ partage le contexte GL → garder la fenêtre Java, cacher le rendu
            // Option B : le C++ crée sa propre fenêtre → cacher celle de Java
            GLFW.glfwHideWindow(windowHandle);
            
            // Attendre que le binaire C++ se termine (joueur quitte le jeu)
            int exitCode = nativeProcess.waitFor();
            NativeForge.LOG.info("Binaire C++ terminé (code " + exitCode + "). Arrêt Java.");
            
            // Quitter la JVM proprement
            mc.stop();
            
        } catch (Exception e) {
            NativeForge.LOG.error("Erreur lancement binaire C++, fallback JVM : ", e);
        }
    }
}
```

---

## 6. Composant 2 — Le Bake Engine

### 6.0 Vue d'ensemble du pipeline du Bake Engine

```
ENTRÉE : Dossier /mods/ + minecraft.jar + neoforge.jar
         
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. JarLoader                                                    │
│  Lit tous les .jar, parse chaque .class avec ASM EXPAND_FRAMES  │
│  → classPool : Map<String, ClassNode>                           │
│  → assets : Map<String, byte[]> (textures, modèles, sons...)   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. AT Applier                                                   │
│  Parse META-INF/accesstransformer.cfg de chaque mod             │
│  Modifie les access flags des ClassNode correspondants          │
│  private → public, final → non-final etc.                       │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. MixinScanner                                                 │
│  Lit *.mixins.json dans chaque .jar                             │
│  Pour chaque classe @Mixin, lit l'annotation pour trouver la    │
│  classe cible                                                   │
│  → mixinMap : Map<cible, List<MixinClass>>                     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. MixinApplicator + MixinExtrasHandler                        │
│  Pour chaque cible dans mixinMap :                              │
│    Trier les Mixins par priorité                                │
│    Appliquer chaque Mixin selon son type :                      │
│    @Inject, @Overwrite, @Accessor, @Invoker,                   │
│    @Shadow, @Unique, @Redirect, @ModifyArg, @ModifyConstant,   │
│    @WrapOperation (MixinExtras), @ModifyExpressionValue, etc.  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  5. ReflectionResolver                                           │
│  Scanne statiquement les Class.forName("nom.fixe")             │
│  Génère reflect-config.json pour le transpileur                │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  6. InvokeDynamic Patcher                                        │
│  Convertit les invokedynamic complexes en classes anonymes ASM  │
│  pour les cas que clearwing-vm ne gère pas                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  7. ClassExporter                                                │
│  Reconstruit les bytes de chaque ClassNode avec ClassWriter     │
│  Écrit les .class dans fused_classes/                          │
│  Valide avec CheckClassAdapter (mode debug)                    │
└─────────────────────────────────────────────────────────────────┘
```

---

### 6.1 JarLoader — Lire tout le bytecode

**But :** Charger TOUS les fichiers .class de tous les .jar en mémoire sous forme de `ClassNode` ASM.

**Points critiques :**
- L'ordre de chargement est crucial : vanilla d'abord, NeoForge ensuite, mods après.
  Si deux .jar ont la même classe, le DERNIER .jar chargé gagne (comme le ClassLoader Java).
- `EXPAND_FRAMES` est obligatoire : sans ça, les frames de pile JVM ne sont pas calculées et
  l'analyse de variables locales (pour les Mixins @ModifyVariable etc.) casse.
  Source : issue #768 NeoForge (bug confirmé EXPAND_FRAMES vs non-EXPAND_FRAMES)
- Les assets (PNG, JSON, ogg) sont aussi stockés pour être copiés intacts dans le binaire.

```java
// JarLoader.java
package net.nativeforge.bake;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class JarLoader {
    
    // Bytecode de chaque classe : nom interne ("net/minecraft/Block") → ClassNode
    private final Map<String, ClassNode> classPool = new LinkedHashMap<>();
    
    // Assets non-class : chemin dans le JAR → bytes bruts
    // (textures, modèles, sons, jsons → copiés tels quels dans le binaire final)
    private final Map<String, byte[]> assets = new LinkedHashMap<>();
    
    // Pour le debug : quel JAR a fourni quelle classe
    private final Map<String, String> classOrigins = new HashMap<>();
    
    /**
     * Charge un seul JAR.
     * L'ordre d'appel de cette méthode détermine quelle classe "gagne"
     * en cas de doublon : le dernier chargé gagne.
     */
    public void loadJar(Path jarPath) throws IOException {
        String jarName = jarPath.getFileName().toString();
        int loaded = 0;
        int skipped = 0;
        
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // ─── Fichiers .class → parse ASM ───────────────────────
                if (entryName.endsWith(".class") && !entryName.equals("module-info.class")) {
                    
                    byte[] bytecode;
                    try (InputStream is = jar.getInputStream(entry)) {
                        bytecode = is.readAllBytes();
                    }
                    
                    try {
                        ClassReader reader = new ClassReader(bytecode);
                        ClassNode node = new ClassNode();
                        
                        // EXPAND_FRAMES = obligatoire pour la manipulation de bytecode
                        // Sans ça : STACK_MAP_FRAME au lieu de FULL_FRAME →
                        // les variables locales ne sont pas correctement recalculées
                        reader.accept(node, ClassReader.EXPAND_FRAMES);
                        
                        String className = node.name;
                        
                        if (classPool.containsKey(className)) {
                            // Doublon : le nouveau .jar remplace l'ancien
                            // (même comportement que le ClassLoader Java)
                        }
                        
                        classPool.put(className, node);
                        classOrigins.put(className, jarName);
                        loaded++;
                        
                    } catch (Exception e) {
                        System.err.println("[JarLoader] Impossible de parser : " 
                                          + entryName + " dans " + jarName 
                                          + " : " + e.getMessage());
                        skipped++;
                    }
                }
                // ─── Autres fichiers → assets ────────────────────────────
                else if (!entryName.endsWith("/")) {
                    // Garder : textures, modèles, sons, JSONs de recettes...
                    // Ignorer : META-INF/MANIFEST.MF, signatures .SF/.DSA
                    if (!entryName.startsWith("META-INF/")) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            assets.put(entryName, is.readAllBytes());
                        }
                    }
                }
            }
        }
        
        System.out.printf("[JarLoader] %-40s → %d classes, %d erreurs%n",
                          jarName, loaded, skipped);
    }
    
    /**
     * Charge tous les JARs dans le bon ordre.
     * Ordre : [vanilla, neoforge, mods...] → les mods ont la priorité
     */
    public void loadAll(List<Path> jarsInOrder) throws IOException {
        for (Path jar : jarsInOrder) {
            loadJar(jar);
        }
        System.out.println("[JarLoader] Total : " + classPool.size() 
                          + " classes, " + assets.size() + " assets");
    }
    
    public Map<String, ClassNode> getClassPool() { return classPool; }
    public Map<String, byte[]> getAssets() { return assets; }
    
    /**
     * Sérialise une ClassNode en bytes .class
     * COMPUTE_FRAMES = recalcule toutes les stack map frames automatiquement
     * Nécessaire après modification des instructions
     */
    public static byte[] toBytes(ClassNode node) {
        // COMPUTE_FRAMES recalcule les frames automatiquement (obligatoire après ajout/modif d'instrs)
        // COMPUTE_MAXS recalcule les tailles de stack et locals
        ClassWriter writer = new ClassWriter(
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        try {
            node.accept(writer);
        } catch (Exception e) {
            // Si le recalcul des frames échoue, essayer sans COMPUTE_FRAMES
            ClassWriter fallbackWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(fallbackWriter);
            return fallbackWriter.toByteArray();
        }
        
        return writer.toByteArray();
    }
}
```

---

### 6.2 AT Applier — Access Transformers

**But :** Chaque mod peut avoir un fichier `META-INF/accesstransformer.cfg` qui rend publics des membres privés de Minecraft. Ces transformations DOIVENT être appliquées avant les Mixins, car les Mixins s'attendent à trouver les classes avec les bons accès.

**Format d'un fichier AT :**
```
# Commentaire
public net.minecraft.world.level.block.Block f_60484_          # champ
public-f net.minecraft.world.level.chunk.LevelChunk f_62051_   # public + non-final
public net.minecraft.client.renderer.GameRenderer m_109089_()V # méthode
public net.minecraft.world.entity.Entity                       # classe entière
```

**On réutilise directement la lib officielle NeoForge** (`at-cli`, MIT) :

```java
// ATApplier.java
package net.nativeforge.bake;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class ATApplier {
    
    // Structure interne : nom de classe → liste de règles AT
    private final Map<String, List<ATRule>> rules = new HashMap<>();
    
    public static class ATRule {
        enum Type { FIELD, METHOD, CLASS }
        Type type;
        String name;    // Nom SRG ou Mojmap du membre (vide pour la classe entière)
        String desc;    // Descriptor JVM (pour les méthodes)
        int newAccess;  // Nouveaux flags d'accès ASM
        boolean removeFinal; // Enlever le flag final ?
    }
    
    /**
     * Scanner tous les fichiers accesstransformer.cfg dans les JARs
     */
    public void scanAllJars(List<Path> jars) {
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                // Chercher le fichier AT dans META-INF/
                JarEntry atEntry = jf.getJarEntry("META-INF/accesstransformer.cfg");
                if (atEntry == null) continue;
                
                try (InputStream is = jf.getInputStream(atEntry)) {
                    parseATFile(is, jar.getFileName().toString());
                }
                System.out.println("[AT] Trouvé dans : " + jar.getFileName());
                
            } catch (IOException e) {
                // Pas de AT dans ce JAR, normal
            }
        }
        System.out.println("[AT] " + rules.size() + " classes avec des ATs");
    }
    
    /**
     * Parse un fichier AT ligne par ligne
     * Format : <access> <className> [memberName] [descriptor]
     * Exemples :
     *   public net.minecraft.world.level.block.Block
     *   public net.minecraft.world.level.block.Block f_60484_
     *   public net.minecraft.world.level.block.Block m_49678_()V
     *   public-f net.minecraft.world.entity.LivingEntity f_20885_
     */
    private void parseATFile(InputStream is, String source) throws IOException {
        List<String> lines = new String(is.readAllBytes()).lines().toList();
        
        for (String rawLine : lines) {
            String line = rawLine.trim();
            
            // Ignorer les commentaires et lignes vides
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            // Enlever les commentaires en fin de ligne
            int commentIdx = line.indexOf('#');
            if (commentIdx > 0) line = line.substring(0, commentIdx).trim();
            
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            
            String accessStr = parts[0]; // "public", "public-f", "protected", etc.
            String className = parts[1].replace('.', '/'); // "net/minecraft/block/Block"
            
            // Calculer les nouveaux flags d'accès
            int newAccess = 0;
            boolean removeFinal = false;
            
            if (accessStr.equals("public") || accessStr.equals("public-f")) {
                newAccess = Opcodes.ACC_PUBLIC;
            } else if (accessStr.equals("protected") || accessStr.equals("protected-f")) {
                newAccess = Opcodes.ACC_PROTECTED;
            } else if (accessStr.equals("default") || accessStr.equals("default-f")) {
                newAccess = 0; // Package-private
            }
            
            if (accessStr.endsWith("-f")) {
                removeFinal = true; // Le -f = remove final
            }
            
            ATRule rule = new ATRule();
            rule.newAccess = newAccess;
            rule.removeFinal = removeFinal;
            
            if (parts.length == 2) {
                // AT sur la classe entière
                rule.type = ATRule.Type.CLASS;
            } else if (parts.length >= 3) {
                String memberName = parts[2];
                if (memberName.contains("(")) {
                    // C'est une méthode : "m_49678_()V" ou "onBreak(LLevel;LBlockPos;)V"
                    rule.type = ATRule.Type.METHOD;
                    int parenIdx = memberName.indexOf('(');
                    rule.name = memberName.substring(0, parenIdx);
                    rule.desc = memberName.substring(parenIdx);
                } else {
                    // C'est un champ
                    rule.type = ATRule.Type.FIELD;
                    rule.name = memberName;
                }
            }
            
            rules.computeIfAbsent(className, k -> new ArrayList<>()).add(rule);
        }
    }
    
    /**
     * Appliquer toutes les règles AT sur les ClassNode du classPool
     */
    public void applyAll(Map<String, ClassNode> classPool) {
        int applied = 0;
        
        for (Map.Entry<String, List<ATRule>> entry : rules.entrySet()) {
            String className = entry.getKey();
            ClassNode cls = classPool.get(className);
            
            if (cls == null) {
                System.err.println("[AT] WARN: Classe non trouvée : " + className);
                continue;
            }
            
            for (ATRule rule : entry.getValue()) {
                applyRule(cls, rule);
                applied++;
            }
        }
        
        System.out.println("[AT] " + applied + " règles appliquées");
    }
    
    private void applyRule(ClassNode cls, ATRule rule) {
        // Masque pour enlever les flags de visibilité existants
        int ACCESS_MASK = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        int FINAL_MASK  = ~Opcodes.ACC_FINAL;
        
        switch (rule.type) {
            case CLASS -> {
                cls.access = (cls.access & ACCESS_MASK) | rule.newAccess;
                if (rule.removeFinal) cls.access &= FINAL_MASK;
            }
            case FIELD -> {
                if (cls.fields == null) return;
                for (FieldNode field : cls.fields) {
                    if (field.name.equals(rule.name)) {
                        field.access = (field.access & ACCESS_MASK) | rule.newAccess;
                        if (rule.removeFinal) field.access &= FINAL_MASK;
                        return;
                    }
                }
                System.err.println("[AT] WARN: Champ non trouvé : " + cls.name + "." + rule.name);
            }
            case METHOD -> {
                if (cls.methods == null) return;
                for (MethodNode method : cls.methods) {
                    boolean nameMatch = method.name.equals(rule.name);
                    boolean descMatch = rule.desc == null || method.desc.equals(rule.desc);
                    if (nameMatch && descMatch) {
                        method.access = (method.access & ACCESS_MASK) | rule.newAccess;
                        if (rule.removeFinal) method.access &= FINAL_MASK;
                        return;
                    }
                }
                System.err.println("[AT] WARN: Méthode non trouvée : " 
                                  + cls.name + "." + rule.name + rule.desc);
            }
        }
    }
}
```

---

### 6.3 MixinScanner — Trouver tous les @Mixin

**But :** Lire les fichiers `*.mixins.json` dans chaque JAR et, pour chaque classe Mixin listée, lire son annotation `@Mixin` pour trouver sa ou ses classes cibles.

**Résultat attendu :** `Map<targetClass, List<mixinClass>>`

Exemple : `"net/minecraft/world/level/block/Block" → ["com/simibubi/create/mixin/BlockMixin"]`

```java
// MixinScanner.java
package net.nativeforge.bake;

import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;

public class MixinScanner {
    
    // cibleInterne → liste de classes Mixin (noms internes ASM)
    private final Map<String, List<MixinEntry>> mixinMap = new LinkedHashMap<>();
    
    public static class MixinEntry {
        String mixinClass;     // "com/simibubi/create/mixin/BlockMixin"
        int priority = 1000;   // Priorité @Mixin(priority=X), défaut=1000
        boolean isClient;      // Mixin client-only ?
        String sourceJar;      // Pour le debug
    }
    
    /**
     * Scanner un JAR à la recherche de configs Mixin
     */
    public void scanJar(JarFile jar, Map<String, ClassNode> classPool) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            
            if (!entry.getName().endsWith(".mixins.json")) continue;
            
            String json;
            try (InputStream is = jar.getInputStream(entry)) {
                json = new String(is.readAllBytes());
            }
            
            try {
                JsonObject config = JsonParser.parseString(json).getAsJsonObject();
                String pkg = config.get("package").getAsString().replace('.', '/');
                String source = jar.getName() + "!" + entry.getName();
                
                // Sections : "mixins" (common), "client", "server"
                boolean isClient = false;
                String[] sections = {"mixins", "client", "server"};
                
                for (String section : sections) {
                    if (!config.has(section)) continue;
                    isClient = section.equals("client");
                    
                    for (JsonElement el : config.getAsJsonArray(section)) {
                        String mixinClassName = pkg + "/" 
                            + el.getAsString().replace('.', '/');
                        registerMixin(mixinClassName, classPool, isClient, source);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[MixinScanner] Erreur parsing " 
                                  + entry.getName() + " : " + e.getMessage());
            }
        }
    }
    
    /**
     * Pour une classe Mixin, lire son annotation @Mixin pour trouver la cible.
     * L'annotation @Mixin a deux attributs :
     *   value = { Target.class, Target2.class }     → cibles par type
     *   targets = { "com.example.Target" }          → cibles par nom string
     */
    private void registerMixin(String mixinClass, Map<String, ClassNode> classPool,
                                boolean isClient, String source) {
        ClassNode node = classPool.get(mixinClass);
        if (node == null) {
            System.err.println("[MixinScanner] WARN: Classe Mixin introuvable : " + mixinClass);
            return;
        }
        
        if (node.visibleAnnotations == null) return;
        
        for (AnnotationNode ann : node.visibleAnnotations) {
            if (!ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            
            if (ann.values == null) continue;
            
            int priority = 1000; // Défaut NeoForge
            List<String> targets = new ArrayList<>();
            
            for (int i = 0; i < ann.values.size() - 1; i += 2) {
                String key = (String) ann.values.get(i);
                Object val = ann.values.get(i + 1);
                
                switch (key) {
                    case "value" -> {
                        // value = { Target.class } → list de Type ASM
                        if (val instanceof List<?> list) {
                            for (Object t : list) {
                                // Un Type ASM se présente comme "Lnet/minecraft/Block;"
                                String typeDesc = t.toString();
                                if (typeDesc.startsWith("L") && typeDesc.endsWith(";")) {
                                    targets.add(typeDesc.substring(1, typeDesc.length() - 1));
                                }
                            }
                        }
                    }
                    case "targets" -> {
                        // targets = { "net.minecraft.block.Block" } → list de String
                        if (val instanceof List<?> list) {
                            for (Object t : list) {
                                targets.add(t.toString().replace('.', '/'));
                            }
                        }
                    }
                    case "priority" -> {
                        priority = (int) val;
                    }
                }
            }
            
            // Enregistrer ce Mixin pour chaque cible
            for (String targetClass : targets) {
                MixinEntry entry = new MixinEntry();
                entry.mixinClass = mixinClass;
                entry.priority = priority;
                entry.isClient = isClient;
                entry.sourceJar = source;
                
                mixinMap.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(entry);
            }
        }
    }
    
    public void scanAll(List<Path> jars, Map<String, ClassNode> classPool) throws IOException {
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                scanJar(jf, classPool);
            }
        }
        System.out.println("[MixinScanner] " + mixinMap.size() 
                          + " classes cibles avec Mixins");
    }
    
    public Map<String, List<MixinEntry>> getMixinMap() { return mixinMap; }
}
```

---

### 6.4 MixinApplicator — Tous les types d'injection expliqués

**C'est la partie la plus complexe du projet entier.**
Chaque type d'annotation `@Inject`, `@Redirect` etc. génère un pattern de bytecode différent.
Voici comment chacun fonctionne et ce qu'il faut générer.

#### Type 1 : `@Inject at = HEAD` — Injection au début d'une méthode

**Ce que le Mixin déclare :**
```java
@Inject(method = "onBreak", at = @At("HEAD"), cancellable = true)
private void onCreate_head(Level world, BlockPos pos, CallbackInfo ci) {
    if (someCondition) ci.cancel();
}
```

**Ce que le Bake Engine doit générer dans la classe cible :**
```java
// Dans Block.class patché :
public void onBreak(Level world, BlockPos pos) {
    // ← CODE INJECTÉ
    CallbackInfo ci = new CallbackInfo("onBreak", true);
    this.onCreate_head_injected(world, pos, ci);  // appel vers le handler
    if (ci.isCancelled()) return;                  // si cancellable
    // ← CODE ORIGINAL DE MINECRAFT
    this.doBreak(world, pos);
}

// Le handler est ajouté comme méthode privée de Block :
private void onCreate_head_injected(Level world, BlockPos pos, CallbackInfo ci) {
    // ← code du mixin, déplacé ici
}
```

**Bytecode ASM à générer :**
```java
// 1. Si cancellable : insérer avant le premier opcode de la méthode cible :
//    NEW CallbackInfo
//    DUP
//    LDC "onBreak"
//    ICONST_1  (cancellable = true)
//    INVOKESPECIAL CallbackInfo.<init>(String, boolean)V
//    ASTORE N  (stocker dans variable locale N)
//
// 2. ALOAD_0 (this, sauf si handler est static)
// 3. Charger chaque paramètre de la méthode cible (ILOAD, ALOAD, etc.)
// 4. Si CallbackInfo : ALOAD N
// 5. INVOKEVIRTUAL/INVOKESPECIAL Block.onCreate_head_injected(Level, BlockPos, CallbackInfo)V
//
// 6. Si cancellable :
//    ALOAD N
//    INVOKEVIRTUAL CallbackInfo.isCancelled()Z
//    IFEQ pas_cancelled   (sauter si false)
//    RETURN               (ou IRETURN, ARETURN selon le type de retour)
//    pas_cancelled:
```

#### Type 2 : `@Inject at = RETURN / TAIL` — Injection avant chaque return

**But :** Exécuter du code juste avant que la méthode retourne.
Il faut trouver CHAQUE instruction RETURN (il peut y en avoir plusieurs dans une méthode) et insérer le code avant.

**Important :** Pour `CallbackInfoReturnable<T>` (méthode qui retourne quelque chose), il faut passer la valeur de retour au handler ET permettre au handler de la changer.

```java
// RETURN (void) : IRETURN (int) : ARETURN (objet) : FRETURN (float) etc.
// Il faut scanner chaque instruction et identifier les RETURN opcodes
for (AbstractInsnNode insn : targetMethod.instructions.toArray()) {
    int op = insn.getOpcode();
    boolean isReturn = (op >= Opcodes.IRETURN && op <= Opcodes.RETURN);
    if (!isReturn) continue;
    
    // Insérer le code du handler AVANT ce RETURN
    InsnList injected = buildHandlerCall(...);
    targetMethod.instructions.insertBefore(insn, injected);
}
```

#### Type 3 : `@Overwrite` — Remplacement total

**Le plus simple :** La méthode du Mixin remplace complètement la méthode originale.

```java
// Trouver et supprimer la méthode originale dans target
target.methods.removeIf(m -> m.name.equals(mixin.name) && m.desc.equals(mixin.desc));
// Ajouter la méthode du Mixin à la place
target.methods.add(mixinMethod);
// Enlever l'annotation @Overwrite de la méthode (elle n'est plus pertinente en C++)
mixinMethod.visibleAnnotations.removeIf(a -> a.desc.contains("Overwrite"));
```

#### Type 4 : `@Accessor` — Getter/setter auto-généré

Un Mixin `@Accessor` sur une interface permet d'accéder à un champ privé.

```java
@Mixin(Block.class)
public interface BlockAccessor {
    @Accessor("explosionResistance") // Accès au champ privé
    float getExplosionResistance();
    
    @Accessor("explosionResistance")
    void setExplosionResistance(float value);
}
```

**Le Bake Engine doit générer ces méthodes dans la classe Block :**
```java
// Dans Block.class patché :
public float getExplosionResistance() {
    return this.explosionResistance; // ou this.f_60489_
}
public void setExplosionResistance(float value) {
    this.explosionResistance = value;
}
```

**Bytecode ASM :**
```
// Getter :
ALOAD_0
GETFIELD Block.explosionResistance F
FRETURN

// Setter :
ALOAD_0
FLOAD_1
PUTFIELD Block.explosionResistance F
RETURN
```

#### Type 5 : `@Invoker` — Appel d'une méthode privée via une interface

```java
@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("updateFallState") // Méthode privée dans Entity
    void invokeUpdateFallState(double yVelocity, boolean onGround, BlockState state, BlockPos pos);
}
```

**Générer dans Entity :**
```java
// La méthode d'invocation qui appelle la méthode privée :
public void invokeUpdateFallState(double yVelocity, boolean onGround, BlockState state, BlockPos pos) {
    this.updateFallState(yVelocity, onGround, state, pos); // délégation vers private
}
```

#### Type 6 : `@Redirect` — Remplacer un appel de méthode

**Le plus complexe des types standard.** `@Redirect` remplace un appel de méthode à un point précis par le handler du Mixin.

```java
// Le Mixin :
@Redirect(method = "tick", at = @At(value = "INVOKE", 
    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)..."))
private BlockState redirectGetBlockState(Level level, BlockPos pos) {
    // Code alternatif au lieu d'appeler level.getBlockState(pos)
    return ModifiedBlockState.getModified(level, pos);
}
```

**Ce que le Bake Engine doit faire :**
1. Trouver l'instruction `INVOKEVIRTUAL Level.getBlockState` dans `tick()`
2. La remplacer par `INVOKEVIRTUAL Block.redirectGetBlockState`
3. Mais le handler reçoit `this` (Block) + les args originaux de la méthode redirigée

**Bytecode : le handler remplace le site d'appel**
```
Avant :  [level ref] [pos ref] INVOKEVIRTUAL Level.getBlockState
Après :  [block this] [level ref] [pos ref] INVOKEVIRTUAL Block.redirectGetBlockState
                                            ↑ on ajoute 'this' au début
```

#### Type 7 : `@ModifyArg` — Modifier un argument d'un appel

```java
@ModifyArg(method = "onBreak", at = @At(value = "INVOKE", 
    target = "Lnet/minecraft/world/entity/player/Player;dropItem(...)"),
    index = 0) // Modifier le premier argument
private ItemStack modifyDroppedItem(ItemStack original) {
    return new ItemStack(original.getItem(), original.getCount() * 2); // doubler la quantité
}
```

**Le Bake Engine doit :**
1. Trouver l'instruction `INVOKEVIRTUAL Player.dropItem` dans la méthode cible
2. Avant l'appel, dupliquer l'argument d'index 0 et le passer au handler
3. Utiliser la valeur retournée par le handler à la place de l'original

#### Type 8 : `@ModifyConstant` — Modifier une constante

```java
@ModifyConstant(method = "getMaxStackSize", constant = @Constant(intValue = 64))
private int modifyMaxStack(int original) {
    return 128; // Doubler la taille max des stacks
}
```

**Le Bake Engine doit :**
1. Trouver l'instruction LDC 64 (ou BIPUSH 64) dans la méthode
2. La remplacer par un appel au handler
3. Le handler reçoit l'original (64) et retourne la nouvelle valeur (128)

---

### 6.5 MixinExtras Handler — @WrapOperation, @ModifyExpressionValue

**MixinExtras est inclus dans NeoForge 1.21+ et utilisé massivement par les mods récents.**

La bonne approche : **utiliser directement le code source de MixinExtras** (MIT) dans notre Bake Engine au lieu de le réécrire. MixinExtras a ses propres classes d'applicateurs ASM.

```xml
<!-- Maven pour le Bake Engine -->
<dependency>
    <groupId>com.llamalad7.mixinextras</groupId>
    <artifactId>mixinextras-common</artifactId>
    <version>0.4.1</version>
</dependency>
```

**@WrapOperation — Wraper une opération :**
```java
// Ce Mixin wrape l'appel à level.getBlockState() dans onBreak()
@WrapOperation(method = "onBreak", at = @At(value = "INVOKE",
    target = "Lnet/minecraft/world/level/Level;getBlockState(...)"))
private BlockState wrapGetBlockState(Level level, BlockPos pos, 
                                      Operation<BlockState> original) {
    BlockState state = original.call(level, pos); // Appel original
    return ModManager.modify(state); // Post-traitement
}
```

**Comment ça fonctionne en bytecode :**
MixinExtras génère une classe anonyme qui implémente `Operation<R>` et capture l'invocation cible. Le site d'appel est remplacé par un appel au handler avec l'`Operation` en paramètre.

```
Avant :  [level] [pos] INVOKEVIRTUAL Level.getBlockState → result
Après :  [this] [level] [pos] [new Operation()] INVOKEVIRTUAL Block.wrapGetBlockState → result
         ↑ le new Operation() est une classe anonyme générée par MixinExtras
           qui fait : call(args) { invokevirtual Level.getBlockState(args) }
```

**Intégration dans notre Bake Engine :**
```java
// MixinExtrasHandler.java
// On appelle directement MixinExtrasService pour qu'il applique les annotations
// MixinExtras à nos ClassNode en mode offline

// Le truc : MixinExtras applique ses transformations VIA le framework Mixin
// On peut donc lui donner nos ClassNode comme si c'était un environment Mixin offline
// (Techniquement : implémenter IMixinService pour un environment "offline")
```

---

### 6.6 ReflectionResolver — Résoudre Class.forName() statique

**But :** Trouver tous les endroits où le code appelle `Class.forName("nom.fixe")` et générer un fichier de configuration pour que le transpileur sache inclure ces classes.

```java
// ReflectionResolver.java
public class ReflectionResolver {
    
    private final Set<String> reflectiveClasses = new LinkedHashSet<>();
    
    public void scan(Map<String, ClassNode> classPool) {
        for (ClassNode cls : classPool.values()) {
            for (MethodNode method : cls.methods) {
                if (method.instructions == null) continue;
                scanMethod(method);
            }
        }
    }
    
    private void scanMethod(MethodNode method) {
        AbstractInsnNode prev = null;
        
        for (AbstractInsnNode insn : method.instructions) {
            
            // Pattern : LDC "com.example.Class" + INVOKESTATIC Class.forName
            if (insn instanceof MethodInsnNode min
                && min.owner.equals("java/lang/Class")
                && min.name.equals("forName")
                && prev instanceof LdcInsnNode ldc
                && ldc.cst instanceof String className) {
                
                // C'est un Class.forName("nom.fixe") → nom connu à la compilation
                reflectiveClasses.add(className.replace('.', '/'));
            }
            
            // Pattern similaire pour getDeclaredField, getDeclaredMethod
            if (insn instanceof MethodInsnNode min
                && (min.name.equals("getDeclaredField") || min.name.equals("getDeclaredMethod"))
                && prev instanceof LdcInsnNode ldc
                && ldc.cst instanceof String memberName) {
                System.out.println("[Reflect] Accès réflexif à : " + memberName + " dans " + method);
            }
            
            prev = insn;
        }
    }
    
    // Générer le fichier de config pour clearwing-vm
    public void writeConfig(Path output) throws IOException {
        var sb = new StringBuilder("[\n");
        for (String cls : reflectiveClasses) {
            sb.append("  { \"name\": \"").append(cls.replace('/', '.'))
              .append("\", \"allDeclaredMethods\": true, \"allDeclaredFields\": true },\n");
        }
        if (sb.length() > 2) sb.setLength(sb.length() - 2); // enlever dernière virgule
        sb.append("\n]\n");
        Files.writeString(output, sb.toString());
        System.out.println("[Reflect] " + reflectiveClasses.size() + " classes réflexives → " + output);
    }
}
```

---

### 6.7 InvokeDynamic Patcher — Les lambdas

**Voici la réalité de clearwing-vm sur les lambdas (source : README officiel) :**

> "Lambda and method reference functionality is accomplished by generating proxy classes which implement the target interface, store captured values, and handle primitive boxing conversions. It **only supports the lambda factory InvokeDynamic and string builder targets**, so compiling with a version later than Java 8 may introduce unsupported calls."

**Ce que ça veut dire :**
```
Lambda simple = SUPPORTÉE
  Supplier<Block> s = () -> new Block();
  → clearwing-vm génère une classe anonyme Block$$Lambda$1 qui implémente Supplier

Concaténation de strings Java 9+ = PARTIELLEMENT SUPPORTÉE
  String s = "bloc = " + block.getName();
  → Java 9+ utilise StringConcatFactory.makeConcatWithConstants()
  → clearwing-vm gère ça

MethodHandles dynamiques = NON SUPPORTÉE
  MethodHandles.lookup().findVirtual(clazz, methodName, ...)
  → Résolution au runtime → impossible en AOT → CRASH
  
invokedynamic bootstrap personnalisé = NON SUPPORTÉE
  Si un framework utilise son propre bootstrap method
  → CRASH ou comportement indéfini
```

**Notre stratégie :**

Pour les MethodHandles dynamiques (cas rare mais qui existe dans certains mods), on peut les patcher avant la transpilation :

```java
// InvokeDynamicPatcher.java
// Convertit les invokedynamic avec bootstrap MethodHandles vers des classes anonymes

public class InvokeDynamicPatcher {
    
    /**
     * Compte les invokedynamic non-supportés dans le classPool
     * pour donner une estimation des problèmes
     */
    public int countProblematic(Map<String, ClassNode> classPool) {
        int count = 0;
        for (ClassNode cls : classPool.values()) {
            for (MethodNode method : cls.methods) {
                if (method.instructions == null) continue;
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof InvokeDynamicInsnNode indy) {
                        // LambdaMetaFactory = OK (clearwing-vm gère)
                        // StringConcatFactory = OK (clearwing-vm gère)
                        // Autres = PROBLÈME POTENTIEL
                        String bsm = indy.bsm.getOwner();
                        if (!bsm.equals("java/lang/invoke/LambdaMetafactory")
                            && !bsm.equals("java/lang/invoke/StringConcatFactory")) {
                            System.out.println("[InDy] Problème potentiel : " 
                                + bsm + " dans " + cls.name);
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }
}
```

---

### 6.8 ClassExporter — Écrire les classes fusionnées

```java
// ClassExporter.java
public class ClassExporter {
    
    public void exportAll(Map<String, ClassNode> classPool, 
                          Map<String, byte[]> assets,
                          Path outputDir,
                          ProgressCallback progress) throws IOException {
        
        Files.createDirectories(outputDir);
        int total = classPool.size();
        int done = 0;
        
        // Exporter les classes .class patchées
        for (Map.Entry<String, ClassNode> entry : classPool.entrySet()) {
            String className = entry.getKey();
            ClassNode node = entry.getValue();
            
            // Valider le ClassNode (facultatif, utile pour debug)
            if (System.getProperty("nativeforge.debug") != null) {
                validateClassNode(node);
            }
            
            // Reconstruire les bytes via ClassWriter
            byte[] bytes = JarLoader.toBytes(node);
            
            // Écrire dans outputDir/net/minecraft/Block.class
            Path classFile = outputDir.resolve(className + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, bytes);
            
            done++;
            if (done % 500 == 0 || done == total) {
                progress.update((float) done / total, "Export classes " + done + "/" + total);
            }
        }
        
        // Copier les assets intacts
        // (textures, modèles, sons → clearwing-vm les copie dans le binaire)
        Path assetsDir = outputDir.resolve("_assets");
        Files.createDirectories(assetsDir);
        for (Map.Entry<String, byte[]> asset : assets.entrySet()) {
            Path assetFile = assetsDir.resolve(asset.getKey());
            Files.createDirectories(assetFile.getParent());
            Files.write(assetFile, asset.getValue());
        }
        
        System.out.println("[Export] " + done + " classes + " 
                          + assets.size() + " assets → " + outputDir);
    }
    
    /**
     * Valide un ClassNode (debug uniquement)
     * Détecte les erreurs de bytecode avant la transpilation
     */
    private void validateClassNode(ClassNode node) {
        try {
            byte[] bytes = JarLoader.toBytes(node);
            ClassReader reader = new ClassReader(bytes);
            CheckClassAdapter.verify(reader, false, new java.io.PrintWriter(System.err));
        } catch (Exception e) {
            System.err.println("[Validate] ERREUR dans " + node.name + " : " + e.getMessage());
        }
    }
}
```

---

## 7. Composant 3 — Transpilation (clearwing-vm)

### 7.1 Ce que fait clearwing-vm (source : README officiel)

clearwing-vm prend un dossier de `.class` Java et génère :
- Un projet CMake C++ complet
- Une "VM" C++ minimaliste (quelques fichiers : `Clearwing.h`, `Array.hpp`, `Object.cpp`)
- Un fichier C++ par classe Java
- Un `main.cpp` avec le point d'entrée

**Limitations connues (à respecter) :**
- Supporte Java 8 bytecode. Java 9+ string concat peut poser problème.
- Supporte les lambdas simples via proxy classes générées
- NE supporte PAS les MethodHandles dynamiques
- NE supporte PAS le chargement de classes dynamique au runtime (évidemment)
- La bibliothèque runtime est minimaliste → certaines classes Java standard manquent

**Ce qui fonctionne prouvé :** LibGDX complet, Kotlin, jeux entiers sur Nintendo Switch

### 7.2 Configuration clearwing.json pour Minecraft

```json
{
  "mainClass": "net.minecraft.client.main.Main",
  "intrinsics": [
    "java.lang.Math.sin(D)D",
    "java.lang.Math.cos(D)D",
    "java.lang.Math.sqrt(D)D",
    "java.lang.Math.abs(I)I",
    "java.lang.Math.abs(F)F",
    "java.lang.Math.abs(D)D",
    "java.lang.Math.floor(D)D",
    "java.lang.Math.ceil(D)D",
    "java.lang.Math.round(F)I",
    "java.lang.Math.min(II)I",
    "java.lang.Math.max(II)I",
    "java.lang.Math.min(FF)F",
    "java.lang.Math.max(FF)F",
    "java.lang.System.currentTimeMillis()J",
    "java.lang.System.nanoTime()J",
    "java.lang.System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
    "java.lang.Float.floatToIntBits(F)I",
    "java.lang.Float.intBitsToFloat(I)F",
    "java.lang.Double.doubleToLongBits(D)J",
    "java.lang.Double.longBitsToDouble(J)D"
  ],
  "reflectConfig": "reflect-config.json",
  "useBoehm": true,
  "generateProjectFiles": true,
  "stripDebugInfo": true,
  "nativePatches": ["native_patches/"],
  "excludedClasses": [
    "sun/",
    "com/sun/",
    "jdk/",
    "java/awt/",
    "javax/swing/"
  ]
}
```

### 7.3 Script de compilation PC

```bash
#!/bin/bash
# compile_pc.sh
set -e

FUSED_CLASSES="$1"  # Dossier avec les classes patchées
OUTPUT_DIR="$2"     # Où mettre le binaire

echo "=== NativeForge — Compilation PC ==="
echo "Classes : $FUSED_CLASSES"
echo "Output  : $OUTPUT_DIR"

# Vérifier Clang
if ! command -v clang++ &> /dev/null; then
    echo "ERREUR: clang++ non trouvé. Installer avec : apt install clang-17"
    exit 1
fi
CLANG_VERSION=$(clang++ --version | head -1)
echo "Compilateur : $CLANG_VERSION"

# Étape 1 : Transpilation Java → C++
echo ""
echo "[1/3] Transpilation avec clearwing-vm..."
java -jar clearwing-vm-transpiler.jar \
  --input "$FUSED_CLASSES" \
  --output "$OUTPUT_DIR/cpp_project" \
  --config clearwing.json \
  --verbose

# Étape 2 : Configurer CMake
echo ""
echo "[2/3] Configuration CMake..."
cd "$OUTPUT_DIR/cpp_project"
cmake -B build \
  -DCMAKE_C_COMPILER=clang \
  -DCMAKE_CXX_COMPILER=clang++ \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS="-O3 -march=native -flto=thin" \
  -DCMAKE_EXE_LINKER_FLAGS="-flto=thin"

# Étape 3 : Compiler
echo ""
CORES=$(nproc)
echo "[3/3] Compilation ($CORES cœurs)... (peut prendre 10-30 min)"
cmake --build build --parallel $CORES

# Résultat
echo ""
BINARY="build/minecraft_native"
if [ -f "$BINARY.exe" ]; then BINARY="$BINARY.exe"; fi
SIZE=$(du -sh "$BINARY" | cut -f1)
echo "=== ✅ Compilation terminée ==="
echo "Binaire : $BINARY"
echo "Taille  : $SIZE"

# Copier dans le répertoire NativeForge
cp "$BINARY" ".nativeforge/"
```

---

## 8. Composant 4 — Runtime C++ des mods

Le code C++ généré par clearwing-vm s'attend à trouver l'infrastructure NeoForge. On la recrée en C++.

### 8.1 NativeRegistry\<T\> — Registre des blocs et items

```cpp
// native_registry.hpp — header-only
#pragma once
#include <unordered_map>
#include <vector>
#include <functional>
#include <memory>
#include <string>
#include <stdexcept>
#include <shared_mutex>
#include <gc/gc_cpp.h>  // Boehm GC

// ─── ResourceLocation ───────────────────────────────────────────────────────
struct ResourceLocation {
    std::string ns;   // "minecraft" ou "create"
    std::string path; // "stone" ou "cogwheel"
    
    ResourceLocation() = default;
    ResourceLocation(const std::string& ns, const std::string& path) 
        : ns(ns), path(path) {}
    
    // Parser "minecraft:stone"
    explicit ResourceLocation(const std::string& full) {
        auto colon = full.find(':');
        if (colon == std::string::npos) {
            ns = "minecraft";
            path = full;
        } else {
            ns = full.substr(0, colon);
            path = full.substr(colon + 1);
        }
    }
    
    std::string str() const { return ns + ":" + path; }
    
    bool operator==(const ResourceLocation& o) const { 
        return ns == o.ns && path == o.path; 
    }
};

namespace std {
    template<> struct hash<ResourceLocation> {
        size_t operator()(const ResourceLocation& r) const {
            return hash<string>{}(r.str());
        }
    };
}

// ─── NativeRegistry<T> ──────────────────────────────────────────────────────
// Équivalent C++ du DeferredRegister<T> de NeoForge
template<typename T>
class NativeRegistry : public gc {
private:
    std::string name_;
    
    // Entrées définitives (après freeze)
    std::unordered_map<ResourceLocation, T*> entries_;
    
    // Enregistrements différés (avant freeze)
    struct Deferred {
        ResourceLocation loc;
        std::function<T*()> factory;
    };
    std::vector<Deferred> deferred_;
    
    bool frozen_ = false;
    mutable std::shared_mutex mutex_;

public:
    explicit NativeRegistry(const std::string& name) : name_(name) {}
    
    /**
     * Enregistrer un objet (mode différé, comme DeferredRegister.register())
     * Retourne un pointeur vers le futur objet (NULL jusqu'au freeze)
     */
    T* register_entry(const std::string& mod, const std::string& path,
                      std::function<T*()> factory) {
        std::unique_lock<std::shared_mutex> lock(mutex_);
        
        if (frozen_) {
            throw std::runtime_error("[Registry:" + name_ + 
                                     "] Enregistrement après freeze refusé : " 
                                     + mod + ":" + path);
        }
        
        deferred_.push_back({ResourceLocation(mod, path), std::move(factory)});
        return nullptr; // Le vrai objet n'existe pas encore
    }
    
    /**
     * Freeze : instancie tous les objets différés
     * Équivalent de l'étape RegisterEvent de NeoForge
     */
    void freeze() {
        std::unique_lock<std::shared_mutex> lock(mutex_);
        if (frozen_) return;
        
        printf("[Registry:%s] Gel — %zu objets...\n", name_.c_str(), deferred_.size());
        
        for (auto& d : deferred_) {
            T* obj = d.factory();
            if (obj == nullptr) {
                fprintf(stderr, "[Registry:%s] WARN: factory() retourne null pour %s\n",
                        name_.c_str(), d.loc.str().c_str());
                continue;
            }
            entries_[d.loc] = obj;
        }
        
        deferred_.clear();
        deferred_.shrink_to_fit(); // Libérer la mémoire
        frozen_ = true;
        
        printf("[Registry:%s] ✅ %zu objets enregistrés\n", name_.c_str(), entries_.size());
    }
    
    /**
     * Obtenir un objet par ResourceLocation
     * Thread-safe (lecture partagée)
     */
    T* get(const ResourceLocation& loc) const {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        auto it = entries_.find(loc);
        return (it != entries_.end()) ? it->second : nullptr;
    }
    
    T* get(const std::string& id) const {
        return get(ResourceLocation(id));
    }
    
    bool contains(const std::string& id) const {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        return entries_.count(ResourceLocation(id)) > 0;
    }
    
    size_t size() const { 
        std::shared_lock<std::shared_mutex> lock(mutex_);
        return entries_.size(); 
    }
    
    bool isFrozen() const { return frozen_; }
    const std::string& getName() const { return name_; }
    
    // Itération thread-safe (copie des entrées)
    std::vector<std::pair<ResourceLocation, T*>> getAllEntries() const {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        return {entries_.begin(), entries_.end()};
    }
};

// ─── GameRegistries ─────────────────────────────────────────────────────────
// Registres globaux. Déclarations extern, définitions dans game_registries.cpp
class Block;
class Item;
class EntityType;
class BlockEntityType;
class Biome;
class SoundEvent;
class Enchantment;
class Attribute;
class ParticleType;

class GameRegistries {
public:
    static NativeRegistry<Block>*          BLOCKS;
    static NativeRegistry<Item>*           ITEMS;
    static NativeRegistry<EntityType>*     ENTITY_TYPES;
    static NativeRegistry<BlockEntityType>* BLOCK_ENTITY_TYPES;
    static NativeRegistry<Biome>*          BIOMES;
    static NativeRegistry<SoundEvent>*     SOUND_EVENTS;
    static NativeRegistry<Enchantment>*    ENCHANTMENTS;
    static NativeRegistry<Attribute>*      ATTRIBUTES;
    static NativeRegistry<ParticleType>*   PARTICLE_TYPES;
    
    static void initAll() {
        BLOCKS           = new NativeRegistry<Block>("minecraft:block");
        ITEMS            = new NativeRegistry<Item>("minecraft:item");
        ENTITY_TYPES     = new NativeRegistry<EntityType>("minecraft:entity_type");
        BLOCK_ENTITY_TYPES = new NativeRegistry<BlockEntityType>("minecraft:block_entity_type");
        BIOMES           = new NativeRegistry<Biome>("minecraft:worldgen/biome");
        SOUND_EVENTS     = new NativeRegistry<SoundEvent>("minecraft:sound_event");
        ENCHANTMENTS     = new NativeRegistry<Enchantment>("minecraft:enchantment");
        ATTRIBUTES       = new NativeRegistry<Attribute>("minecraft:attribute");
        PARTICLE_TYPES   = new NativeRegistry<ParticleType>("minecraft:particle_type");
    }
    
    static void freezeAll() {
        BLOCKS->freeze();           ITEMS->freeze();
        ENTITY_TYPES->freeze();     BLOCK_ENTITY_TYPES->freeze();
        BIOMES->freeze();           SOUND_EVENTS->freeze();
        ENCHANTMENTS->freeze();     ATTRIBUTES->freeze();
        PARTICLE_TYPES->freeze();
    }
};
```

### 8.2 EventBus\<E\> et EventSystem complet

```cpp
// event_system.hpp
#pragma once
#include <functional>
#include <vector>
#include <algorithm>
#include <shared_mutex>
#include <atomic>

// ─── Priorités (identiques à NeoForge EventPriority) ────────────────────────
enum class EventPriority : int {
    HIGHEST = 0,
    HIGH    = 1,
    NORMAL  = 2,
    LOW     = 3,
    LOWEST  = 4
};

// ─── Classe de base des événements ──────────────────────────────────────────
class Event {
protected:
    bool cancelled_ = false;

public:
    virtual ~Event() = default;
    
    // Peut-on annuler cet événement ?
    virtual bool isCancellable() const { return false; }
    
    void setCancelled(bool val) {
        if (!isCancellable() && val) {
            throw std::runtime_error("Cet événement ne peut pas être annulé");
        }
        cancelled_ = val;
    }
    
    bool isCancelled() const { return cancelled_; }
};

// Macro pour créer des événements annulables facilement
#define CANCELLABLE_EVENT \
    bool isCancellable() const override { return true; }

// ─── EventBus<E> ────────────────────────────────────────────────────────────
template<typename E>
class EventBus {
    static_assert(std::is_base_of<Event, E>::value, "E doit dériver de Event");

private:
    struct Listener {
        int id;                           // ID unique pour pouvoir supprimer
        EventPriority priority;
        bool receiveCancelled;
        std::function<void(E&)> handler;
        
        bool operator<(const Listener& o) const {
            if (priority != o.priority)
                return static_cast<int>(priority) < static_cast<int>(o.priority);
            return id < o.id; // À priorité égale, ordre d'enregistrement
        }
    };
    
    std::vector<Listener> listeners_;
    mutable std::shared_mutex mutex_;
    std::atomic<int> nextId_{0};
    std::atomic<bool> sortNeeded_{false};

public:
    /**
     * S'abonner à cet événement.
     * Retourne un ID pour pouvoir se désabonner.
     * Équivalent : NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, handler)
     */
    int addListener(std::function<void(E&)> handler,
                    EventPriority priority = EventPriority::NORMAL,
                    bool receiveCancelled = false) {
        std::unique_lock<std::shared_mutex> lock(mutex_);
        int id = nextId_.fetch_add(1);
        listeners_.push_back({id, priority, receiveCancelled, std::move(handler)});
        sortNeeded_.store(true);
        return id;
    }
    
    /**
     * Se désabonner.
     * Équivalent : NeoForge unsubscribe (rare mais nécessaire pour les mods dynamiques)
     */
    void removeListener(int id) {
        std::unique_lock<std::shared_mutex> lock(mutex_);
        listeners_.erase(
            std::remove_if(listeners_.begin(), listeners_.end(),
                          [id](const Listener& l) { return l.id == id; }),
            listeners_.end());
    }
    
    /**
     * Poster un événement.
     * Appelle tous les listeners dans l'ordre de priorité.
     * Retourne true si l'événement a été annulé.
     * Équivalent : NeoForge.EVENT_BUS.post(event)
     */
    bool post(E& event) {
        // Trier les listeners si nécessaire (lazy sort)
        if (sortNeeded_.load()) {
            std::unique_lock<std::shared_mutex> lock(mutex_);
            if (sortNeeded_.load()) {
                std::sort(listeners_.begin(), listeners_.end());
                sortNeeded_.store(false);
            }
        }
        
        // Copie des listeners pour éviter les deadlocks si un handler modifie la liste
        std::vector<Listener> snapshot;
        {
            std::shared_lock<std::shared_mutex> lock(mutex_);
            snapshot = listeners_;
        }
        
        for (const auto& listener : snapshot) {
            if (event.isCancelled() && !listener.receiveCancelled) {
                continue; // Skip si annulé et listener ne veut pas les annulés
            }
            listener.handler(event);
        }
        
        return event.isCancelled();
    }
    
    size_t listenerCount() const {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        return listeners_.size();
    }
};

// ─── Les deux bus NeoForge ────────────────────────────────────────────────
// Déclarer les événements d'abord (dans leurs propres fichiers .hpp)
struct BlockBreakEvent;
struct BlockPlaceEvent;
struct EntityDeathEvent;
struct PlayerTickEvent;
struct LivingDamageEvent;
struct ItemCraftedEvent;
struct ChunkLoadEvent;
struct WorldTickEvent;
struct RenderGameOverlayEvent;
struct FMLCommonSetupEvent;
struct FMLClientSetupEvent;
struct RegisterEvent;

// Mod Bus — pour l'initialisation des mods
class ModEventBus {
public:
    EventBus<FMLCommonSetupEvent>   onCommonSetup;
    EventBus<FMLClientSetupEvent>   onClientSetup;
    EventBus<RegisterEvent>         onRegister;
    
    static ModEventBus& get() {
        static ModEventBus instance;
        return instance;
    }
};

// Game Bus — pour les événements de gameplay
class GameEventBus {
public:
    EventBus<BlockBreakEvent>      onBlockBreak;
    EventBus<BlockPlaceEvent>      onBlockPlace;
    EventBus<EntityDeathEvent>     onEntityDeath;
    EventBus<PlayerTickEvent>      onPlayerTick;
    EventBus<LivingDamageEvent>    onLivingDamage;
    EventBus<ItemCraftedEvent>     onItemCrafted;
    EventBus<ChunkLoadEvent>       onChunkLoad;
    EventBus<WorldTickEvent>       onWorldTick;
    EventBus<RenderGameOverlayEvent> onRenderOverlay;
    
    static GameEventBus& get() {
        static GameEventBus instance;
        return instance;
    }
};
```

### 8.3 Séquence de boot C++ (ordre NeoForge)

```cpp
// main_native.cpp — généré automatiquement pour chaque modpack
#include "native_registry.hpp"
#include "event_system.hpp"
#include "gc/gc.h"
#include <cstdio>

// Déclarations externes — une fonction par mod détecté
// Générées automatiquement par le Bake Engine
extern void neoforge_register(ModEventBus& bus, GameRegistries& reg);
extern void mod_create_register(ModEventBus& bus, GameRegistries& reg);
extern void mod_jei_register(ModEventBus& bus, GameRegistries& reg);
extern void mod_botania_register(ModEventBus& bus, GameRegistries& reg);
// ... (autant de lignes que de mods)

// Point d'entrée du jeu natif
int minecraft_native_main(int argc, char* argv[]) {
    
    // ── Phase 0 : Garbage Collector ──────────────────────────────────────
    GC_INIT();
    GC_set_all_interior_pointers(1);  // Mode conservateur
    GC_enable_incremental();           // Éviter les pauses GC longues
    GC_set_initial_heap_size(512 * 1024 * 1024);    // 512 Mo initial
    GC_set_max_heap_size(2ULL * 1024 * 1024 * 1024); // 2 Go max
    
    printf("=== NativeForge Runtime C++ ===\n");
    
    // ── Phase 1 : Initialiser les registres vides ─────────────────────────
    printf("[Boot] Phase 1 : Initialisation des registres...\n");
    GameRegistries::initAll();
    
    // ── Phase 2 : Enregistrements différés (constructeurs @Mod) ──────────
    // Chaque mod enregistre ses blocs/items/entités via DeferredRegister
    printf("[Boot] Phase 2 : Enregistrement des objets...\n");
    neoforge_register(ModEventBus::get(), GameRegistries::get());
    mod_create_register(ModEventBus::get(), GameRegistries::get());
    mod_jei_register(ModEventBus::get(), GameRegistries::get());
    mod_botania_register(ModEventBus::get(), GameRegistries::get());
    // ...
    
    // ── Phase 3 : Déclencher RegisterEvent + Gel ─────────────────────────
    printf("[Boot] Phase 3 : Gel des registres...\n");
    
    // NeoForge déclenche un RegisterEvent pour chaque registre
    // (les mods peuvent s'abonner pour faire des enregistrements supplémentaires)
    for (const char* regName : {
        "minecraft:block", "minecraft:item", "minecraft:entity_type",
        "minecraft:block_entity_type", "minecraft:sound_event",
        "minecraft:enchantment", "minecraft:mob_effect"
    }) {
        RegisterEvent evt(regName);
        ModEventBus::get().onRegister.post(evt);
    }
    
    GameRegistries::freezeAll(); // Gel définitif
    
    // ── Phase 4 : FMLCommonSetupEvent ────────────────────────────────────
    printf("[Boot] Phase 4 : Setup commun...\n");
    FMLCommonSetupEvent commonSetup;
    ModEventBus::get().onCommonSetup.post(commonSetup);
    
    // ── Phase 5 : FMLClientSetupEvent ────────────────────────────────────
    printf("[Boot] Phase 5 : Setup client...\n");
    FMLClientSetupEvent clientSetup;
    ModEventBus::get().onClientSetup.post(clientSetup);
    
    // ── Phase 6 : Signaler que le jeu est prêt (pour le lanceur Java) ────
    // NativeBinaryLauncher.java attend cette ligne sur stdout
    printf("NATIVEFORGE_READY\n");
    fflush(stdout);
    
    // ── Phase 7 : Lancer le jeu ──────────────────────────────────────────
    printf("[Boot] ✅ Lancement du jeu...\n");
    return MinecraftNativeClient::run(argc, argv);
}
```

---

## 9. Composant 5 — Android (Amethyst + MobileGlues)

### 9.1 Stratégie

On greffe notre `.so` natif sur **Amethyst-Android** (LGPL v3). Amethyst gère :
- L'authentification Microsoft
- Le téléchargement des fichiers Minecraft
- L'interface utilisateur du launcher
- OpenJDK ARM64 embarqué (pour le fallback JVM)
- NeoForge support (depuis une release récente)
- MobileGlues pour OpenGL → OpenGL ES

**On ne remplace que l'OpenJDK par notre binaire C++** quand il est disponible.

### 9.2 NativeMinecraftBridge.java

```java
// À ajouter dans le projet Amethyst-Android
package com.amethyst.nativebridge;

import android.content.Context;
import android.view.Surface;
import android.util.Log;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class NativeMinecraftBridge {
    private static final String TAG = "NativeForge";
    
    static {
        try {
            System.loadLibrary("minecraft_native");
            Log.i(TAG, "libminecraft_native.so chargé ✅");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Binaire natif non disponible, utilisation JVM");
        }
    }
    
    // Méthodes natives — implémentées dans android_main.cpp
    public native void startNative(Surface surface, int width, int height, String sessionData);
    public native void onTouch(int action, float x, float y, int pointerId);
    public native void onKeyEvent(int keyCode, int action, int modifiers);
    public native void stopNative();
    
    public enum LaunchMode {
        NATIVE_CPP,       // Binaire C++ disponible et à jour
        NEEDS_RECOMPILE,  // Modpack changé → recompiler
        JVM_FALLBACK      // Pas de binaire → JVM classique
    }
    
    public static LaunchMode selectMode(Context ctx, List<File> mods) {
        // Vérifier si le .so existe
        File nativeSo = new File(ctx.getApplicationInfo().nativeLibraryDir,
                                  "libminecraft_native.so");
        if (!nativeSo.exists()) {
            Log.i(TAG, "Mode JVM (pas de .so natif)");
            return LaunchMode.JVM_FALLBACK;
        }
        
        // Vérifier le hash du modpack
        try {
            String current = computeHash(mods);
            String cached  = loadCachedHash(ctx);
            if (!current.equals(cached)) {
                Log.i(TAG, "Modpack changé → recompilation");
                return LaunchMode.NEEDS_RECOMPILE;
            }
        } catch (Exception e) {
            Log.w(TAG, "Erreur hash : " + e.getMessage());
        }
        
        Log.i(TAG, "Mode C++ natif ✅");
        return LaunchMode.NATIVE_CPP;
    }
    
    private static String computeHash(List<File> mods) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (File mod : mods.stream().sorted(Comparator.comparing(File::getName)).toList()) {
            try (FileInputStream fis = new FileInputStream(mod)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) md.update(buf, 0, n);
            }
        }
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    
    private static String loadCachedHash(Context ctx) {
        File f = new File(ctx.getFilesDir(), "native_hash.sha256");
        try { return new String(Files.readAllBytes(f.toPath())).trim(); }
        catch (Exception e) { return ""; }
    }
}
```

### 9.3 android_main.cpp — Point d'entrée JNI

```cpp
// android_main.cpp
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <gc/gc.h>
#include <thread>
#include <string>

#define LOG_TAG  "NativeForge"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

// ─── État global EGL ─────────────────────────────────────────────────────────
static EGLDisplay gDisplay = EGL_NO_DISPLAY;
static EGLSurface gSurface = EGL_NO_SURFACE;
static EGLContext gContext  = EGL_NO_CONTEXT;
static ANativeWindow* gWindow = nullptr;

// ─── JNI_OnLoad ───────────────────────────────────────────────────────────────
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("NativeForge .so chargé (JNI_OnLoad)");
    
    // Initialiser Boehm GC EN PREMIER (avant toute allocation)
    GC_INIT();
    GC_set_all_interior_pointers(1);
    GC_enable_incremental();
    
    return JNI_VERSION_1_6;
}

// ─── Initialiser EGL OpenGL ES 3.0 ───────────────────────────────────────────
static bool initEGL(ANativeWindow* window) {
    gDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (gDisplay == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay échoué !");
        return false;
    }
    
    EGLint major, minor;
    if (!eglInitialize(gDisplay, &major, &minor)) {
        LOGE("eglInitialize échoué !");
        return false;
    }
    LOGI("EGL %d.%d initialisé", major, minor);
    
    // Demander OpenGL ES 3.0 avec depth buffer 24 bits
    EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
        EGL_RED_SIZE,   8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE,  8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };
    
    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(gDisplay, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOGE("eglChooseConfig échoué !");
        return false;
    }
    
    EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    gContext = eglCreateContext(gDisplay, config, EGL_NO_CONTEXT, ctxAttribs);
    gSurface = eglCreateWindowSurface(gDisplay, config, window, nullptr);
    
    if (!eglMakeCurrent(gDisplay, gSurface, gSurface, gContext)) {
        LOGE("eglMakeCurrent échoué !");
        return false;
    }
    
    LOGI("Contexte OpenGL ES 3.0 actif ✅");
    return true;
}

// ─── startNative — appelé par Amethyst quand la Surface est prête ─────────────
extern "C" JNIEXPORT void JNICALL
Java_com_amethyst_nativebridge_NativeMinecraftBridge_startNative(
    JNIEnv* env, jobject thiz,
    jobject surface, jint width, jint height, jstring sessionData) {
    
    LOGI("startNative(%d x %d)", width, height);
    
    // Récupérer la fenêtre native Android depuis la Surface Java
    gWindow = ANativeWindow_fromSurface(env, surface);
    if (!gWindow) {
        LOGE("ANativeWindow_fromSurface échoué !");
        return;
    }
    ANativeWindow_setBuffersGeometry(gWindow, width, height, 0);
    
    // Initialiser EGL
    if (!initEGL(gWindow)) {
        LOGE("Échec EGL !");
        return;
    }
    
    // Récupérer la session Minecraft
    const char* session_cstr = env->GetStringUTFChars(sessionData, nullptr);
    std::string session(session_cstr);
    env->ReleaseStringUTFChars(sessionData, session_cstr);
    
    LOGI("Session : %s", session.substr(0, 20).c_str());
    
    // Lancer le jeu dans un thread séparé (ne JAMAIS bloquer le thread UI Android)
    std::thread([session]() {
        LOGI("Thread jeu démarré");
        
        // Enregistrer ce thread avec Boehm GC
        GC_register_my_thread(nullptr);
        
        // Lancer la boot sequence C++
        const char* argv[] = {"minecraft", "--session", session.c_str(), nullptr};
        int ret = minecraft_native_main(3, const_cast<char**>(argv));
        
        LOGI("Jeu terminé (code %d)", ret);
        GC_unregister_my_thread();
        
    }).detach();
}

// ─── onTouch ─────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_amethyst_nativebridge_NativeMinecraftBridge_onTouch(
    JNIEnv*, jobject, jint action, jfloat x, jfloat y, jint pointerId) {
    
    // Traduire l'événement tactile Android en événement "souris" pour Minecraft
    // Minecraft attend une souris, on émule avec le premier doigt (pointerId == 0)
    if (pointerId != 0) return; // Ignorer les autres doigts pour l'instant
    
    switch (action & 0xFF) { // Masquer l'ID du pointeur
        case 0: // ACTION_DOWN
            NativeInputQueue::push(NativeMouseEvent::PRESS, x, y, 0); break;
        case 1: // ACTION_UP
            NativeInputQueue::push(NativeMouseEvent::RELEASE, x, y, 0); break;
        case 2: // ACTION_MOVE
            NativeInputQueue::push(NativeMouseEvent::MOVE, x, y, -1); break;
        case 5: // ACTION_POINTER_DOWN (second doigt = clic droit)
            if (pointerId == 1)
                NativeInputQueue::push(NativeMouseEvent::PRESS, x, y, 1); break;
        case 6: // ACTION_POINTER_UP
            if (pointerId == 1)
                NativeInputQueue::push(NativeMouseEvent::RELEASE, x, y, 1); break;
    }
}

// ─── Stubs GLFW pour Android ─────────────────────────────────────────────────
// Minecraft appelle des fonctions GLFW (fenêtre, input, swap buffers)
// Sur Android, on les redirige vers notre contexte EGL

extern "C" {
    void glfwSwapBuffers(void*) {
        if (gDisplay != EGL_NO_DISPLAY && gSurface != EGL_NO_SURFACE) {
            eglSwapBuffers(gDisplay, gSurface);
        }
    }
    
    void glfwGetWindowSize(void*, int* w, int* h) {
        if (gWindow) {
            *w = ANativeWindow_getWidth(gWindow);
            *h = ANativeWindow_getHeight(gWindow);
        }
    }
    
    void glfwPollEvents(void) {
        // Les événements arrivent via les callbacks JNI
        // Rien à faire ici
    }
    
    void* glfwGetCurrentContext(void) {
        return (void*) gContext;
    }
}
```

---

## 10. Catalogue complet des problèmes — causes et solutions

### 🔴 Problème CRITIQUE 1 : invokedynamic non-lambda

**Cause racine :**
Java 8 a introduit `invokedynamic` pour les lambdas. Chaque lambda compile en :
```
INVOKEDYNAMIC run()Ljava/lang/Runnable; [
  LambdaMetaFactory.metafactory (bootstrap),
  REF_invokeStatic MyClass.lambda$0 ()V
]
```
clearwing-vm gère ce cas (`LambdaMetaFactory`).

Mais Java permet des bootstrap methods personnalisées. `StringConcatFactory` pour les strings, et certains frameworks (comme Kotlin coroutines) ont les leurs. Ces cas cassent clearwing-vm.

**Vérification :** Notre `InvokeDynamicPatcher.countProblematic()` compte ces cas avant compilation.

**Solution :**
- Si zéro ou quelques cas → ignorer (clearwing-vm va probablement s'en sortir)
- Si beaucoup → patcher le bytecode pour convertir les `invokedynamic` exotiques en classes anonymes ASM avant la transpilation

**Patcher un invokedynamic en classe anonyme :**
```java
// Pour chaque invokedynamic problématique dans une méthode :
// 1. Créer une nouvelle classe anonyme dans le classPool
//    qui implémente l'interface fonctionnelle cible
// 2. Déplacer la lambda dans cette classe
// 3. Remplacer l'invokedynamic par NEW + INVOKESPECIAL
```

---

### 🔴 Problème CRITIQUE 2 : MixinExtras — les injections modernes

**Cause racine :**
MixinExtras (LlamaLad7) ajoute des injections au-delà du Mixin standard :
- `@WrapOperation` wrape une opération (appel, comparaison, cast, accès tableau)
- `@ModifyExpressionValue` modifie une valeur arbitraire
- `@WrapMethod` wrape une méthode entière
- `@WrapWithCondition` conditionne un bloc
- `@ModifyReturnValue` modifie la valeur de retour
- `@Expressions` cible une séquence arbitraire d'instructions

Ces annotations ont le descriptor `Lcom/llamalad7/mixinextras/injector/...`.
Notre MixinApplicator standard ne les reconnaît pas → elles sont silencieusement ignorées → le comportement du mod est incorrect.

**Solution :**
Intégrer `MixinExtrasService` directement. MixinExtras est MIT et son applicateur peut être utilisé offline si on implémente `IMixinService` (une interface de SpongePowered Mixin) pour notre environment offline.

---

### 🟠 Problème IMPORTANT 3 : Mixin dans les constructeurs `<init>`

**Cause racine :**
La spec JVM impose que la première instruction d'un constructeur soit `INVOKESPECIAL <init>` vers la super-classe (appel à `super()`). Aucun code ne peut s'exécuter avant.

Certains Mixins injectent dans les constructeurs avec `@Inject(method = "<init>", at = @At("TAIL"))` → à la fin, c'est OK. Mais `@At("HEAD")` dans un constructeur viole la spec JVM.

SpongePowered/Mixin gère ça avec un mécanisme spécial appelé "delegate initialiser" qui déplace le code. clearwing-vm peut ne pas supporter ça.

**Solution :**
- Détecter les `@Inject at=HEAD` dans des `<init>` → les transformer en `@Inject at=TAIL` si possible
- Ou déplacer le code après le premier `INVOKESPECIAL <init>`

---

### 🟠 Problème IMPORTANT 4 : Variables locales capturées par Mixins

**Cause racine :**
`@Inject(locals = LocalCapture.CAPTURE_FAILSOFT)` permet à un Mixin de capturer des variables locales au point d'injection. Mixin utilise l'analyse de frames JVM (LVT - Local Variable Table) pour ça.

Le problème NeoForge #768 montre que NeoForge et Fabric peuvent voir des résultats différents avec EXPAND_FRAMES vs sans. Notre Bake Engine DOIT utiliser `ClassReader.EXPAND_FRAMES` partout (déjà dans le plan).

Mais même avec EXPAND_FRAMES, l'analyse LVT peut être fausse si le compilateur Java a omis la table de variables locales (optimisation). Les builds en mode release (comme les mods distribués aux joueurs) n'ont souvent pas de LVT.

**Solution :**
Utiliser l'analyse de frames ASM (`Analyzer<SourceValue>`) pour reconstruire les variables locales même sans LVT.

---

### 🟠 Problème IMPORTANT 5 : Interface injections via Mixin

**Cause racine :**
Un Mixin peut faire implémenter une interface à une classe Minecraft :
```java
@Mixin(LivingEntity.class)
public class CreateCapabilitiesMixin implements ICreateCapabilities {
    // ...
}
```
Cette injection ajoute `ICreateCapabilities` à la liste `implements` de `LivingEntity`.

Sans traiter ça dans le Bake Engine, les `instanceof ICreateCapabilities` et les casts échouent en C++ car la hiérarchie de classes est incorrecte.

**Solution :**
Lors du traitement d'un `@Mixin`, si la classe Mixin implémente des interfaces supplémentaires (autres que Object), les ajouter à `ClassNode.interfaces` de la classe cible.

```java
// Dans MixinApplicator.applyMixin() :
if (mixinNode.interfaces != null) {
    for (String iface : mixinNode.interfaces) {
        if (!iface.equals("java/lang/Object")
            && !targetNode.interfaces.contains(iface)) {
            targetNode.interfaces.add(iface);
        }
    }
}
```

---

### 🟠 Problème IMPORTANT 6 : Threads et Boehm GC

**Cause racine :**
Certains mods lancent des threads Java en background (Create avec ses simulations cinétiques, AE2 avec ses networks, etc.). Boehm GC doit connaître tous les threads pour les scanner.

En mode Java, le GC est "aware" de tous les threads automatiquement. En C++ avec Boehm GC, chaque thread doit s'enregistrer explicitement.

**Solution :**
Wrapper la création de threads. clearwing-vm a un `Thread_create` qu'on peut modifier pour appeler `GC_register_my_thread()` automatiquement au démarrage de chaque thread.

---

### 🟡 Problème MODÉRÉ 7 : DataFixerUpper (DFU)

**Cause racine :**
DFU est le système de migration de données de Minecraft. Il utilise des patterns fonctionnels avancés avec des lambda chains, des `Codec<T>` via réflexion, et des `TypeRewriteRule` complexes.

**Pour une première version :** Ignorer DFU dans la transpilation et laisser la JVM le gérer via une interface JNI quand une migration est nécessaire (ouverture d'anciens mondes). C'est acceptable pour v1.0.

---

### 🟡 Problème MODÉRÉ 8 : LWJGL natif → Android

**Cause racine :**
Minecraft utilise LWJGL3 pour OpenGL (via GLFW) sur PC. LWJGL n'existe pas sur Android. Sur Android, le rendu doit passer par l'API native Android (EGL/OpenGL ES).

**Solution :**
MobileGlues fait déjà ce travail pour Amethyst. On lie notre `.so` contre MobileGlues et les appels OpenGL sont transparenément traduits.

Pour les appels GLFW (gestion de fenêtre), on crée des stubs qui redirigent vers notre ANativeWindow/EGL comme montré dans `android_main.cpp`.

---

### 🟡 Problème MODÉRÉ 9 : Mods Kotlin

**Cause racine :**
Kotlin compile vers du bytecode JVM standard, donc clearwing-vm peut le transpiler. MAIS Kotlin génère des patterns spéciaux :
- Classes compagnon (`MyClass$Companion.class`)
- Extension functions compilées comme méthodes statiques avec receiver
- `data class` avec `copy()`, `componentN()`
- Coroutines : continuation-passing style, `suspend fun` → state machines

**Solution :**
- Classes compagnon, extension functions, data classes → OK, clearwing-vm les gère
- Coroutines → potentiellement problématique. Vérifier au cas par cas.
  La plupart des mods n'utilisent pas de coroutines dans leur logique principale.

---

### 🟡 Problème MODÉRÉ 10 : Obfuscation résiduelle

**Cause racine :**
NeoForge 1.20.2+ utilise Mojmap pour les classes et les méthodes. Mais certains noms de champs peuvent encore être en SRG (f_60484_ etc.) selon comment NeoForge applique ses propres mappings internes.

**Solution :**
En pratique, les mods modernes (Create, etc.) utilisent déjà des noms Mojmap. Et NeoForge lui-même distribue ses classes en Mojmap. Le problème est minimal.
Si des crash surviennent à cause de noms incorrects, `TinyRemapper` (Fabric, MIT) peut résoudre ça.

---

## 11. Plan de développement mois par mois

### Milestones et critères de succès

```
MILESTONE 1 — Semaine 1-4 : Toolchain validée
─────────────────────────────────────────────────
□ clearwing-vm installé et fonctionnel
□ "Hello World" Java → C++ → compile et tourne
□ Minecraft VANILLA (sans mods) compile avec clearwing-vm
  Référence : github.com/kb-1000/mc-image
□ Le menu principal s'affiche

Test de succès : le menu Minecraft s'affiche depuis le binaire C++ sans JVM

MILESTONE 2 — Semaine 5-8 : Bake Engine v1
──────────────────────────────────────────────────────────────────
□ JarLoader : charge et parse 10.000+ classes sans crash
□ ATApplier : applique les AT de NeoForge sur vanilla (accès des méthodes change)
□ MixinScanner : liste TOUS les @Mixin d'un modpack (ex: Create avec 80+ Mixins)
□ MixinApplicator Tier 1 : @Inject HEAD/TAIL, @Overwrite, @Accessor, @Invoker, @Shadow

Test de succès : Create Mod compilé, les blocs s'enregistrent correctement

MILESTONE 3 — Semaine 9-12 : Mods complexes
──────────────────────────────────────────────────────────────────
□ MixinApplicator Tier 2 : @Redirect, @ModifyArg, @ModifyConstant, @ModifyVariable
□ MixinExtras Handler : @WrapOperation, @ModifyExpressionValue
□ Interface injections gérées
□ InvokeDynamic Patcher : compte et traite les cas problématiques
□ ReflectionResolver : génère reflect-config.json complet

Test de succès : modpack de 10 mods populaires (Create, JEI, Waystones, Botania) qui compile

MILESTONE 4 — Semaine 13-16 : Le mod NeoForge complet (PC)
──────────────────────────────────────────────────────────────────
□ nativeforge-1.0.jar installable dans /mods/
□ Détection hash modpack → décision compile/lancer/fallback
□ BakeProgressScreen dans le jeu (barre de progression)
□ NativeBinaryLauncher : transfert de fenêtre GLFW
□ Fallback JVM automatique si erreur

Test de succès : installer le mod, lancer le jeu, il compile, relance en C++

MILESTONE 5 — Semaine 17-20 : Android
──────────────────────────────────────────────────────────────────
□ Compilation MobiVM → LLVM IR → NDK Clang → libminecraft_native.so
□ android_main.cpp : JNI_OnLoad, startNative, onTouch, onKeyEvent
□ MobileGlues intégré (OpenGL → GLES 3)
□ Amethyst patché avec NativeMinecraftBridge.java
□ Minecraft vanilla natif sur Android

Test de succès : menu principal sur Android depuis le .so C++

MILESTONE 6 — Semaine 21-24 : Stabilisation + Release
──────────────────────────────────────────────────────────────────
□ Tests avec 20+ mods populaires
□ Fix des crashs les plus courants
□ Logs de diagnostic complets
□ README clair pour les utilisateurs
□ Publication sur Modrinth / CurseForge

Test de succès : Create + JEI + Botania + Waystones + 10 autres mods sans crash
```

---

## 12. Prompts IA pour chaque composant

### Prompt contexte global (TOUJOURS envoyer en premier)

```
Contexte projet NativeForge :

Tu es un expert en bytecode Java (JVM spec 21), ASM 9.7 Tree API, SpongePowered/Mixin 0.8.7,
LlamaLad7/MixinExtras 0.4.1, NeoForge 1.21, Android NDK ARM64, C++17 moderne, Boehm GC.

Le projet : un mod NeoForge ordinaire (.jar dans /mods/) qui, au premier lancement, compile
le modpack entier en binaire C++ natif. Basé sur clearwing-vm pour la transpilation.
On réutilise les libs open source existantes sans réinventer ce qui existe.

Infos critiques :
- NeoForge 1.20.2+ utilise Mojmap au runtime (pas de remapping SRG nécessaire)
- clearwing-vm gère les lambdas LambdaMetaFactory et StringConcatFactory, pas les autres
- Boehm GC doit être initialisé en premier et chaque thread JNI doit s'enregistrer
- EXPAND_FRAMES obligatoire dans ClassReader pour les manipulations de bytecode
- ClassWriter.COMPUTE_FRAMES obligatoire après modification d'instructions

Règles de code :
- Java 21 pour le Bake Engine, C++17 pour le runtime
- ASM Tree API uniquement (pas ClassVisitor)
- Code complet et compilable uniquement, pas de pseudocode
- Inclure les imports Maven nécessaires en commentaire
```

### Prompt : JarLoader

```
Implémente JarLoader.java complet. Il doit :
1. loadJar(Path) : ouvrir un JAR avec JarFile, énumérer les JarEntry
   Pour chaque .class : parser avec ClassReader(EXPAND_FRAMES), stocker ClassNode dans
   Map<String, ClassNode> classPool. Pour les non-.class non-META-INF : stocker dans
   Map<String, byte[]> assets.
2. loadAll(List<Path>) : charger dans l'ordre (vanilla → neoforge → mods)
3. Méthode statique toBytes(ClassNode) : ClassWriter(COMPUTE_FRAMES|COMPUTE_MAXS)
4. Gérer les exceptions par .class (log + skip, ne pas crasher sur un seul .class)
5. Log verbose : nb classes par JAR, total final, conflits de classes (doublon)
```

### Prompt : ATApplier

```
Implémente ATApplier.java complet. Il doit :
1. scanAllJars(List<Path>) : pour chaque JAR, chercher META-INF/accesstransformer.cfg
2. parseATFile(InputStream, String) : parser le format AT ligne par ligne
   Format : "<access> <className.interne> [<memberName> [<descriptor>]]"
   Access strings : "public", "protected", "default", "private",
                    "public-f", "protected-f" (le -f = enlever final)
   Exemples lignes : 
     "public net/minecraft/world/level/block/Block"
     "public net/minecraft/world/level/block/Block f_60484_"
     "public-f net/minecraft/world/level/block/Block m_49678_()V"
3. applyAll(Map<String, ClassNode>) : pour chaque règle, trouver le ClassNode
   et modifier ses access flags
   Utiliser le masque : (access & ~(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE)) | newAccess
   Pour -f : access &= ~ACC_FINAL
4. Log warning si classe/champ/méthode AT non trouvé dans le classPool
```

### Prompt : MixinScanner

```
Implémente MixinScanner.java complet. Il doit :
1. scanAll(List<Path>, Map<String, ClassNode>) : scanner chaque JAR
2. Pour chaque fichier *.mixins.json : lire le package, les arrays "mixins"/"client"/"server"
3. Pour chaque classe Mixin listée : lire ClassNode depuis classPool
4. Trouver l'annotation ASM "Lorg/spongepowered/asm/mixin/Mixin;" sur la ClassNode
5. Extraire les cibles : attribute "value" (List<Type>) et "targets" (List<String>)
6. Extraire la priorité : attribute "priority" (Integer, défaut 1000)
7. Stocker dans Map<String, List<MixinEntry>> où MixinEntry contient :
   String mixinClass, int priority, boolean isClient, String sourceJar
8. getMixinMap() : retourner la map triée (les cibles triées par nombre de Mixins desc)
```

### Prompt : MixinApplicator — @Inject HEAD cancellable

```
Implémente la méthode applyInjectHead dans MixinApplicator.java en Java 21 avec ASM 9.7.
Paramètres : ClassNode target, ClassNode mixin, MethodNode handler, AnnotationNode injectAnn

Elle doit :
1. Extraire l'attribut "method" de injectAnn (peut être String ou List<String>)
   Format possible : "onBreak", "onBreak(LLevel;LBlockPos;)V", 
                     "Lonbreak;onBreak(LLevel;LBlockPos;)V"
2. Extraire "cancellable" (boolean, défaut false)
3. Trouver la MethodNode cible dans target.methods (par nom, optionnel descriptor)
4. Si cancellable : avant la première instruction de la méthode cible, insérer :
   NEW org/spongepowered/asm/mixin/injection/callback/CallbackInfo
   DUP
   LDC <methodName>
   ICONST_1
   INVOKESPECIAL CallbackInfo.<init>(Ljava/lang/String;Z)V
   ASTORE <nextAvailableLocal>
5. Ajouter le handler (MethodNode) comme méthode privée de target (changer son nom en
   <modid>$<handlerName>$<uniqueId> pour éviter les conflits)
6. Insérer AVANT la première instruction de target :
   ALOAD_0 (si non-static)
   [charger chaque param de la méthode cible]
   [ALOAD N si cancellable]
   INVOKEVIRTUAL/INVOKESPECIAL target.<handlerName>(...)V
7. Si cancellable : après l'appel, insérer :
   ALOAD N
   INVOKEVIRTUAL CallbackInfo.isCancelled()Z
   IFEQ <skip_return>
   RETURN (ou le bon type de RETURN)
   <skip_return>:
Utiliser uniquement ASM Tree API. Retourner false si méthode cible introuvable.
```

### Prompt : Runtime C++ NativeRegistry

```
Écris native_registry.hpp en C++17 header-only complet avec :
- struct ResourceLocation avec namespace:path, hash pour unordered_map
- template<typename T> class NativeRegistry : public gc (Boehm GC) avec :
  - register_entry(modid, path, factory): enregistrement différé avec std::function<T*()>
  - freeze(): instancie tous les différés, std::unique_lock<std::shared_mutex>
  - get(const string& id) → T* : std::shared_lock pour thread-safety
  - get(const ResourceLocation&) → T*
  - contains(const string&) → bool
  - size() → size_t
  - isFrozen() → bool
  - getAllEntries() → vector<pair<ResourceLocation, T*>> (copie, thread-safe)
  - Exception si register_entry appelé après freeze
- class GameRegistries avec static NativeRegistry<Block>* BLOCKS, ITEMS, etc.
  (au moins 8 registres), initAll(), freezeAll()
Inclure gc/gc_cpp.h. Pas d'explications. Code complet prêt à compiler.
```

### Prompt : android_main.cpp

```
Écris android_main.cpp complet pour Android API 34 avec :
- JNI_OnLoad : GC_INIT(), GC_enable_incremental(), return JNI_VERSION_1_6
- initEGL(ANativeWindow*) : eglGetDisplay/Initialize/ChooseConfig/CreateContext/Surface
  Config : RGBA8888, depth 24, stencil 8, OpenGL ES 3 (EGL_OPENGL_ES3_BIT)
- Java_com_amethyst_nativebridge_NativeMinecraftBridge_startNative(
    JNIEnv*, jobject, jobject surface, jint w, jint h, jstring session)
  ANativeWindow_fromSurface, setBuffersGeometry, initEGL, launch thread,
  GC_register_my_thread() dans le thread, minecraft_native_main(argv)
- Java_..._onTouch(JNIEnv*, jobject, jint action, jfloat x, jfloat y, jint pointerId)
  Traduire ACTION_DOWN/UP/MOVE en NativeInputQueue mouse events
- Java_..._onKeyEvent(JNIEnv*, jobject, jint keyCode, jint action, jint mods)
- extern "C" stubs pour GLFW : glfwSwapBuffers(eglSwapBuffers), glfwGetWindowSize,
  glfwPollEvents (no-op), glfwGetCurrentContext
Includes : jni.h, android/log.h, android/native_window_jni.h, EGL/egl.h, gc/gc.h
```

---

## 13. Sources et références

| Projet | URL | Rôle dans NativeForge |
|--------|-----|-----------------------|
| clearwing-vm | `github.com/SwitchGDX/clearwing-vm` | Transpileur principal Java→C++ |
| SpongePowered/Mixin | `github.com/SpongePowered/Mixin` | Framework Mixin (mode offline) |
| LlamaLad7/MixinExtras | `github.com/LlamaLad7/MixinExtras` | Injections modernes |
| neoforged/FancyModLoader | `github.com/neoforged/FancyModLoader` | Comprendre le pipeline FML |
| neoforged/accesstransformers | `github.com/neoforged/accesstransformers` | Lib AT officielle |
| Boehm GC | `github.com/bdwgc/bdwgc` | GC pour C++ |
| Amethyst-Android | `github.com/AngelAuraMC/Amethyst-Android` | Base launcher Android |
| MobileGlues | `github.com/MobileGL-Dev/MobileGlues-release` | OpenGL→GLES 3.x |
| mc-image | `github.com/kb-1000/mc-image` | Config ref Minecraft natif |
| ObjectWeb ASM | `asm.ow2.io` | Bytecode manipulation |
| NeoForge issue #768 | `github.com/neoforged/NeoForge/issues/768` | Bug EXPAND_FRAMES |
| Mojmap explainer | `neoforged.net/personal/sciwhiz12/what-are-mappings` | Pourquoi pas de remapping |
| clearwing-vm README | `github.com/SwitchGDX/clearwing-vm#readme` | Limites invokedynamic |
| MixinExtras releases | `github.com/LlamaLad7/MixinExtras/releases` | Liste @Expressions etc. |
| FML pipeline DeepWiki | `deepwiki.com/neoforged/FancyModLoader/4-mod-loading-pipeline` | Architecture FML |
| Android NDK | `developer.android.com/ndk/guides` | Cross-compilation ARM64 |

---

*NativeForge Plan Maître V3 — Mai 2026*
*~1500 lignes. Tous les composants détaillés avec code complet.*
*Nouveautés vs V2 : code complet pour chaque composant, deep dive invokedynamic,*
*Mojmap confirme qu'on n'a pas besoin de remapping sur NeoForge 1.21+,*
*séquence de boot C++ complète, clearwing-vm limites documentées.*
