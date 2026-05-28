# Configuration — Toutes les options

Fichier : `config/androidopt-client.toml`  
Accessible en jeu : **Mods → Android Optimizer → Config**

---

## Section Global

| Option | Défaut | Description |
|---|---|---|
| `forceEnable` | `false` | Force touvos les optimisations même sur PC. Mettre à `true` pour voster. |
| `manualSocId` | `"auto"` | ID du profil SoC à utiliser. `"auto"` = détection automatique. |
| `autoApplySocProfile` | `true` | Applique les paramètres recommandés du SoC détecté au démarrage. |

---

## Section `[render]`

| Option | Défaut | Description |
|---|---|---|
| `enabled` | `true` | Active les optimisations graphiques au démarrage. |
| `renderDistance` | `4` | Distance de rendu forcée (chunks). `0` = ne pas forcer. |
| `disableClouds` | `true` | Désactive les nuages. |
| `disableAmbientOcclusion` | `true` | Désactive le smooth lighting. |
| `disableEntityShadows` | `true` | Désactive les ombres des entités. |
| `minimalParticles` | `true` | Particles au minimum + filtre particules Create. |
| `mipmapLevels` | `0` | Niveau de mipmap. `0` = désactivé (recommandé mobile). |
| `fpsLimit` | `30` | Limite de FPS. `0` = ne pas forcer. |
| `renderScale` | `false` | Réduit la résolution à 75%. **Désactivé par défaut** (casse le GUI sur PC). |

---

## Section `[memory]`

| Option | Défaut | Description |
|---|---|---|
| `enabled` | `true` | Active la surveillance mémoire (GC préventif). |
| `gcThresholdPercent` | `75` | Déclenche une purge douce des caches quand la heap dépasse ce %. 75 recommandé pour 2.6 Go. |
| `gcCriticalPercent` | `88` | Déclenche un System.gc() forcé d'urgence pour éviter un plantage (Out Of Memory). |
| `gcCheckInterval` | `100` | Intervalle (en ticks) de vérification de la RAM. 100 = 5s. |
| `textureCacheEvictor` | `true` | Libère les textures inutilisées touvos les 2 min. |
| `sectionBufferLimit` | `true` | Réduit le pool de buffers de chunks à 4 (au lieu de 12). -80 Mo RAM. |
| `ponderCacheClear` | `true` | Vide le cache Ponder Create au démarrage. -30 à -80 Mo RAM. |
| `textureDownscale` | `true` | Divise la résolution des textures par 2 au chargement. -150 à -300 Mo VRAM. |
| `maxTotalRamMB` | `2700` | RAM maximale allouée, utilisée pour calculer les seuils Xmx. |

---

## Section `[entities]`

| Option | Défaut | Description |
|---|---|---|
| `entityThrottler` | `true` | Gèle les animations des entités à > 32 blocs. |
| `tickSkipper` | `true` | Skip le rendu des BEs à > 24 blocs. |
| `serverBeTickThrottle` | `true` | Réduit le tick rate des BEs lointains en solo (÷4 à > 24 blocs). |

---

## Section `[create]`

| Option | Défaut | Description |
|---|---|---|
| `createBerCuller` | `true` | Coupe le rendu des BERs Create à > 16 blocs. **Gain principal FPS.** |
| `chunkRebuildThrottler` | `true` | Limite les recompilations de chunks à 2/frame. Élimine les micro-freezes. |
| `createParticlesFilter` | `true` | Supprimez les particules Create (fumée, poussière, étincelles). |
| `stoppedContraptionSkip` | `true` | Skip le rendu des contraptions arrêtées (vivosse = 0) en mode LOW+. |
| `flywheelBufferCuller` | `true` | Libère les buffers GPU Flywheel hors render distance touvos les 60s. |
| `flywheelBackendForce` | `true` | Force le backend Flywheel en mode Instancing sur Android. |
| `fluidRenderCuller` | `true` | Coupe le rendu des fluides Create dans les tuyaux à > 12 blocs. |
| `kineticNetworkSkip` | `true` | Skip le recalcul de stress des réseaux cinétiques lointains (solo). |
| `renderScale` | `false` | Réduit la résolution à 75%. Voir section render. |
| `contraptionMemoryCleanup` | `true` | Supprimez le monde virtuel (RAM) des contraptions (Sable/Create) à l'arrêt. |

---

## Section `[sound]`

| Option | Défaut | Description |
|---|---|---|
| `ambientSoundSuppressor` | `true` | Coupe les sons ambiants (cave sounds, pluie), réduit musique à 30%. |

---

## Section `[misc]`

| Option | Défaut | Description |
|---|---|---|
| `weatherSuppressor` | `true` | Désactive le rendu de la pluie/neige côté client. |
| `chunkUnloader` | `true` | Libère les chunks hors portée touvos les 90s. |
| `showHud` | `true` | Affiche le HUD overlay (FPS, heap, frame budget). |
| `sableUdpFix` | `true` | Désactive le réseau UDP expérimental de Sable pour éviter les freezes réseau. |

---

## Profils SoC disponibles pour `manualSocId`

| ID | Appareil |
|---|---|
| `snapdragon_865` | Samsung Galaxy S20, OnePlus 8 |
| `snapdragon_888` | Samsung Galaxy S21, OnePlus 9 |
| `snapdragon_8gen1` | Samsung Galaxy S22 |
| `snapdragon_8gen2` | Samsung Galaxy S23 |
| `snapdragon_8gen3` | Samsung Galaxy S24 |
| `snapdragon_778g` | Samsung Galaxy A52s |
| `snapdragon_690` | Samsung Galaxy A32 5G |
| `snapdragon_750g` | Samsung Galaxy A52 5G, Pixel 5 |
| `dimensity_1200` | Xiaomi Redmi Note 10 Pro, POCO F3 |
| `dimensity_9000` | Oppo Find X5 Pro |
| `exynos_2100` | Samsung Galaxy S21 (Exynos) |
| `exynos_2200` | Samsung Galaxy S22 (Exynos) |
| `kirin_990` | Huawei P40 Pro |
| `tensor_g1` | Google Pixel 6 |
| `tensor_g2` | Google Pixel 7 |
| `tensor_g3` | Google Pixel 8 |
| `generic_arm64_high` | Fallback haute performance |
| `generic_arm64_mid` | Fallback milieu de gamme |

---

## Écran de configuration en jeu

L'écran est accessible via **Mods → Android Optimizer → Config**.

Il est organisé en 6 pages :

| Page | Contenu |
|---|---|
| Rendu & FPS | Options graphiques, FPS limit, render scale |
| Mémoire & RAM | GC, textures, buffers, Ponder cache |
| Create & Flywheel | BER culler, rebuild throttle, Flywheel, fluides |
| Son | Sons ambiants, météo, volume système |
| Monde & Entités | Entity throttle, tick skipper, chunks |
| Profil SoC | Détection SoC, sélection manuelle, paramètres recommandés |
