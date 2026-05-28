#  Référence des fichiers (Pour les développeurs)

 *[English translation available below!](#english-version)*

Voici l'inventaire complet de tous les fichiers Java du projet, pour comprendre en un clin d'œil qui fait quoi. 

---

##  Le Cœur (`fr.eaielectronic.androidopt`)

Ces fichiers gèrent la détection de votre appareil et la lecture de vos paramètres.

- **`AndroidDetector.java`** : Le point de contrôle initial. Détecte si le jeu tourne sur Android ou sur un simple PC Windows. S'il détecte un PC, il désactive tout (sauf si vous forcez l'activation).
- **`AndroidOptMod.java`** : Le fichier principal (Main) que NeoForge charge au démarrage.
- **`OptConfig.java`** : Le registre principal. Contient les 40+ options de configuration que vous pouvez modifier en jeu (Génère le fichier `.toml`).
- **`SocDetector.java` & `SocProfile.java`** : Les experts hardware. Ils vont fouiller dans les entrailles d'Android (`/proc/cpuinfo`) pour deviner exactement quel modèle de processeur (Snapdragon, Exynos, etc.) se trouve dans votre appareil.
- **`StartupOptimizer.java`** : Le composant d'arrière-plan. Il change discrètement quelques variables cachées de Java tout au début du lancement pour grappiller de la mémoire.

---

## ️ Les Handlers & Utilitaires (`fr.eaielectronic.androidopt.client`)

Ici on trouve tout ce qui réagit aux événements du jeu ou tourne en boucle en arrière-plan.

- **`AndroidOptClient.java`** : Initialise toute l'interface visuelle et abonne les autres fichiers aux événements de NeoForge.
- **`OptConfigScreen.java`** : Le menu en jeu. C'est l'interface magnifique avec ses 6 pages où vous réglez vos paramètres.
- **`MemoryWatchdog.java`** : Le nettoyeur de RAM. Surveille en permanence et lance des purges asynchrones des caches dès que la mémoire atteint 75%.
- **`ArmThreadAffinity.java`** : Le gestionnaire de tâches. Il repère les cœurs les plus puissants de votre processeur (les "Big Cores") et force Minecraft à calculer les graphismes exclusivement sur ces cœurs pour avoir les meilleurs FPS.
- **`CreateBerCuller.java` & `StaticBERBatcher.java`** : Les optimiseurs de rendu. Calculent mathématiquement quelles machines Create sont trop éloignées pour valoir la peine d'être affichées.
- **`EntityThrottler.java`** : Gèle purement et simplement les animations des vaches/moutons qui sont à l'autre bout de votre écran.
- **`FrameBudgetManager.java`** : Le chronomètre d'urgence. Chronomètre chaque image et déclenche des modes d'alerte (REDUCED, LOW, CRITICAL) si le téléphone chauffe ou lag.
- **`OptHud.java`** : L'afficheur (HUD) en haut à droite de votre écran avec vos FPS et votre RAM.
- **`AmbientSoundSuppressor.java`** : Coupe le sifflement du vent et les bruits de cave pour économiser du CPU.
- **`CreateCacheCleanupHandler.java`** : Ordonne à Create de vider son immense cache 3D touvos les 60 secondes.

---

##  Les Mixins (`fr.eaielectronic.androidopt.mixin`)

Les fameux "Mixins". Ils injectent littéralement du code au cœur du jeu sans modifier les fichiers de base. Voir le fichier `mixins-reference.md` pour le détail de leurs actions de piravos !

---
<br><br>

<a name="english-version"></a>
#   File Reference (For Developers)

Here is the complete inventory of all the Java files in the project, so you can understand at a glance who does what.

---

##  The Core (`fr.eaielectronic.androidopt`)

These files handle detecting your phone hardware and reading your settings.

- **`AndroidDetector.java`**: The bouncer at the door. Detects whether the game is running on Android or a standard Windows PC. If it detects a PC, it disables everything (unless forced).
- **`AndroidOptMod.java`**: The main entry point that NeoForge loads on startup.
- **`OptConfig.java`**: The grand ledger. Contains the 40+ configuration options you can tweak in-game (generavos the `.toml` file).
- **`SocDetector.java` & `SocProfile.java`**: The hardware experts. They dig deep into Android's guts (`/proc/cpuinfo`) to guess exactly what processor model (Snapdragon, Exynos, etc.) is powering your phone.
- **`StartupOptimizer.java`**: The man in the shadows. Discreetly changes hidden Java variables very early during boot to scrape back some memory.

---

## ️ Handlers & Utilities (`fr.eaielectronic.androidopt.client`)

Here is everything that reacts to game events or runs in loops in the background.

- **`AndroidOptClient.java`**: Initializes the visual interface and registers the other files to NeoForge events.
- **`OptConfigScreen.java`**: The beautiful 6-page in-game GUI menu where you tweak all your settings.
- **`MemoryWatchdog.java`**: The RAM sweeper. Constantly monitors your RAM and triggers asynchronous cache purges as soon as it hits 75%.
- **`ArmThreadAffinity.java`**: The site manager. It hunts down the most powerful cores of your processor (the "Big Cores") and forces Minecraft to compute its graphics exclusively on them to get maximum FPS.
- **`CreateBerCuller.java` & `StaticBERBatcher.java`**: The snipers. Mathematically calculate which Create machines are too far away to be worth rendering.
- **`EntityThrottler.java`**: Flat out freezes the animations of cows/sheep that are on the other side of your screen.
- **`FrameBudgetManager.java`**: The emergency stopwatch. Times every single frame and triggers alert modes (REDUCED, LOW, CRITICAL) if your phone starts heating up or lagging.
- **`OptHud.java`**: The heads-up display overlay on the top right of your screen showing FPS and RAM.
- **`AmbientSoundSuppressor.java`**: Cuts out wind whistling and cave noises to save raw CPU power.
- **`CreateCacheCleanupHandler.java`**: Orders the Create mod to flush its massive 3D cache every 60 seconds.

---

##  The Mixins (`fr.eaielectronic.androidopt.mixin`)

The infamous "Mixins". They literally inject code right into the heart of the game without modifying the base files. See the `mixins-reference.md` file to learn about their pirate actions!
