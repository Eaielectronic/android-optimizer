# API Native JNI — NativeGLEngine

Toutes les fonctions JNI exposées par `libNativeGLEngine.so` via `jni_bridge.cpp`.

## ShaderCompilerBridge

Classe Java : `fr.eaielectronic.nativeglengine.ShaderCompilerBridge`

### `nativeCompileGLSLtoSPIRV`
```java
static native byte[] nativeCompileGLSLtoSPIRV(String glslSource, int shaderType, int socVendor);
```
- **glslSource** — Code source GLSL (version 4.50 ou ESSL 3.20)
- **shaderType** — 0=VERTEX, 1=FRAGMENT, 2=GEOMETRY, 3=COMPUTE
- **socVendor** — 0=UNKNOWN, 1=QUALCOMM, 2=ARM, 3=MEDIATEK, 4=SAMSUNG, 5=GOOGLE_TENSOR, 6=HUAWEI
- **Retour** — SPIR-V binaire en `byte[]`, ou `null` si échec
- **Statut** — STUB (retourne `null`)

### `nativeConvertSPIRVtoESSL`
```java
static native String nativeConvertSPIRVtoESSL(byte[] spirvBytes);
```
- **spirvBytes** — SPIR-V binaire
- **Retour** — Code source ESSL 3.20, ou `null` si échec
- **Statut** — STUB (retourne `null`)

### `nativeGetDriverVersion`
```java
static native String nativeGetDriverVersion();
```
- **Retour** — Version du driver GPU (via EGL), ou `"unknown"`
- **Statut** — STUB (retourne `"unknown"`)

---

## NativeMemoryBridge

Classe Java : `fr.eaielectronic.nativeglengine.NativeMemoryBridge`

### `nativeInit`
```java
static native boolean nativeInit();
```
- **Retour** — `true` si VMA initialisé avec succès
- **Statut** — STUB (retourne `true`, pas de vrai VMA)

### `nativeGetGPUBudget`
```java
static native long nativeGetGPUBudget();
```
- **Retour** — Budget GPU total en bytes (via VK_EXT_memory_budget)
- **Statut** — STUB (retourne 0)

### `nativeGetGPUUsage`
```java
static native long nativeGetGPUUsage();
```
- **Retour** — Usage GPU actuel en bytes
- **Statut** — STUB (retourne 0)

### `nativeGetGPUPressure`
```java
static native float nativeGetGPUPressure();
```
- **Retour** — Pression GPU : 0.0 = libre, 1.0 = max budget
- **Statut** — STUB (retourne 0.0)

### `nativeGetSystemAvailableMB`
```java
static native long nativeGetSystemAvailableMB();
```
- **Retour** — Mémoire système disponible en MB (via `/proc/meminfo`)
- **Statut** — ✅ IMPLÉMENTÉ

### `nativeGetTemperature`
```java
static native int nativeGetTemperature();
```
- **Retour** — Température max en °C (via `/sys/class/thermal/`)
- **Statut** — ✅ IMPLÉMENTÉ

### `nativeIsThermalThrottling`
```java
static native boolean nativeIsThermalThrottling();
```
- **Retour** — `true` si température > 50°C
- **Statut** — ✅ IMPLÉMENTÉ

### `nativeDestroy`
```java
static native void nativeDestroy();
```
- Détruit le VMA allocator et le VkDevice/VkInstance
- **Statut** — STUB

---

## GLInterceptorBridge

Classe Java : `fr.eaielectronic.nativeglengine.GLInterceptorBridge`

### `nativeInstallHooks`
```java
static native boolean nativeInstallHooks();
```
- Installe les hooks PLT sur les fonctions GL de LWJGL
- Recherche : `liblwjgl_opengl.so`, `libGL.so`, `libGLESv3.so`
- **Retour** — `true` si hooks installés
- **Statut** — PARTIEL (détecte le handle, pas de vrais hooks)

### `nativeUninstallHooks`
```java
static native void nativeUninstallHooks();
```
- Restaure les pointeurs originaux dans la PLT
- **Statut** — STUB

### `nativeGetTotalGLCalls`
```java
static native long nativeGetTotalGLCalls();
```
- **Retour** — Nombre total d'appels GL interceptés
- **Statut** — STUB (compteur à 0)

### `nativeGetDedupedCalls`
```java
static native long nativeGetDedupedCalls();
```
- **Retour** — Nombre d'appels GL filtrés (redondants)
- **Statut** — STUB (compteur à 0)

### `nativeGetDeferredTextures`
```java
static native long nativeGetDeferredTextures();
```
- **Retour** — Nombre de textures en queue (upload différé)
- **Statut** — STUB (compteur à 0)

### `nativeDrainTextureQueue`
```java
static native void nativeDrainTextureQueue(int maxUploads);
```
- Draine la queue de textures différées (max `maxUploads` par appel)
- Budget temps : max 2ms
- **Statut** — STUB

---

## Enums C++ (shader_compiler.h)

### ShaderType
| Valeur | Nom | Description |
|---|---|---|
| 0 | VERTEX | Vertex shader |
| 1 | FRAGMENT | Fragment shader |
| 2 | GEOMETRY | Geometry shader (non supporté GLES) |
| 3 | COMPUTE | Compute shader (GLES 3.1+) |

### SocVendor
| Valeur | Nom | Description |
|---|---|---|
| 0 | VENDOR_UNKNOWN | Non détecté |
| 1 | VENDOR_QUALCOMM | Qualcomm Adreno |
| 2 | VENDOR_ARM | ARM Mali |
| 3 | VENDOR_MEDIATEK | MediaTek Dimensity |
| 4 | VENDOR_SAMSUNG | Samsung Xclipse |
| 5 | VENDOR_GOOGLE_TENSOR | Google Tensor |
| 6 | VENDOR_HUAWEI | Huawei Kirin |
