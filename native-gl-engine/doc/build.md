# Build — NativeGLEngine

## Prérequis

| Outil | Version | Vérification |
|---|---|---|
| Java | 21 (LTS) | `java -version` |
| Gradle | 8.8+ (via wrapper) | `./gradlew --version` |
| Android NDK | r26+ | `$ANDROID_NDK_HOME/source.properties` |
| CMake | 3.22+ | `cmake --version` |

## Build rapide

### 1. Compiler le C++ natif

```bash
cd native-gl-engine
./build-native.sh
```

Cela :
- Configure CMake avec le toolchain NDK
- Télécharge Shaderc, SPIRV-Cross, VMA via FetchContent
- Compile `libNativeGLEngine.so` pour arm64-v8a
- Copie la `.so` dans `src/main/resources/assets/nativeglengine/native/arm64-v8a/`

### 2. Builder le JAR NeoForge

```bash
./gradlew build
```

Le JAR final est dans `build/libs/nativeglengine-0.1.0.jar`.

## Build manuel CMake

```bash
export ANDROID_NDK_HOME=/chemin/vers/android-ndk-r26d

cd src/main/cpp

# Configuration
cmake \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release \
    -S . -B build

# Compilation
cmake --build build -j$(nproc)

# Copie manuelle
mkdir -p ../resources/assets/nativeglengine/native/arm64-v8a/
cp build/libNativeGLEngine.so ../resources/assets/nativeglengine/native/arm64-v8a/
```

## Dépendances C++ (gérées par CMake FetchContent)

| Dépendance | Version | Licence | Rôle |
|---|---|---|---|
| [Shaderc](https://github.com/google/shaderc) | v2024.1 | Apache 2.0 | GLSL → SPIR-V |
| [SPIRV-Cross](https://github.com/KhronosGroup/SPIRV-Cross) | sdk-1.3.268.0 | Apache 2.0 | SPIR-V → ESSL |
| [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator) | v3.1.0 | MIT | Mémoire GPU |

## Librairies système Android linkées

| Librairie | Usage |
|---|---|
| `liblog.so` | `__android_log_print` (logging) |
| `libandroid.so` | NDK utilities |
| `libEGL.so` | Contexte OpenGL/Vulkan |
| `libGLESv3.so` | OpenGL ES 3.x |
| `libvulkan.so` | Vulkan API |

## Flags de compilation

```
-std=c++17          # Standard C++17
-O3                 # Optimisation maximale
-ffast-math         # Optimisations mathématiques agressives
-fvisibility=hidden # Ne pas exporter les symboles internes
-fomit-frame-pointer # Gain perf léger sur ARM64
-funwind-tables     # Stack traces pour le debug
-s                  # Strip symboles en Release
```

## Troubleshooting

### "ANDROID_NDK_HOME non défini"
```bash
export ANDROID_NDK_HOME=/chemin/vers/android-ndk-r26d
```

### "CMake ne trouve pas le toolchain"
Vérifiez que le fichier existe :
```bash
ls $ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake
```

### "FetchContent timeout"
Le premier build télécharge ~500MB de dépendances. Attendez ou utilisez un miroir.

### "La .so ne se charge pas sur Android"
- Vérifiez l'architecture : `file libNativeGLEngine.so` → doit afficher `ARM aarch64`
- Vérifiez l'API level : compilé pour `android-26` (Android 8.0+)
- Vérifiez les logs : `adb logcat -s NativeGLEngine`
