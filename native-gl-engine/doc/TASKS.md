# NativeGLEngine — Liste des Tâches

## Vue d'ensemble
NativeGLEngine est un mod NeoForge 1.21.1 **indépendant** qui agit comme moteur de rendu natif pour Android.
Il se connecte au mod principal `Android Optimizer` s'il est présent (détection via `ModList`),
et fonctionne aussi de manière autonome.

---

## Phase 0 — Setup du Projet (Structure NeoForge)

### Tâche 0.1 — Structure Gradle du projet
- [x] `settings.gradle` — plugin NeoForge + nom du projet
- [x] `build.gradle` — ModDevGradle, Java 21, neoForge block, mixin support
- [x] `gradle.properties` — mod_id=nativeglengine, versions MC/NeoForge
- [x] Wrapper Gradle (copié depuis android-optimizer)

### Tâche 0.2 — Fichiers de base du mod
- [x] `neoforge.mods.toml` — descriptor avec dépendance optionnelle vers `androidopt`
- [x] `nativeglengine.mixins.json` — configuration Mixin (vide pour l'instant)
- [x] `NativeGLEngineMod.java` — classe principale `@Mod("nativeglengine")`
- [x] `NativeLib.java` — chargement de la `.so` native (extraction JAR → fichier temp → System.load)

### Tâche 0.3 — Bridge vers Android Optimizer
- [x] `AndroidOptBridge.java` — détecte `androidopt` via ModList, accès par réflexion aux configs
- [x] Support autonome : si `androidopt` absent, le mod utilise ses propres valeurs par défaut

### Tâche 0.4 — Fichiers C++ (stubs prêts à compiler)
- [x] `CMakeLists.txt` — configuration build NDK avec FetchContent (Shaderc, SPIRV-Cross, VMA)
- [x] `jni_bridge.cpp` — points d'entrée JNI (stubs)
- [x] Headers pour chaque module C++

### Tâche 0.5 — Fichiers de langue
- [x] `en_us.json` et `fr_fr.json`

---

## Phase 1 — ShaderCompiler (Compilation GLSL asynchrone)

### Tâche 1.1 — ShaderCacheManager.java
- [x] Hash SHA-256 : source GLSL + SoC + driver version
- [x] Cache L1 mémoire (ConcurrentHashMap)
- [x] Cache L2 disque (fichiers .spv / .essl dans gameDirectory)
- [x] Invalidation automatique (version driver, version MC, version mod)

### Tâche 1.2 — ShaderCompilerBridge.java
- [x] Méthodes JNI natives : `compileGLSLtoSPIRV()`, `convertSPIRVtoESSL()`
- [x] Fallback Java si la .so n'est pas chargée (retourne le GLSL tel quel)
- [x] Pool de threads dédié pour la compilation async

### Tâche 1.3 — ShaderPlaceholderManager.java
- [x] Génère des shaders placeholder (magenta) pendant la compilation async
- [x] Système de notification quand la compilation est terminée
- [x] Remplacement transparent du placeholder par le shader compilé

### Tâche 1.4 — shader_compiler.cpp (C++ NDK)
- [x] Pipeline GLSL → SPIR-V via Shaderc (stub avec TODO)
- [x] Pipeline SPIR-V → ESSL via SPIRV-Cross (stub avec TODO)
- [x] Optimisation SPIR-V par SoC (Qualcomm, ARM Mali, MediaTek, Tensor)

### Tâche 1.5 — shader_cache.cpp (C++ NDK)
- [x] Cache SHA-256 persistant sur disque
- [x] Lecture/écriture thread-safe

### Tâche 1.6 — Mixin ShaderCompilerMixin.java
- [x] Intercepte `Program.compileShaderInternal` (Mojang Mappings 1.21.1)
- [x] Vérifie cache → retourne immédiatement si trouvé
- [x] Sinon compilation async + placeholder

---

## Phase 2 — NativeMemoryManager (VMA + budget GPU)

### Tâche 2.1 — NativeMemoryBridge.java
- [x] JNI bridge vers VMA
- [x] `getGPUBudget()`, `getGPUUsage()`, `getGPUPressure()`
- [x] Fallback Java (estimation via Runtime si .so absent)

### Tâche 2.2 — MemoryReport.java
- [x] POJO : gpuBudgetBytes, gpuUsageBytes, gpuPressure, sysAvailableMB, tempCelsius

### Tâche 2.3 — native_memory.cpp (C++ NDK)
- [x] Init VkInstance + VkDevice minimal (juste pour VMA)
- [x] VMA allocator avec VK_EXT_memory_budget
- [x] Thread de surveillance (500ms polling)

---

## Phase 3 — InterceptLayer (Hooks GL)

### Tâche 3.1 — GLInterceptorBridge.java
- [x] JNI bridge pour installer/désinstaller les hooks
- [x] Stats : nombre d'appels GL hookés, state dedup count

### Tâche 3.2 — gl_interceptor.cpp (C++ NDK)
- [x] PLT hooking sur LWJGL/libGL
- [x] Hooks : glShaderSource, glCompileShader, glTexImage2D, glEnable/glDisable
- [x] GL state deduplication (filtre les appels redondants)

### Tâche 3.3 — TextureUploadQueue (C++ NDK)
- [x] texture_manager.cpp — queue MPSC pour uploads différés
- [x] Drain progressif entre frames (max 2ms par frame)

---

## Phase 4 — RendererDetector + Config Screen

### Tâche 4.1 — RendererDetector.java
- [x] Détection : MobileGlues, GL4ES, Zink, ANGLE, LWJGL Desktop
- [x] Cache du résultat (détecté une seule fois)

### Tâche 4.2 — NativeGLConfig.java
- [x] Config NeoForge avec ModConfigSpec
- [x] Toggles : shader cache, GL hooks, memory monitor, renderer preset

### Tâche 4.3 — NativeGLConfigScreen.java
- [x] Écran de configuration in-game
- [x] Pages : Shaders, Mémoire GPU, Hooks GL, Renderer, Diagnostic

---

## Phases Futures (Non implémentées — stubs uniquement)

### Phase 5 — Mode Bypass Vulkan
- [ ] VulkanCapabilityChecker.java
- [ ] VulkanRenderBackend (C++)
- [ ] SocVulkanConfig.java (Adreno TBDR, Mali FP16, Tensor high-perf)
- [ ] Stubs GL → Vulkan (200+ fonctions)

> **Note** : La Phase 5 est documentée mais pas implémentée.
> Elle représente 6-12 mois de dev et sera utile quand Mojang migrera vers Vulkan.
