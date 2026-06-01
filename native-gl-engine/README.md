# 🚀 NativeGLEngine

**Moteur de rendu natif Android pour Minecraft Java Edition**

[![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange)](https://neoforged.net/)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-red)](https://adoptium.net/)
[![NDK r26+](https://img.shields.io/badge/NDK-r26d-yellow)](https://developer.android.com/ndk)

---

## 📖 Table des matières

- [Qu'est-ce que NativeGLEngine ?](#quest-ce-que-nativeglengine-)
- [Architecture](#-architecture)
- [Modules](#-modules)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Compatibilité](#-compatibilité)
- [Compilation depuis les sources](#-compilation-depuis-les-sources)
- [Connexion avec Android Optimizer](#-connexion-avec-android-optimizer)
- [Roadmap](#-roadmap)
- [Licence](#-licence)

---

## Qu'est-ce que NativeGLEngine ?

NativeGLEngine est un mod **NeoForge 1.21.1** séparé et indépendant qui agit comme moteur d'optimisation graphique natif pour **Minecraft Java Edition sur Android**. Il utilise du code C++ compilé via le **NDK Android** pour :

- **Compilation de shaders asynchrone** (GLSL → SPIR-V → ESSL)
- **Cache persistant SHA-256** pour éliminer les recompilations
- **Gestion mémoire GPU réelle** via Vulkan Memory Allocator (VMA)
- **Interception des appels OpenGL** pour déduplication d'état et throttling
- **Optimisations SPIR-V par SoC** (Adreno, Mali, Dimensity, Tensor)

Le mod se connecte **optionnellement** à [Android Optimizer](https://github.com/Eaielectronic/android-optimizer). S'il est absent, NativeGLEngine fonctionne en **mode autonome**.

### Le problème résolu

| Problème | Cause | Impact |
|---|---|---|
| Freezes 200ms–2s | Compilation shader synchrone | Stutter visible |
| Recompilation à chaque démarrage | Pas de cache par appareil | Chargement long |
| Crashes mémoire GPU | Pas de budget GPU réel | OOM, LMK kill |
| Appels GL redondants | Pas de filtrage d'état | -2 à 5ms/frame |

---

## 🏗 Architecture

```
NativeGLEngine (mod NeoForge indépendant)
      │
      ├── Java (NeoForge Mod)
      │   ├── NativeGLEngineMod.java        ← @Mod entry point
      │   ├── NativeLib.java                ← Chargement .so
      │   ├── ShaderCacheManager.java       ← Cache L1/L2, SHA-256
      │   ├── ShaderCompilerBridge.java     ← JNI : GLSL → SPIR-V → ESSL
      │   ├── ShaderPlaceholderManager.java ← Placeholder async
      │   ├── NativeMemoryBridge.java       ← JNI : VMA
      │   ├── GLInterceptorBridge.java      ← JNI : PLT hooks
      │   ├── RendererDetector.java         ← MobileGlues/GL4ES/Zink/ANGLE
      │   ├── AndroidOptBridge.java         ← Connexion Android Optimizer
      │   ├── NativeGLConfig.java           ← ModConfigSpec
      │   ├── NativeGLTickHandler.java      ← Tick handler client
      │   ├── MemoryReport.java             ← POJO mémoire GPU
      │   └── mixin/ShaderProgramMixin.java ← Intercept shader compile
      │
      └── C++ (NDK arm64-v8a) — libNativeGLEngine.so
          ├── jni_bridge.cpp                ← Points d'entrée JNI
          ├── shader_compiler.cpp/.h        ← Shaderc + SPIRV-Cross
          ├── shader_cache.cpp/.h           ← Cache disque persistant
          ├── soc_optimizer.cpp/.h          ← Optimisations par SoC
          ├── gl_interceptor.cpp/.h         ← PLT hooking
          ├── native_memory.cpp/.h          ← VMA + thermal
          ├── texture_manager.cpp/.h        ← Queue uploads différés
          └── CMakeLists.txt                ← Build NDK + FetchContent
```

---

## 📦 Modules

### ShaderCompiler — GLSL → SPIR-V → ESSL

Pipeline de compilation asynchrone avec cache SHA-256 :
1. Hash du GLSL + SoC + driver → cache → **0ms** si déjà compilé
2. Sinon → compilation thread séparé → placeholder shader → zéro freeze
3. Résultat en cache → rechargements futurs instantanés

### NativeMemoryManager — VMA + VK_EXT_memory_budget

Budget GPU réel, `/proc/meminfo`, `/sys/class/thermal/`.

### InterceptLayer — GL Hooks via PLT

Hooks sur `glShaderSource`, `glTexImage2D`, `glEnable/glDisable`, `glDraw*`.

### SoC Optimizer

Passes SPIR-V ciblées : Adreno (VectorDCE), Mali (BlockMerge), Dimensity (LoopUnroll), Tensor (AggressiveDCE).

### RendererDetector

Détection automatique : MobileGlues, GL4ES, Zink, ANGLE, Desktop.

---

## 📥 Installation

1. Télécharger `nativeglengine-0.1.1.jar` depuis les [Releases](../../releases)
2. Placer dans `mods/`
3. *(Optionnel)* Installer [Android Optimizer](https://github.com/Eaielectronic/android-optimizer)
4. Lancer Minecraft avec **NeoForge 1.21.1**

| Composant | Version | Requis ? |
|---|---|---|
| Minecraft | 1.21.1 | ✅ |
| NeoForge | 21.1.x | ✅ |
| Java | 21 | ✅ |
| Android Optimizer | ≥1.0.5 | ❌ Optionnel |

---

## ⚙ Configuration

Fichier : `config/nativeglengine-client.toml`

| Option | Défaut | Description |
|---|---|---|
| `shader.shaderCacheEnabled` | `true` | Cache SPIR-V/ESSL persistant |
| `shader.asyncCompilation` | `true` | Compilation hors render thread |
| `shader.compilerThreads` | `2` | Threads de compilation |
| `hooks.glHooksEnabled` | `true` | Hooks PLT sur LWJGL |
| `hooks.stateDedup` | `true` | Filtrage appels GL redondants |
| `hooks.textureThrottling` | `true` | Queue uploads si budget GPU tendu |
| `memory.gpuMemoryMonitor` | `true` | Surveillance VMA |
| `memory.gpuPressureSoft` | `80` | Seuil cleanup doux (%) |
| `memory.gpuPressureHard` | `92` | Seuil cleanup agressif (%) |
| `shader.shaderCompiler` | `true` | Active la compilation asynchrone des shaders |
| `hooks.glInterceptor` | `true` | Active les hooks GL bas niveau |
| `memory.offHeapEnabled` | `true` | Active l'Off-Heap Arena pour NativeImage |
| `memory.offHeapSizeMB` | `150` | Taille de l'arène C++ en Mo |
| `memory.asyncBuffers` | `true` | Active l'envoi asynchrone des buffers |
| `debug.verboseLog` | `false` | Logs détaillés |
| `debug.showHud` | `true` | HUD stats |

---

## 📱 Compatibilité

### Lanceurs Android

| Lanceur | Statut |
|---|---|
| ZalithLauncher 2 | ✅ Recommandé |
| FCL | ✅ Compatible |
| Amethyst | ✅ Compatible |
| PojavLauncher | ⚠️ Archivé |

### SoC supportés

| GPU | Vulkan | Support |
|---|---|---|
| Adreno 730+ | 1.1–1.3 | ✅ Complet |
| Mali G77+ (Valhall) | 1.1–1.2 | ✅ Complet |
| Immortalis G715 | 1.3 | ✅ + Ray Tracing |
| Google Tensor G3/G4 | 1.3 | ✅ Complet |
| Mali G52/G57 | 1.1 | ⚠️ Partiel |
| Mali G31 | 1.0 | ❌ Trop ancien |

---

## 🔨 Compilation depuis les sources

### Prérequis

- Java 21, Android NDK r26+, CMake 3.22+, Gradle (wrapper inclus)

### Compiler la .so native

```bash
export ANDROID_NDK_HOME=/chemin/vers/android-ndk-r26d
cd src/main/cpp
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
      -DCMAKE_BUILD_TYPE=Release -S . -B build
cmake --build build -j$(nproc)
```

Ou : `./build-native.sh`

### Builder le JAR

```bash
./gradlew build
# → build/libs/nativeglengine-0.1.1.jar
```

---

## 🔗 Connexion avec Android Optimizer

Détection via `ModList.get().isLoaded("androidopt")`. Récupère par réflexion :
- Profil SoC, seuils thermiques, cœurs big.LITTLE, FrameBudgetManager.

Si absent → mode autonome avec valeurs par défaut.

---

## 🗓 Roadmap

- [x] Phase 0 — Structure projet NeoForge
- [x] Phase 1 — ShaderCompiler + Cache SHA-256
- [x] Phase 2 — NativeMemoryManager (VMA)
- [x] Phase 3 — InterceptLayer (GL hooks)
- [x] Phase 4 — RendererDetector + Config
- [x] Phase 7 — Module 7: Off-Heap Arena (Zero-GC)
- [ ] Phase 5 — Implémentation complète Shaderc/SPIRV-Cross
- [ ] Phase 6 — Mode bypass Vulkan complet
- [ ] Phase 8 — Compatibilité Minecraft Vulkan (2026/2027)

---

## 📊 Métriques d'impact

| Métrique | Avant | Après Phase 1-4 | Après Phase 5+ |
|---|---|---|---|
| Stutter shader | 200ms–2s | <50ms | <10ms |
| Rechargement | 50–200ms | 0ms (cache) | 0ms |
| Précision mémoire | ±15% | ±5% | ±2% |
| Freezes texture | 5–15ms/frame | <2ms/frame | <1ms/frame |

---

## 📜 Licence

MIT — [Eaielectronic](https://github.com/Eaielectronic)
