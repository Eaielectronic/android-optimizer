#  Profils de Téléphones (SoC) : Comment le mod vous identifie

 *[English translation available below!](#english-version)*

Les téléphones Android sont tous différents. Un vieux Xiaomi n'a pas du tout la même architecture processeur (SoC) qu'un Samsung Galaxy S24 récent. L'Optimizer s'adapte à votre appareil grâce à son système de profils intelligents.

## ️ Comment la détection marche en vrai ?

Quand vous lancez le jeu, le mod va analyser les fichiers système d'Android :
1. Il lit `/proc/cpuinfo` pour trouver le nom de code secret de votre matériel.
2. Il exécute la commande interne `getprop ro.hardware`.

**Exemple concret :** Vous avez un Samsung Galaxy S20 FE.
Le mod cherche et trouve le mot caché `kona`. Il ouvre sa base de données (le fichier `soc_profiles.json`) et comprend : "Ainsi, `kona` c'est le processeur Snapdragon 865 ! Je vais appliquer 30 FPS max et 4 chunks de distance de rendu".

##  Le gros avantage : Le Thread Affinity (Big.LITTLE)

Les puces de téléphones (ARM) sont très spécifiques. Elles ont des "petits cœurs" (qui consomment peu d'énergie mais sont très lents) et des "Gros cœurs" (très rapides, faits pour le gaming).

Parfois, Android se trompe et donne les calculs de Minecraft à un "petit cœur". Le résultat ? Vous avez 8 FPS.
Notre mod utilise une commande système cachée Linux (`taskset`) pour obliger Android à confier les graphismes du jeu exclusivement à vos gros cœurs. Magie : vos FPS doublent instantanément.

## Ajouter votre propre appareil s'il n'est pas reconnu

Vous avez un téléphone très spécifique et le HUD t'affiche `SoC: Générique` ? Pas besoin de savoir coder en Java !
Ouvrez le fichier `src/main/resources/assets/androidopt/soc_profiles.json` et ajoutez votre appareil dedans. 

Trouvezz juste le mot-clé de votre appareil en installant une app comme AIDA64 ou en tapant `adb shell getprop ro.hardware` sur votre PC, et ajoutez-le à la liste des "keywords".

---
<br><br>

<a name="english-version"></a>
#   Phone Profiles (SoC): How the mod recognizes you

Android phones are all completely different. An old Xiaomi does not share the same processor architecture (SoC) as a brand new Samsung Galaxy S24. The Optimizer adapts to your phone using a smart profile system.

## ️ How detection actually works

When you boot the game, the mod digs straight into sensitive Android system files:
1. It reads `/proc/cpuinfo` to find your hardware's secret codename.
2. It fires the internal command `getprop ro.hardware`.

**Real Example:** You have a Samsung Galaxy S20 FE.
The mod searches and finds the hidden word `kona`. It checks its database (the `soc_profiles.json` file) and realizes: "Aha! `kona` is the Snapdragon 865 processor! I will instantly apply a 30 FPS cap and 4 chunks of render distance".

##  The killer feature: Thread Affinity (Big.LITTLE)

Phone chips (ARM) are weird. They have "little cores" (which use low battery but are super slow) and "Big Cores" (ultra-fast, made for hardcore gaming).

Sometimes, Android gets confused and hands Minecraft's heavy calculations to a "little core". The result? You get 8 FPS.
Our mod uses a hidden Linux system command (`taskset`) to force Android to give the game's graphics exclusively to your big cores. Magic: your FPS double instantly.

## How to add your own phone if it isn't recognized

Got a super obscure phone and the HUD is showing `SoC: Generic`? You don't need to know Java to fix it!
Just open the `src/main/resources/assets/androidopt/soc_profiles.json` file and add your phone to it.

Find your phone's codename by installing an app like AIDA64 or typing `adb shell getprop ro.hardware` on your PC, and throw it into the "keywords" list in the JSON file.
