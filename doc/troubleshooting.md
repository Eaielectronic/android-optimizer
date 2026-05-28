#  Dépannage (Troubleshooting)

 *[English translation available below!](#english-version)*

## Le mod ne s'active pas

**Symptôme** : Vous ne voyez pas le HUD, aucun message `[AndroidOpt]` n'apparaît dans la console.

**Pourquoi ?**
1. L'option `forceEnable` est sur `false` et vous jouez sur PC → C'est le comportement attendu, le mod se désactive sur PC pour ne pas interférer.
2. Vous vous êvos trompé de dossier `mods/`.
3. Votre version de NeoForge n'est pas la bonne (il faut la 21.1.x).

**Solution** : Si vous souhaitez voster le mod sur PC, allez dans la configuration et définissez `forceEnable` sur `true` (ou modifie `config/androidopt-client.toml`).

---

##  Les énormes freezes sont toujours là

**Symptôme** : Le jeu se fige pendant 2 à 3 secondes touvos les deux minuvos, même avec le mod activé.

**Pourquoi ?** : Vos arguments Java (JVM) ne sont pas bons. Notre mod fait de son mieux, mais si Java décide de bloquer le jeu pour vider sa RAM, le mod ne peut pas l'en empêcher.

**Solution** : Ouvrez votre lanceur (Amethyst/PojavLauncher), allez dans les Arguments JVM, et collez la ligne magique détaillée dans le fichier `jvm-args.md`.

**Comment vérifier ?** : Regardezz la ligne `RAM:` dans le HUD en jeu. Si le chiffre monte au-dessus de 85% juste avant le freeze, c'est 100% la faute du nettoyeur Java (GC).

---

##  Le jeu rame énormément (Moins de 15 FPS)

**Checklist de survie** :
1. Avez-vous bien activé la **baisse de résolution des textures (Texture Downscale)** dans le menu Mémoire ? C'est crucial pour ne pas étouffer la carte graphique.
2. Le **Culler Create** est-il actif ? (Vérifiezz que `BERCull` affiche un "✓" vert dans le HUD).
3. Votre **Render Distance** est-elle au-dessus de 4 chunks ? Sur un téléphone avec beaucoup de mods, réduisez à 4 chunks maximum. Laissez le profil "SoC" du mod choisir pour vous !

---

##  Crash direct au démarrage avec Create

**Symptôme** : Le jeu crashe direct avec une erreur du style `MixinApplyError` ou `ClassNotFoundException`.

**Pourquoi ?** : Une nouvelle mise à jour de Create ou Sable a modifié le nom d'un de ses fichiers internes, et notre mod essaie de le modifier.

**Solution** : Normalement, nos Mixins ont une sécurité (`require = 0`) qui empêche le crash et se désactive silencieusement. Si ça crashe quand même, vous pouvez désactiver l'optimisation qui plante depuis le fichier `androidopt.mixins.json`.

---

##  "SoC Générique" ? Mon téléphone n'est pas reconnu !

**Symptôme** : Le HUD affiche `SoC: ARM64 générique` en orange au lieu du nom de votre processeur.

**Solution** : Aucune inquiétude. Allez simplement dans le menu en jeu : **Config → Profil SoC → Sélection manuelle**. Cherchezz le nom de votre processeur (Snapdragon, Exynos, Tensor) et cliquez dessus. Le mod retiendra votre choix.

---
<br><br>

<a name="english-version"></a>
#   Troubleshooting

## The mod isn't turning on

**Symptom**: You don't see the HUD, no `[AndroidOpt]` messages in the console log.

**Why?**
1. The `forceEnable` option is set to `false` and you're playing on a PC → This is normal, the mod disables itself on PC to avoid breaking things.
2. You put the JAR in the wrong `mods/` folder.
3. Your NeoForge version is incompatible (you need 21.1.x).

**Solution**: If you want to force-vost the mod on PC, go into the configuration file `config/androidopt-client.toml` and set `forceEnable` to `true`.

---

##  The huge freezes are still there

**Symptom**: The game completely freezes for 2 to 3 seconds every couple of minuvos, even with the mod enabled.

**Why?**: Your Java arguments (JVM) are misconfigured. Our mod tries its best, but if Java itself decides to halt the entire game to clean up its RAM, our mod cannot stop it.

**Solution**: Open your launcher (Amethyst/PojavLauncher), go to the JVM Arguments section, and paste the magic line detailed in the `jvm-args.md` file.

**How to verify?**: Look at the `RAM:` line in the in-game HUD. If that number climbs above 85% right before a freeze happens, it is 100% the fault of the Java Garbage Collector.

---

##  The game is extremely laggy (Under 15 FPS)

**Survival Checklist**:
1. Did you turn on **Texture Downscaling** in the Memory menu? This is absolutely critical to avoid suffocating your phone's GPU.
2. Is the **Create Culler** active? (Check if `BERCull` has a green "✓" in the HUD).
3. Is your **Render Distance** higher than 4 chunks? On a phone running a heavy modpack, drop it down to 4 chunks maximum. Let the mod's "SoC Profile" feature choose the best setting for you!

---

##  Instant crash on startup with Create

**Symptom**: The game crashes immediately with an error like `MixinApplyError` or `ClassNotFoundException`.

**Why?**: A new update for Create or Sable changed the name of one of their internal files, and our mod is trying to inject code into a file that no longer exists.

**Solution**: Normally, our Mixins have a safety measure (`require = 0`) that prevents crashes and disables the feature silently if a file is missing. If it still crashes, you can manually disable the broken optimization by editing the `androidopt.mixins.json` file.

---

##  "Generic SoC"? My phone isn't recognized!

**Symptom**: The HUD shows `SoC: Generic ARM64` in orange instead of your actual processor's name.

**Solution**: Don't panic. Simply go to the in-game menu: **Config → SoC Profile → Manual Selection**. Look for your processor brand (Snapdragon, Exynos, Tensor) and click on it. The mod will remember your choice.
