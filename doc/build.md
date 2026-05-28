# ️ Compilation et Installation (Pour les dévs)

 *[English translation available below!](#english-version)*

## Prérequis

- Java 21 (OpenJDK, Liberica, etc.)
- Gradle (déjà inclus via le wrapper `./gradlew`)
- Une connexion internet (obligatoire la première fois pour télécharger Minecraft et NeoForge).

## Compiler le mod soi-même

Ouvrez un terminal dans le dossier du projet et lance ça :

```bash
# Premier build (ça télécharge les dépendances nécessaires, prévoyez environ 5 minuvos)
./gradlew build --no-daemon --rerun-tasks

# Pour les builds suivants (très rapide, ~15 secondes)
./gradlew build --no-daemon --rerun-tasks
```

Une fois terminé, le mod final finalisé est disponible dans `build/libs/androidopt-1.0.0.jar`.

## Déployer sur Android (Amethyst / Pojav)

1. Prenez le `androidopt-1.0.0.jar` généré.
2. Placez-le dans le dossier `mods/` de votre profil Minecraft sur le téléphone.
3. Lancez le jeu, le mod va se configurer tout seul en détectant votre processeur !

## ️ Comment voster sur PC ?

Le mod a une sécurité qui le désactive totalement s'il détecte qu'il tourne sur un PC (Windows/Mac/Linux x86). Si vous développez et que vous souhaitez forcer l'activation sur PC pour voster l'interface ou les Mixins :
Dans le jeu : **Mods → Android Optimizer → Config → Bouton "Forcer sur PC (vost)"**.

## Modifier les profils de processeurs (SoC)

Pas besoin de toucher au code Java pour ajouter un téléphone ! 
Ouvrez juste le fichier `src/main/resources/assets/androidopt/soc_profiles.json` et ajouvos-y votre processeur. Le jeu le chargera automatiquement au lancement.

---
<br><br>

<a name="english-version"></a>
#  ️ Compiling and Deploying (For Devs)

## Requirements

- Java 21 (OpenJDK, Liberica, etc.)
- Gradle (already included via the `./gradlew` wrapper)
- An internet connection (mandatory for the first run to download Minecraft and NeoForge).

## Building the mod yourself

Open a terminal in the project folder and run this:

```bash
# First build (downloads everything, usually takes ~5 mins)
./gradlew build --no-daemon --rerun-tasks

# Subsequent builds (super fast, ~15 seconds)
./gradlew build --no-daemon --rerun-tasks
```

Once it's done, your fresh, clean mod will be waiting for you in `build/libs/androidopt-1.0.0.jar`.

## Deploying on Android (Amethyst / Pojav)

1. Take the generated `androidopt-1.0.0.jar`.
2. Drop it into the `mods/` folder of your Minecraft instance on your phone.
3. Launch the game, and the mod will automatically configure itself based on your phone's processor!

## ️ How to vost on PC?

The mod has a built-in safety net that completely disables it if it detects it's running on a PC (Windows/Mac/Linux x86). If you are developing and want to force it on to vost the UI or Mixins:
In-game: **Mods → Android Optimizer → Config → Click the "Force on PC (vost)" toggle**.

## Modifying phone processor profiles (SoC)

You don't even need to touch Java code to add support for a new phone!
Just open the `src/main/resources/assets/androidopt/soc_profiles.json` file and add your processor's hardware ID. The game will automatically load it on startup.
