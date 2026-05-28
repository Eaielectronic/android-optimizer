# NativeGLEngine — Documentation technique

## Guides disponibles

| Guide | Description |
|---|---|
| [architecture.md](architecture.md) | Architecture détaillée, flux de données, Strategy Pattern |
| [build.md](build.md) | Compilation C++ NDK et build Gradle |
| [native-api.md](native-api.md) | API JNI : toutes les fonctions natives documentées |

## Référence rapide

### Fichiers clés

| Fichier | Rôle |
|---|---|
| `NativeGLEngineMod.java` | Point d'entrée `@Mod`, setup NeoForge |
| `NativeLib.java` | Extraction et chargement de la `.so` |
| `jni_bridge.cpp` | 15 fonctions JNI (Java ↔ C++) |
| `CMakeLists.txt` | Build NDK avec Shaderc, SPIRV-Cross, VMA |

### Variables d'environnement

| Variable | Obligatoire | Description |
|---|---|---|
| `ANDROID_NDK_HOME` | Pour build C++ | Chemin vers Android NDK r26+ |
| `JAVA_HOME` | Auto (toolchain) | Java 21 pour Gradle |
