# Android Optimizer (Minecraft 1.21.1)

*[English translation available below!](#english-version)*

Un mod NeoForge 1.21.1 concu pour optimiser Minecraft Java sur les telephones Android (Amethyst, PojavLauncher). Il cible les appareils disposant de **2 a 4 Go de RAM** et d'un processeur ARM64, tout en supportant les modpacks lourds comme ceux bases sur **Create**.

---

## Installation

1. Telechargez `androidopt-1.0.0.jar` depuis les [releases](../../releases) ou compilez-le.
2. Placez-le dans le dossier `mods/` de votre instance Minecraft.
3. Lancez le jeu — le mod s'adapte automatiquement a votre appareil.

---

## Important : Arguments JVM (Anti-Freeze & ZGC)

**CRITIQUE POUR ÉVITER LES FREEZES (PojavLauncher / Android)** : Android bloque le ZGC (Z Garbage Collector) pour des raisons de sécurité mémoire (SELinux). Pour éviter les énormes freezes causés par le nettoyage de la RAM, vous devez utiliser le **G1GC** avec ces arguments d'optimisation spécifiques :
👉 `-XX:+UseG1GC -XX:MaxGCPauseMillis=30 -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15`

**Consultez notre [Tableau des Configurations RAM & CPU (doc/jvm-args.md)](doc/jvm-args.md) pour obtenir plus de détails sur l'optimisation G1GC.**
Vous y trouverez les explications sur la réduction des micro-pauses (`MaxGCPauseMillis`).

---

## Fonctionnement interne

Ce mod intervient directement dans le code du jeu et de certains mods lourds pour y effectuer des optimisations chirurgicales.

Principales optimisations :

1. **BER Culler** : Les mods industriels comme Create dessinent chaque engrenage individuellement. Sur telephone, cela degrade fortement les performances graphiques. Le mod coupe l'affichage des machines situees au-dela de 16 blocs, liberant ainsi le GPU.
2. **Reduction des textures (VRAM)** : Au demarrage, le mod divise la resolution de toutes les textures par 2, incluant les animations (`FrameSize`). Resultat : **-150 a -300 Mo de memoire graphique**.
3. **Nettoyage RAM par reflexion (Create/Sable)** : Quand une machine (Contraption) s'arrete, elle laisse un "monde fantome" en memoire. Le mod utilise la reflexion Java pour detecter et liberer cette memoire. Gain : **~30 a 80 Mo par machine arretee**.
4. **Correction reseau Sable (UDP)** : Le mod Sable tente d'utiliser un protocole UDP experimental qui bloque le jeu pendant 200 a 300 millisecondes en cas d'echec. Le paquet serveur est intercepte et annule silencieusement, forcant le reseau a utiliser TCP.
5. **Surveillance Mémoire Intelligente (Watchdog)** : Au lieu d'attendre la saturation de la RAM (qui provoque des freezes de 3 secondes) ou de forcer le GC manuellement, le mod déclenche des **purges asynchrones** des caches Create et des textures dès que la mémoire atteint un seuil (ex: **75%**). Le Garbage Collector natif s'occupe ensuite de petites micro-pauses indolores. Un `System.gc()` n'est forcé qu'en cas d'extrême urgence (seuil critique configurable, ex: 88%) pour éviter le crash.

### Gains estimes (teste avec Create)

| Metrique | Avant | Apres |
|---|---|---|
| **Freezes RAM** | 2-3 secondes toutes les 2 min | ~50ms, quasi invisibles |
| **FPS en base Create** | 8-15 FPS | 15-25 FPS |
| **Micro-stutters (reseau/machines)** | Tres frequents | Elimination totale |

---

## Configuration (In-Game)

Tous les parametres sont accessibles sans redemarrage : **Mods -> Android Optimizer -> Config**

- **Rendu et FPS** (Nuages, ombres, particules)
- **Memoire et RAM** (Textures, seuil GC, intervalle de verification)
- **Create et Flywheel** (Distance de culling, rebuild throttle)
- **Son** (Sons ambiants, meteo)
- **Monde et Entites** (Entity throttle, chunk unloader)
- **Profil SoC** (Detection automatique du processeur)

---

## Documentation

| Guide | Description |
|---|---|
| [doc/jvm-args.md](doc/jvm-args.md) | Arguments Java pour optimiser l'utilisation de la RAM |
| [doc/config.md](doc/config.md) | Detail de chaque option de configuration |
| [doc/architecture.md](doc/architecture.md) | Fonctionnement technique complet (Mixins, flux) |
| [doc/soc-profiles.md](doc/soc-profiles.md) | Base de donnees des appareils compatibles |

---

<br>
<br>

<a name="english-version"></a>
# English Version: Android Optimizer (Minecraft 1.21.1)

A NeoForge 1.21.1 mod designed to optimize Minecraft Java on Android phones (Amethyst, PojavLauncher). It targets devices with **2 to 4 GB of RAM** and an ARM64 processor, making heavy modpacks like **Create** playable.

---

## Installation

1. Download `androidopt-1.0.0.jar` from the [releases](../../releases) or build it yourself.
2. Place it in your Minecraft instance's `mods/` folder.
3. Launch the game — the mod automatically detects your phone's processor and applies optimal settings.

---

## Important: JVM Arguments (Anti-Freeze & ZGC)

**CRITICAL TO AVOID FREEZES (PojavLauncher / Android)**: Android blocks ZGC (Z Garbage Collector) for memory security reasons (SELinux). To avoid huge freezes caused by RAM cleanup, you must use **G1GC** with these specific optimization arguments:
👉 `-XX:+UseG1GC -XX:MaxGCPauseMillis=30 -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15`

**Please refer to our [RAM & CPU Configurations Table (doc/jvm-args.md)](doc/jvm-args.md) to get more details on G1GC optimization.**
You will find explanations on reducing micro-pauses (`MaxGCPauseMillis`).

---

## How It Works (Technical Details)

This mod deeply injects into the core game code and heavy mods to perform surgical optimizations.

Key optimizations:

1. **BER Culler**: Industrial mods like Create draw every cog individually. On a phone, this destroys graphical performance. The mod cuts rendering of machines beyond 16 blocks, freeing the GPU.
2. **Texture Downscaling (VRAM)**: During boot, the mod halves the resolution of all textures, including animation sizes (`FrameSize`). Result: **-150 to -300 MB VRAM saved**.
3. **Reflection-based RAM Sweeper (Create/Sable)**: When a contraption stops, it leaves a "ghost world" in memory. The mod uses Java Reflection to detect and free this memory. Gain: **~30 to 80 MB per stopped machine**.
4. **Sable Network UDP Fix**: The Sable mod attempts to use an experimental UDP protocol that freezes the game for 200-300ms on failure. The server packet is intercepted and silently cancelled, forcing TCP fallback.
5. **Smart Memory Watchdog**: Instead of waiting for RAM saturation (causing 3-second freezes) or forcing a manual GC, the mod triggers **asynchronous purges** of Create and texture caches as soon as memory reaches a soft threshold (e.g., **75%**). Java's native Garbage Collector then handles the cleanup in painless micro-pauses. A hard `System.gc()` is only forced in extreme emergencies (configurable critical threshold, e.g., 88%) to prevent an OutOfMemory crash.

### Estimated Gains (Tested with Create)

| Metric | Before | After |
|---|---|---|
| **RAM Freezes** | 2-3 seconds every 2 mins | ~50ms, basically invisible |
| **FPS in a Create base** | 8-15 FPS | 15-25 FPS |
| **Micro-stutters (network/blocks)** | Very frequent | Completely eliminated |

---

## Configuration (In-Game Menu)

All settings are accessible without restarting: **Mods -> Android Optimizer -> Config**

- **Render and FPS** (Clouds, shadows, particles)
- **Memory and RAM** (Textures, GC threshold, check interval)
- **Create and Flywheel** (Cull distance, rebuild throttle)
- **Sound** (Ambient sounds, weather)
- **World and Entities** (Entity throttle, chunk unloader)
- **SoC Profile** (Automatic processor detection)

---

## License
MIT — see [LICENSE](LICENSE)

---

## Note for Modrinth Moderators
**Target Audience**: This mod is specifically designed to run on Android-based Minecraft launchers (such as PojavLauncher and Amethyst). It will passively disable itself on PC.

**ProcessBuilder / `taskset` usage**:
In order to handle ARM's big.LITTLE CPU architecture efficiently on Android, the class `ArmThreadAffinity.java` uses `ProcessBuilder` to execute the Linux command `taskset`. This ensures the heavy Minecraft rendering thread is bound to the high-performance cores (Big Cores) of the phone.
- This is **guarded** strictly by `if (!AndroidDetector.IS_ANDROID) return;`.
- It will **never** execute on a standard Windows/Mac/Linux PC.
- It also reads `/proc/self/task` to identify the Linux TID of the render thread, which is a standard approach on Android since Java's `Thread.getId()` does not map to OS TIDs.
