# Changelog

All notable changes to NativeGLEngine will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned
- Implémentation complète Shaderc (GLSL → SPIR-V) dans shader_compiler.cpp
- Implémentation complète SPIRV-Cross (SPIR-V → ESSL) dans shader_compiler.cpp
- Implémentation spirv-opt pour les passes SoC dans soc_optimizer.cpp
- Implémentation VMA + VK_EXT_memory_budget dans native_memory.cpp
- Implémentation PLT hooking réel dans gl_interceptor.cpp
- Implémentation texture queue MPSC dans texture_manager.cpp
- Mode bypass Vulkan complet (Phase 6)

---

## [0.1.1] - 2026-06-01

### Added
- **Compilation C++ (NDK)** : Compilation réussie de la librairie ARM64 (`libNativeGLEngine.so`) via CMake et `build-native.sh`.
- **System Optimizations** : Intégration de `thermal_monitor.cpp`, `perf_hint.cpp`, et `memory_purge.cpp` pour gérer la température, l'affinité CPU (threads) et le nettoyage mémoire (mallopt M_PURGE) directement depuis le C++.
- **LZ4 Bridge** : Intégration de LZ4 et `lz4_bridge.cpp` pour la décompression et compression asynchrone ultrarapide en C++.
- **Particle Pool** : Intégration du gestionnaire de particules natives `particle_pool.cpp` au build NDK.
- **Jar Packaging** : La librairie native `libNativeGLEngine.so` est maintenant correctement injectée et packagée au sein du mod NeoForge `nativeglengine-0.1.1.jar`.

---

## [0.1.0] - 2026-05-27

### Added — Structure complète du projet

#### Java (NeoForge Mod)
- **NativeGLEngineMod.java** — Point d'entrée `@Mod("nativeglengine")` avec `IEventBus` et `ModContainer`. Gestion FMLCommonSetupEvent et FMLClientSetupEvent.
- **NativeLib.java** — Chargement sécurisé de `libNativeGLEngine.so` depuis le JAR. Extraction vers fichier temporaire, détection ARM64, fallback mode Java seul.
- **ShaderCompilerBridge.java** — Bridge JNI vers `shader_compiler.cpp`. Méthodes natives : `nativeCompileGLSLtoSPIRV()`, `nativeConvertSPIRVtoESSL()`, `nativeGetDriverVersion()`.
- **ShaderCacheManager.java** — Cache shader à deux niveaux : L1 mémoire (HashMap) + L2 disque (fichiers .spv/.essl). Hash SHA-256 incluant source + SoC + driver.
- **ShaderPlaceholderManager.java** — Gestion des shaders placeholder (magenta) pendant la compilation asynchrone. Replacement automatique une fois la compilation terminée.
- **NativeMemoryBridge.java** — Bridge JNI vers `native_memory.cpp`. Méthodes : `nativeGetGPUBudget()`, `nativeGetGPUUsage()`, `nativeGetGPUPressure()`, `nativeGetTemperature()`, `nativeIsThermalThrottling()`.
- **GLInterceptorBridge.java** — Bridge JNI vers `gl_interceptor.cpp`. Méthodes : `nativeInstallHooks()`, `nativeGetTotalGLCalls()`, `nativeGetDedupedCalls()`, `nativeGetDeferredTextures()`, `nativeDrainTextureQueue()`.
- **RendererDetector.java** — Détection automatique du renderer actif via `GL_VENDOR`/`GL_RENDERER` : MobileGlues, GL4ES, Zink, ANGLE, Desktop.
- **AndroidOptBridge.java** — Connexion au mod Android Optimizer via réflexion Java (pas de dépendance dure). Récupère profil SoC, seuils thermiques, cœurs big.LITTLE.
- **NativeGLConfig.java** — Configuration NeoForge via `ModConfigSpec.Builder`. Sections : shader, hooks, memory, debug.
- **NativeGLTickHandler.java** — Handler de tick client pour la surveillance périodique mémoire et température.
- **MemoryReport.java** — POJO pour les rapports mémoire GPU (budget, usage, pression, température, throttle).
- **mixin/ShaderProgramMixin.java** — Mixin ciblant `Program.compileShaderInternal` (Mojang Mappings 1.21.1) pour intercepter la compilation shader.

#### C++ (NDK arm64-v8a)
- **jni_bridge.cpp** — 15 fonctions JNI couvrant ShaderCompilerBridge, NativeMemoryBridge, et GLInterceptorBridge.
- **shader_compiler.cpp/.h** — Pipeline GLSL → SPIR-V (Shaderc) → ESSL (SPIRV-Cross). Stubs en attente de FetchContent.
- **shader_cache.cpp/.h** — Cache disque persistant : save/load/exists avec chemin basé sur hash SHA-256.
- **soc_optimizer.cpp/.h** — Optimisations SPIR-V par SoC : Qualcomm (VectorDCE), ARM (BlockMerge), MediaTek (LoopUnroll), Google Tensor (AggressiveDCE). Stubs.
- **gl_interceptor.cpp/.h** — Hooks PLT via dlopen/dlsym avec recherche multi-noms LWJGL. Stats atomiques thread-safe.
- **native_memory.cpp/.h** — Lecture `/proc/meminfo` (MemAvailable), lecture `/sys/class/thermal/thermal_zone*/temp`, détection throttle thermique (>50°C). Stubs VMA.
- **texture_manager.cpp/.h** — Queue MPSC pour uploads texture différés avec budget temps 2ms/frame. Stub.
- **CMakeLists.txt** — Build NDK complet avec FetchContent pour Shaderc, SPIRV-Cross, VMA. Flags `-O3 -ffast-math -fvisibility=hidden`.

#### Build et configuration
- **build.gradle** — Plugin `net.neoforged.moddev` v2.0.141, Java 21 toolchain, Parchment mappings.
- **settings.gradle** — Plugin management NeoForge + foojay-resolver.
- **gradle.properties** — mod_id=nativeglengine, mod_version=0.1.0, neo_version=21.1.227.
- **neoforge.mods.toml** — Descripteur mod avec dépendance optionnelle vers androidopt ≥1.0.5.
- **nativeglengine.mixins.json** — Configuration Mixin avec ShaderProgramMixin (client-only).
- **libNativeGLEngine.so** — Bibliothèque native ARM64 pré-compilée (252KB, stripped).

#### Documentation
- **README.md** — Documentation complète avec architecture, modules, installation, configuration, compatibilité, compilation, roadmap.
- **CHANGELOG.md** — Ce fichier.
- **CONTRIBUTING.md** — Guide de contribution détaillé.
- **SECURITY.md** — Politique de sécurité.
- **LICENSE** — Licence MIT.
- **build-native.sh** — Script de compilation NDK automatisé.

#### Localisation
- **en_us.json** — Traductions anglaises.
- **fr_fr.json** — Traductions françaises.
