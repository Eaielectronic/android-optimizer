# Contributing to Android Optimizer

Thank you for considering contributing to Android Optimizer! This document explains how to get started, what we expect from contributions, and how to test your changes.

## Getting Started

### Prerequisites
- **Java 21** (OpenJDK, Adoptium, or Liberica)
- **Gradle** (included via wrapper, no manual install needed)
- **An IDE** — IntelliJ IDEA is strongly recommended

### Setting Up the Development Environment

```bash
git clone https://github.com/Eaielectronic/android-optimizer.git
cd android-optimizer
./gradlew build --no-daemon
```

The first build downloads Minecraft + NeoForge dependencies (~5 minutes). Subsequent builds take ~15 seconds.

### Running in Dev Mode

```bash
./gradlew runClient
```

This launches Minecraft with the mod loaded. Set `forceEnable = true` in `config/androidopt-client.toml` to test optimizations on a PC.

## How to Contribute

### Reporting Bugs

Open an [Issue](https://github.com/Eaielectronic/android-optimizer/issues) with:
- Your device model and SoC (e.g., "Samsung Galaxy S20 FE, Snapdragon 865")
- Minecraft version and launcher (Amethyst / PojavLauncher)
- A screenshot of the in-game HUD (top-right corner)
- The `latest.log` file (use [mclo.gs](https://mclo.gs) to share it)
- The `androidopt_freeze_report_*.txt` file if available

### Adding a New SoC Profile

This is the easiest way to contribute! No Java knowledge needed.

1. Find your SoC codename:
   ```bash
   adb shell getprop ro.hardware
   adb shell getprop ro.product.board
   adb shell getprop ro.soc.model
   ```
2. Edit `src/main/resources/assets/androidopt/soc_profiles.json`
3. Add your profile following the existing format
4. Submit a Pull Request

### Adding a New Optimization

1. Determine if it should be a **Handler** (`client/`) or a **Mixin** (`mixin/`)
2. If it's a Mixin: add the class name to `androidopt.mixins.json`
3. If it targets an optional mod (Create, JEI, Sable): use `require = 0` and `remap = false`
4. Add a config toggle in `OptConfig.java`
5. Add the toggle to the GUI in `OptConfigScreen.java`
6. Add translation keys in `en_us.json` and `fr_fr.json`

### Code Style

- Use the existing package structure (`fr.eaielectronic.androidopt.*`)
- All Mixins targeting optional mods must use `require = 0`
- Never call `OptConfig.*.get()` in Mixins that run before `FMLClientSetupEvent` — use `ConfigGuard.isReady()` instead
- Keep log messages prefixed with `[AndroidOpt]`

## Testing Checklist

Before submitting a PR, please verify:

- [ ] `./gradlew build` completes without errors
- [ ] The mod launches with `./gradlew runClient` and `forceEnable = true`
- [ ] The HUD displays correctly (FPS, RAM, Budget level)
- [ ] Your optimization appears as a toggle in the Config screen
- [ ] Disabling the toggle actually disables the optimization
- [ ] No crash when the targeted mod (Create, JEI, etc.) is absent

## Manual Testing on Android

If you have access to an Android device running PojavLauncher or Amethyst:

1. Build the JAR: `./gradlew build`
2. Copy `build/libs/androidopt-1.0.0.jar` to the device's `mods/` folder
3. Launch the game and check:
   - Is the SoC detected correctly? (HUD shows green SoC name)
   - Are freezes reduced? (Check the freeze counter in the HUD)
   - Is the FPS improvement measurable?

## Project Structure

```
fr.eaielectronic.androidopt/
├── AndroidDetector.java      # Android/PC detection
├── OptConfig.java            # All config options (TOML)
├── SocDetector.java          # SoC identification
├── client/                   # Event handlers and utilities
│   ├── MemoryWatchdog.java   # Preventive GC
│   ├── FrameBudgetManager.java # Adaptive quality levels
│   ├── CreateBerCuller.java  # Distance-based BER culling
│   ├── OptConfigScreen.java  # In-game config GUI
│   └── ...
└── mixin/                    # Bytecode injections
    ├── BERDistanceMixin.java # Core FPS optimization
    ├── ChunkRebuildMixin.java
    └── ...
```

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
