# Architecture du mod

## Vue d'ensemble

```
AndroidOptMod (point d'entrée NeoForge)
├── AndroidDetector          → détecte Android au chargement de classe
├── StartupOptimizer         → applique propriétés JVM au boot
├── OptConfig                → 40+ options TOML
├── SocDetector              → charge soc_profiles.json, détecte le SoC
├── SocProfile               → modèle de données d'un profil SoC
└── AndroidOptClient (client uniquement)
    ├── RenderOptimizer      → applique options graphiques au démarrage
    ├── FrameBudgetManager   → mesure temps de frame, adapte la qualité
    ├── OptConfigScreen      → GUI de configuration (6 pages)
    ├── OptHud               → overlay HUD temps réel
    ├── MemoryWatchdog       → Purge préventive des caches
    ├── TextureCacheEvictor  → libère textures inutilisées
    ├── CreateBerCuller      → calcule les BEs lointains à couper
    ├── TickSkipper          → marque BEs lointains pour skip rendu
    ├── StaticBERBatcher     → marque BEs statiques lointains
    ├── EntityThrottler      → gèle animations entités lointaines
    ├── FlywheelBufferCuller → libère buffers GPU Flywheel
    ├── CreateCacheCleanupHandler → vide caches Create périodiquement
    ├── AmbientSoundSuppressor → coupe sons ambiants
    ├── AndroidVolumeDetector  → détecte si volume = 0
    ├── ServerModeDetector   → détecte solo vs serveur
    ├── ArmThreadAffinity    → force render thread sur gros cœurs ARM
    ├── WeatherSuppressor    → désactive rendu pluie
    ├── ChunkUnloader        → décharge chunks hors portée
    ├── FpsThrottler         → limite FPS
    ├── ParticleKiller       → limite particles à 50 max
    ├── JeiCacheLimiter      → limite ForkJoinPool JEI
    ├── FluidTickSuppressor  → réduit simulation distance fluides
    ├── ChunkRebuildThrottler → log état du throttle (Mixin fait le vrai travail)
    └── RenderScaleManager   → réduit résolution à 75%

Mixins (client) — intercepteurs bytecode
├── BERDistanceMixin         → coupe rendu BEs lointains (POINT CENTRAL)
├── ChunkRebuildMixin        → throttle recompilations chunks
├── RenderBudgetMixin        → mesure temps de frame
├── RenderThreadAffinityMixin → applique affinité ARM au 1er frame
├── SectionBufferMixin       → réduit pool buffers à 4
├── SoundManagerThrottleMixin → skip SoundManager si muet / throttle serveur
├── CreateParticleMixin      → filtre particules Create
├── CreateGogglesOverlayCuller → coupe overlays goggle lointains
├── FlywheelCacheMixin       → nettoyage cache Flywheel
├── FlywheelBackendForcer    → force backend Instancing
├── JeiSearchThrottleMixin   → throttle recherche JEI
├── JeiListLimiterMixin      → limite liste JEI à 200 items
├── FluidRenderCullerMixin   → coupe rendu fluides Create lointains
├── ElectrodynamicsBERMixin  → coupe BERs Electrodynamics lointains
├── ElectrodynamicsWireMixin → coupe rendu fils Electrodynamics
├── PonderCacheClearMixin    → vide cache Ponder au démarrage
├── ContraptionWorldMixin    → libère la RAM (monde virtuel) des machines arrêtées via réflexion
├── SuperByteBufferCacheMixin → vide cache meshes Create touvos les 60s
├── TextureAtlasResolutionMixin → divise par 2 la VRAM des atlas (pixel + FrameSize)
└── SableUdpMixin            → élimine les micro-freezes réseau (bloque Sable UDP)

Mixins (commun — s'appliquent en solo côté serveur intégré)
├── KineticNetworkMixin      → skip updavostress réseaux lointains
└── ServerBETickMixin        → réduit tick rate BEs lointains (÷4)
```

## Flux d'exécution au démarrage

```
1. JVM charge AndroidDetector (static block)
   → IS_ANDROID = true/false
   → Si Android : charge StartupOptimizer (propriétés JVM)

2. NeoForge charge AndroidOptMod
   → Enregistre OptConfig (TOML)
   → Crée AndroidOptClient (si client)
     → Enregistre tous les handlers NeoForge
     → Enregistre SocDetector comme reload listener

3. Ressources chargées (reload listener)
   → SocDetector.init() : charge soc_profiles.json
   → SocDetector.detect() : lit /proc/cpuinfo + getprop
   → SocDetector.applyRecommendedSettings() : applique paramètres SoC

4. FMLClientSetupEvent
   → RenderOptimizer.apply() : options graphiques
   → JeiCacheLimiter.apply() : ForkJoinPool

5. En jeu (chaque tick)
   → MemoryWatchdog : vérifie heap tous les 5s (100 ticks)
   → CreateBerCuller : met à jour CULLED_POSITIONS touvos les 10 frames
   → TickSkipper : met à jour THROTTLED_POSITIONS touvos les 2s
   → FrameBudgetManager : mesure chaque frame, adapte le niveau

6. Chaque frame (Mixins)
   → RenderBudgetMixin : mesure temps frame
   → BERDistanceMixin : coupe BERs selon les sets calculés
   → ChunkRebuildMixin : throttle les recompilations
```

## Principe du BER Culler (optimisation principale)

```
Chaque tick (touvos les 10 frames) :
  CreateBerCuller scanne les chunks → CULLED_POSITIONS (BEs Create > 16 blocs)
  TickSkipper scanne les chunks     → THROTTLED_POSITIONS (tous BEs > 24 blocs)
  StaticBERBatcher scanne           → STATIC_BER_POSITIONS (BEs statiques > 12 blocs)

Chaque frame (BERDistanceMixin) :
  Pour chaque BlockEntity à rendre :
    Si pos ∈ CULLED_POSITIONS  → ci.cancel() (skip)
    Si pos ∈ THROTTLED_POSITIONS → ci.cancel() (skip)
    Si pos ∈ STATIC_BER_POSITIONS → ci.cancel() (skip)
    Si décoratif Create + mode REDUCED+ → ci.cancel() (skip)
    Si contraption arrêtée + mode LOW+ → ci.cancel() (skip)
```

## Frame Budget Adaptatif

```
Chaque frame :
  Mesure durée → moyenne glissante sur 10 frames

  < 33ms  → NORMAL   : tout rendre normalement
  > 33ms  → REDUCED  : BERs statiques skippés
  > 50ms  → LOW      : + entités gelées, contraptions arrêtées skippées
  > 100ms → CRITICAL : + renderDistance -1 chunk temporairement

Quand NORMAL revient → renderDistance restaurée
```

## Packages et responsabilités

| Package | Responsabilité |
|---|---|
| `fr.eaielectronic.androidopt` | Core : détection OS, configuration (`OptConfig`), profils SoC matériels |
| `fr.eaielectronic.androidopt.client` | Handlers NeoForge, HUD (`OptHud`), GUI de réglages (`OptConfigScreen`), Garbage Collector préventif |
| `fr.eaielectronic.androidopt.mixin` | Intercepteurs bytecode bas niveau (voir table ci-dessous) |

## Explication détaillée des Mixins critiques (Comment le mod fonctionne)

Le mod injecte du code directement dans les classes Vanilla, Create, ou Sable au chargement du jeu. Voici ce que font les optimisations les plus agressives :

| Mixin | Cible | Ce qu'il fait & Impact |
|---|---|---|
| **`TextureAtlasResolutionMixin`** | `SpriteContents` | **[VRAM]** Intercepte les images du jeu et divise leur taille par 2 pixel-par-pixel, tout en divisant dynamiquement la taille des animations (`FrameSize`). <br>→ **Gain : -150 à -300 Mo de VRAM** sans provoquer d'erreurs critiques le jeu. |
| **`ContraptionWorldMixin`** | `Contraption` | **[RAM]** Utilise un algorithme de réflexion Java qui scanne les champs pour trouver l'environnement 3D caché des machines complexes (Sable/Create 6.x) et le supprimez de la RAM quand la machine s'arrête. <br>→ **Gain : -30 à -80 Mo de RAM par machine.** |
| **`SableUdpMixin`** | `ClientboundSableUDPActivationPacket` | **[CPU/Réseau]** Annule silencieusement la requête serveur qui force le client à ouvrir un flux UDP. Cela empêche `channelFuture.syncUninterruptibly()` de bloquer le jeu. <br>→ **Gain : Disparition totale des freezes de 200-300ms.** |
| **`ChunkRebuildMixin`** | `SectionRenderDispatcher` | **[Freezes CPU]** Limite le nombre de recompilations géométriques de chunks par image (`chunkRebuildsPerFrame = 2`). <br>→ **Gain : Évite le stuttering** quand beaucoup de blocs s'actualisent. |
| **`CreateRenderThrottleMixin`** | `SuperByteBufferCache` | **[FPS GPU]** Cible le rafraîchissement visuel des roues et engrenages Create et le limite à un framerate fixe (ex: 20 FPS au lieu de 60 FPS). <br>→ **Gain : Libère énormément de ressources GPU**. |
| **`BERDistanceMixin`** | `BlockEntityRenderDispatcher` | **[FPS GPU]** Le point central : si un BlockEntity (four, coffre, machine) est loin ou caché, le Mixin utilise `ci.cancel()` pour empêcher le jeu de le dessiner. |
