# Contributing to NativeGLEngine

Merci de considérer contribuer à NativeGLEngine ! Ce document explique comment démarrer, ce que nous attendons des contributions, et comment tester vos modifications.

*[English version below](#english-version)*

---

## Prérequis

### Pour les contributions Java (mod NeoForge)
- **Java 21** (OpenJDK, Adoptium, ou Liberica)
- **Gradle** (inclus via le wrapper, pas d'installation manuelle)
- **IntelliJ IDEA** (fortement recommandé)

### Pour les contributions C++ (NDK natif)
- **Android NDK r26+** avec la variable `ANDROID_NDK_HOME` définie
- **CMake 3.22+**
- Connaissance de JNI et des APIs Android natives

---

## Mise en place de l'environnement

```bash
git clone https://github.com/Eaielectronic/android-optimizer.git
cd android-optimizer/native-gl-engine
./gradlew build --no-daemon
```

Le premier build télécharge les dépendances Minecraft + NeoForge (~5 minutes).

### Lancer en mode développement

```bash
./gradlew runClient
```

> **Note :** Sur PC, le mod charge en mode "Java seul" (pas de .so native). Utilisez les logs pour vérifier le comportement.

---

## Comment contribuer

### 🐛 Signaler un bug

Ouvrez une [Issue](https://github.com/Eaielectronic/android-optimizer/issues) avec :
- Modèle de l'appareil et SoC (ex: "Samsung Galaxy S23, Snapdragon 8 Gen 2")
- Version Minecraft et lanceur (ZalithLauncher 2 / Amethyst / FCL)
- Le fichier `latest.log` (utilisez [mclo.gs](https://mclo.gs) pour le partager)
- Les étapes pour reproduire le problème

### 💻 Contribuer du code

#### Java — Ajouter une optimisation

1. Déterminez si c'est un **Handler** (événement NeoForge) ou un **Mixin** (injection bytecode)
2. Si Mixin : ajouter le nom de la classe dans `nativeglengine.mixins.json`
3. Si cela cible un mod optionnel : utiliser `require = 0` et `remap = false`
4. Ajouter un toggle dans `NativeGLConfig.java`
5. Ajouter les clés de traduction dans `en_us.json` et `fr_fr.json`

#### C++ — Implémenter un stub

Les fichiers C++ contiennent des stubs (`TODO`) à implémenter :

| Fichier | Stub à implémenter | Dépendance |
|---|---|---|
| `shader_compiler.cpp` | Pipeline GLSL → SPIR-V → ESSL | Shaderc, SPIRV-Cross |
| `soc_optimizer.cpp` | Passes spirv-opt par SoC | SPIRV-Tools |
| `native_memory.cpp` | VMA allocator + VK_EXT_memory_budget | Vulkan SDK, VMA |
| `gl_interceptor.cpp` | PLT hooking réel | dlsym, ELF |
| `texture_manager.cpp` | Queue MPSC + drain | Aucune |

#### JNI — Ajouter un nouveau bridge

1. Déclarer la méthode `native` dans la classe Java correspondante
2. Implémenter la fonction JNI dans `jni_bridge.cpp` avec la convention de nommage :
   ```
   Java_fr_eaielectronic_nativeglengine_<Classe>_<methode>
   ```
3. Recompiler la `.so` via `build-native.sh`

### 📝 Style de code

#### Java
- Package : `fr.eaielectronic.nativeglengine.*`
- Tous les Mixins ciblant des mods optionnels doivent utiliser `require = 0`
- Logs préfixés avec `[NativeGLEngine]`
- Utiliser `NativeGLEngineMod.LOGGER` (SLF4J)

#### C++
- Standard : C++17
- Logs via `__android_log_print` avec tag `"NativeGLEngine"`
- Macros : `LOGI(...)`, `LOGW(...)`, `LOGD(...)`
- Pas de `new`/`delete` explicite (utiliser des wrappers RAII)
- Headers : `#pragma once`

---

## Checklist avant Pull Request

- [ ] `./gradlew build` compile sans erreur
- [ ] Le mod se charge avec `./gradlew runClient`
- [ ] Les logs `[NativeGLEngine]` montrent l'initialisation correcte
- [ ] Si modification C++ : `build-native.sh` compile sans erreur
- [ ] Si modification C++ : la `.so` est copiée dans `src/main/resources/assets/nativeglengine/native/arm64-v8a/`
- [ ] Si ajout de config : le toggle apparaît et fonctionne
- [ ] Si ajout de Mixin : `nativeglengine.mixins.json` est mis à jour
- [ ] Les traductions sont ajoutées dans `en_us.json` et `fr_fr.json`

---

## Structure du projet

```
native-gl-engine/
├── src/main/
│   ├── java/fr/eaielectronic/nativeglengine/
│   │   ├── NativeGLEngineMod.java      # @Mod entry point
│   │   ├── NativeLib.java              # .so loader
│   │   ├── ShaderCompilerBridge.java   # JNI shader
│   │   ├── ShaderCacheManager.java     # Cache L1/L2
│   │   ├── ShaderPlaceholderManager.java
│   │   ├── NativeMemoryBridge.java     # JNI memory
│   │   ├── GLInterceptorBridge.java    # JNI hooks
│   │   ├── RendererDetector.java       # GL renderer
│   │   ├── AndroidOptBridge.java       # Réflexion androidopt
│   │   ├── NativeGLConfig.java         # Config TOML
│   │   ├── NativeGLTickHandler.java    # Client tick
│   │   ├── MemoryReport.java           # POJO
│   │   └── mixin/
│   │       └── ShaderProgramMixin.java
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── jni_bridge.cpp
│   │   ├── shader_compiler.cpp/.h
│   │   ├── shader_cache.cpp/.h
│   │   ├── soc_optimizer.cpp/.h
│   │   ├── gl_interceptor.cpp/.h
│   │   ├── native_memory.cpp/.h
│   │   └── texture_manager.cpp/.h
│   ├── resources/
│   │   ├── nativeglengine.mixins.json
│   │   └── assets/nativeglengine/
│   │       ├── native/arm64-v8a/libNativeGLEngine.so
│   │       └── lang/{en_us,fr_fr}.json
│   └── templates/META-INF/neoforge.mods.toml
├── build.gradle
├── gradle.properties
├── settings.gradle
├── build-native.sh
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── SECURITY.md
└── LICENSE
```

---

## Licence

En contribuant, vous acceptez que vos contributions soient publiées sous la licence MIT.

---

<a name="english-version"></a>
## English Version

### Prerequisites
- Java 21, Gradle (wrapper), IntelliJ IDEA
- For C++: Android NDK r26+, CMake 3.22+

### Setup
```bash
git clone https://github.com/Eaielectronic/android-optimizer.git
cd android-optimizer/native-gl-engine
./gradlew build --no-daemon
```

### Contributing
- **Bug reports**: Open an Issue with device model, SoC, launcher, and latest.log
- **Java**: Follow `fr.eaielectronic.nativeglengine.*` package structure
- **C++**: C++17, Android logging macros, `#pragma once` headers
- **JNI**: Follow `Java_fr_eaielectronic_nativeglengine_<Class>_<method>` naming

### PR Checklist
- `./gradlew build` passes
- Mod loads correctly via `./gradlew runClient`
- C++ changes: `build-native.sh` compiles, `.so` copied to resources
- Translations added to both `en_us.json` and `fr_fr.json`

### License
By contributing, you agree that your contributions will be licensed under the MIT License.
