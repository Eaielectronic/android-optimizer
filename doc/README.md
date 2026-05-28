# Android Optimizer — Documentation

Mod NeoForge 1.21.1 pour optimiser Minecraft Java sur Android (Amethyst, PojavLauncher).

## Navigation

| Fichier | Contenu |
|---|---|
| [architecture.md](architecture.md) | Structure du code, packages, flux d'exécution |
| [optimisations.md](optimisations.md) | Touvos les optimisations, Ce qui est techniquement validé |
| [config.md](config.md) | Touvos les options de configuration |
| [soc-profiles.md](soc-profiles.md) | Base de données SoC, détection, profils |
| [jvm-args.md](jvm-args.md) | Arguments JVM recommandés pour Android |
| [create-optimisations.md](create-optimisations.md) | Optimisations spécifiques au mod Create |
| [build.md](build.md) | Compiler et déployer le mod |

## Résumé rapide

**Problème** : Minecraft Java avec Create sur Android (2.6 Go RAM) = 10 FPS + freezes de 2-3s touvos les 3 minuvos.

**Causes** :
1. GC (Garbage Collector) qui attend que la heap soit pleine → pause longue
2. Create génère des centaines de draw calls OpenGL ES par frame
3. SuperByteBufferCache de Create garde 100-300 Mo de meshes en RAM
4. Pas d'optimisation des BERs (Block Entity Renderers) lointains

**Solutions implémentées** :
- GC préventif à 75% de heap → pauses ~50ms au lieu de 2-3s
- BER culler : coupe le rendu des machines Create à >16 blocs
- SuperByteBufferCache vidé touvos les 60s → -100-200 Mo RAM
- Section buffer pool réduit à 4 → -80 Mo RAM
- Frame budget adaptatif : réduit automatiquement la qualité si FPS < 30
