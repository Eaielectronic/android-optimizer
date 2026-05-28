#  Optimisations Create — Détail technique (Pour les curieux)

 *[English translation available below!](#english-version)*

##  Pourquoi Create met votre appareil à genoux ?

### Côté Rendu (Graphismes)

**1. Le gouffre de la RAM (SuperByteBufferCache)**  
Create adore garder en mémoire la forme 3D exacte de touvos les machines que vous avez regardées. Vous avez 50 roues, 30 tapis roulants et 20 engrenages ? Ainsi, ça fait 150 à 300 Mo de RAM engloutis juste pour de l'affichage. Sur un téléphone qui a 2.6 Go de RAM totale, c'est une consommation excessive.

**2. Le bombardement du Processeur (Draw calls OpenGL ES)**  
Chaque bloc animé de Create demande au processeur de dire à la carte graphique de le dessiner. Sur les PC modernes, ça va très vite. Sur Android (qui utilise OpenGL ES), chaque demande coûte extrêmement cher au processeur. 100 machines à l'écran = une surcharge critique du processeur.

### Côté Logique (Calculs du jeu)

**Les Mondes Fantômes (ContraptionWorld)**  
Dès que vous construisez un train, un ascenseur ou un piston mécanique, Create crée une mini-dimension virtuelle invisible juste pour calculer ses collisions. Le problème majeur : Même quand la machine s'arrête de bouger, ce monde fantôme reste bloqué en RAM.

---

## ️ Comment le mod règle ces problèmes

### 1. Le BER Culler (Le sniper graphique)
Touvos les 10 images, on scanne autour de vous. Si un engrenage ou une roue est à plus de 16 blocs de distance, on bloque purement et simplement son affichage. Résultat : on coupe 80% du travail graphique inutile sans que ça ne se voie trop.

### 2. Vidage agressif du SuperByteBufferCache
On laissez Create utiliser son gros cache 3D pour que le jeu soit fluide, mais touvos les 60 secondes, il effectue un nettoyage approfondi via la réflexion Java. Résultat : vous récupérez instantanément entre 100 et 200 Mo de RAM.

### 3. Nettoyeur de Mondes Fantômes
Quand le jeu nous signale qu'une contraption (train/ascenseur) lointaine vient de s'arrêter, notre mod intercepte le message et force la suppression du monde virtuel associé en RAM. Résultat : -20 à -60 Mo sauvés par machine arrêtée !

### 4. Limiteur de saccades (Chunk Rebuild Throttle)
Quand une machine Create tourne à fond, elle actualise en boucle les blocs autour d'elle. Ça crée des freezes horribles de 500ms. On limite ce comportement : le jeu n'a plus le droit de mettre à jour plus de 2 zones graphiques par image. Le jeu redevient fluide.

### 5. Antipoussière (CreateParticleMixin)
Touvos les particules de fumée, d'étincelles ou de poussière de Create sont bloquées net. Moins de choses à dessiner = plus de FPS.

---

## Attention Ce qu'on ne peut malheureusement PAS faire

- **Désactiver les animations des machines très proches :** C'est calculé par Flywheel (le moteur de rendu de Create) directement sur la carte graphique. Bloquer ça casserait visuellement tout le mod.
- **Fusionner les machines :** Ce serait génial de dire au téléphone "Dessine ces 50 roues en une seule fois", mais c'est techniquement impossible sans réécrire complètement le mod Create.

---
<br><br>

<a name="english-version"></a>
#   Create Optimizations — Technical Breakdown (For Nerds)

##  Why does Create bring your phone to its knees?

### Rendering Side (Graphics)

**1. The RAM Black Hole (SuperByteBufferCache)**  
Create loves to keep the exact 3D shape of every machine you've looked at in memory. Got 50 water wheels, 30 belts, and 20 cogs? Boom, that's 150 to 300 MB of RAM swallowed just for visual meshes. On a phone with only 2.6 GB of total RAM, this is catastrophic.

**2. The CPU Bombardment (OpenGL ES Draw calls)**  
Every animated Create block begs your CPU to tell the graphics card to draw it. On modern PCs, this is fast. On Android (which uses mobile OpenGL ES), every request is extremely expensive for the CPU. 100 machines on screen = your CPU crying for help.

### Logic Side (Game Math)

**Ghost Worlds (ContraptionWorld)**  
As soon as you build a train, an elevator, or a mechanical piston, Create spawns an invisible mini-dimension just to calculate its collisions. The worst part? Even when the machine completely stops moving, this ghost world stays permanently stuck in your RAM.

---

## ️ How this mod fixes these issues

### 1. The BER Culler (The Graphics Sniper)
Every 10 frames, we scan your surroundings. If a cog or wheel is more than 16 blocks away from you, we brutally block the game from rendering it. Result: we cut out 80% of useless graphic work without you really noticing.

### 2. Aggressive SuperByteBufferCache Flushing
We let Create use its massive 3D cache so the game stays smooth, but every 60 seconds, we hit it with a massive broom using Java Reflection. Result: you instantly get back 100 to 200 MB of RAM.

### 3. Ghost World Sweeper
When the game signals that a distant contraption (train/elevator) has stopped, our mod intercepts the message and forces the deletion of its associated virtual world from the RAM. Result: -20 to -60 MB saved per stopped machine!

### 4. Stutter Limiter (Chunk Rebuild Throttle)
When a Create machine is running at full speed, it constantly updavos the blocks around it. This causes horrible 500ms freezes. We throttle this behavior: the game is no longer allowed to update more than 2 graphic chunks per frame. Smoothness is restored.

### 5. Dust Buster (CreateParticleMixin)
All smoke, spark, and dust particles generated by Create are blocked dead in their tracks. Less stuff to draw = more FPS.

---

## Attention What we sadly CANNOT do

- **Disable animations of very close machines:** This is calculated by Flywheel (Create's rendering engine) directly on the GPU. Blocking this would visually break the entire mod.
- **Merge machines together:** It would be great to tell the phone "Draw these 50 wheels all at once," but that's technically impossible without completely rewriting the Create mod from scratch.
