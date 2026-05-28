# Architecture — NativeGLEngine

## Vue d'ensemble

NativeGLEngine est un mod NeoForge 1.21.1 indépendant composé de deux couches :

1. **Couche Java** — Mod NeoForge standard avec Mixins, config, event handlers
2. **Couche C++ (NDK)** — Bibliothèque native `libNativeGLEngine.so` pour ARM64

La communication entre les deux couches se fait via **JNI** (Java Native Interface).

## Diagramme d'architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Minecraft 1.21.1                          │
│                    (LWJGL 3 + Blaze3D)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │ OpenGL calls
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              NativeGLEngine (mod NeoForge)                    │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Java Layer (NeoForge Mod)                             │    │
│  │                                                       │    │
│  │  @Mod ──→ FMLCommonSetup ──→ SoC Detection            │    │
│  │       ──→ FMLClientSetup ──→ RendererDetector          │    │
│  │                           ──→ ShaderCacheManager       │    │
│  │                           ──→ NativeMemoryBridge       │    │
│  │                           ──→ GLInterceptorBridge      │    │
│  │                                                       │    │
│  │  Mixin: ShaderProgramMixin                             │    │
│  │    └→ Intercept Program.compileShaderInternal()        │    │
│  │    └→ Check cache → async compile → placeholder       │    │
│  └──────────────────────────┬───────────────────────────┘    │
│                             │ JNI calls                       │
│  ┌──────────────────────────▼───────────────────────────┐    │
│  │ C++ Layer (libNativeGLEngine.so)                      │    │
│  │                                                       │    │
│  │  jni_bridge.cpp ──→ shader_compiler.cpp (Shaderc)     │    │
│  │                 ──→ shader_cache.cpp (disque)          │    │
│  │                 ──→ soc_optimizer.cpp (spirv-opt)      │    │
│  │                 ──→ native_memory.cpp (VMA)            │    │
│  │                 ──→ gl_interceptor.cpp (PLT hooks)     │    │
│  │                 ──→ texture_manager.cpp (queue)        │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│           MobileGlues (OpenGL → GLES traduction)             │
│                          ↓                                    │
│           GPU Android (Adreno / Mali / Tensor)                │
└─────────────────────────────────────────────────────────────┘
```

## Strategy Pattern — Backend de rendu

Le pattern Strategy permet de changer de backend sans modifier le code appelant :

```
RendererBackend (interface)
    ├── VulkanPath   (Vulkan 1.2+, cible principale future)
    ├── GLESPath     (GLES 3.1+, fallback via MobileGlues)
    └── ANGLEPath    (Google ANGLE, option)
```

Le backend est sélectionné une fois au démarrage par `RendererDetector.detect()`.

## Flux de compilation shader

```
1. Minecraft appelle glShaderSource() / glCompileShader()
   │
2. ShaderProgramMixin.interceptShaderCompile() capture l'appel
   │
3. ShaderCacheManager.computeHash(glsl, soc, driver) → SHA-256
   │
4. Cache L1 (mémoire) HIT ? → Retourner shader ID (0ms)
   │                     MISS ↓
5. Cache L2 (disque) HIT ? → Charger, mettre en L1, retourner (5ms)
   │                    MISS ↓
6. ShaderPlaceholderManager.getOrCreate(type) → placeholder shader
   │
7. Thread async : ShaderCompilerBridge.compileGLSLtoSPIRV()
   │  └→ Shaderc (GLSL → SPIR-V)
   │  └→ soc_optimizer (passes par SoC)
   │  └→ SPIRV-Cross (SPIR-V → ESSL si backend GLES)
   │
8. Callback : sauvegarder en cache L1 + L2
   │
9. ShaderPlaceholderManager.notifyCompilationDone() → swap shader
```

## Cycle de vie NeoForge 1.21.1

```
1. Constructeur @Mod (NativeGLEngineMod)
   └→ Register config, setup listeners, NativeLib.tryLoad()

2. FMLCommonSetupEvent (client + serveur, parallèle)
   └→ enqueueWork: AndroidOptBridge.init(), SoC detection

3. FMLClientSetupEvent (client seulement, parallèle)
   └→ enqueueWork: RendererDetector, ShaderCacheManager.init(),
      NativeMemoryBridge.init(), GLInterceptorBridge.install()

4. TickEvent.ClientTickEvent (runtime, chaque tick)
   └→ NativeGLTickHandler: monitoring mémoire, température, stats
```

## Connexion AndroidOptBridge (réflexion)

```java
// Pas de dépendance dure → réflexion
ModList.get().isLoaded("androidopt") // check présence
Class.forName("fr.eaielectronic.androidopt.SocDetector") // accès
Method m = clazz.getMethod("getProfile"); // appel
```

Avantage : NativeGLEngine compile et fonctionne sans Android Optimizer.
