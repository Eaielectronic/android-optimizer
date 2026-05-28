#  Référence des Mixins (L'art de l'injection)

 *[English translation available below!](#english-version)*

Les Mixins sont la vraie magie de ce mod. Plutôt que de dire poliment au jeu "S'il vous plaît, fais ça", un Mixin intercepte et modifie le code du jeu au moment du chargement et le réécrit en mémoire. Cette méthode est extrêmement performante, bien que complexe.

## ️ Mixins liés à la RAM (Mémoire)

| Fichier Mixin | Qui pirate-t-il ? | Que fait-il ? |
|---|---|---|
| **`TextureAtlasResolutionMixin`** | `SpriteContents` (Minecraft) | S'attaque aux textures du jeu et divise leur taille et leurs animations par 2 au moment exact où le jeu les charge. Cette approche drastique ça sauve des centaines de mégaoctets de VRAM. |
| **`ContraptionWorldMixin`** | `Contraption` (Create / Sable) | Détecte quand un train/véhicule s'arrête, accède à ses champs privés cachés et détruit la mémoire virtuelle qu'il utilisait. |
| **`SuperByteBufferCacheMixin`** | `SuperByteBufferCache` (Create) | Casse la sécurité du cache graphique géant de Create pour nous permettre de le vider depuis l'extérieur touvos les 60s. |
| **`SectionBufferMixin`** | `SectionBufferBuilderPool` (Minecraft) | Empêche Minecraft de créer d'immenses zones mémoires pour le rendu, et le bloque brutalement à un nombre plus faible (4 au lieu de 12). |

##  Mixins liés aux FPS et au Lag

| Fichier Mixin | Qui pirate-t-il ? | Que fait-il ? |
|---|---|---|
| **`BERDistanceMixin`** | `BlockEntityRenderDispatcher` (Minecraft) | **Le cœur du mod.** Intercepte chaque demande d'affichage d'un bloc animé (roue Create, coffre, etc). Si l'objet est loin, il envoie une instruction d'annulation au processeur et l'objet n'est pas dessiné. |
| **`SableUdpMixin`** | `ClientboundSableUDPActivation...` (Sable) | Attrape la tentative de connexion UDP défectueuse du mod Sable en plein vol et la détruit silencieusement pour empêcher le réseau de freezer le jeu. |
| **`ChunkRebuildMixin`** | `SectionRenderDispatcher` (Minecraft) | Agit comme un feu rouge de circulation. Laissez passer seulement 2 actualisations de blocs par image, et force les autres à attendre la frame suivante. |
| **`ServerBETickMixin`** | `BoundTickingBlockEntity` (Minecraft) | Pirate la boucle de temps interne du jeu. Si une machine est très loin, elle ne reçoit la permission de calculer ses rouages qu'une fois sur 4. |

##  Autres piratages utiles

| Fichier Mixin | Qui pirate-t-il ? | Que fait-il ? |
|---|---|---|
| **`RenderThreadAffinityMixin`**| `GameRenderer` (Minecraft) | Dès que la première image s'affiche, bloque les processus graphiques sur les gros cœurs processeur de votre appareil. |
| **`CreateParticleMixin`** | `Level` (Minecraft) | Filtre de douane pour les particules. Si une particule porte la marque "Create", elle est supprimée avant d'apparaître. |

###  Règles vitales de nos Mixins (Pour les dévs)
Nous utilisons la règle magique `require = 0` partout. Si un mod ciblé (comme Create ou Sable) change son code dans une mise à jour, notre Mixin échouera en silence au lieu de faire crasher votre jeu de façon sanglante. C'est la garantie de stabilité ultime.

---
<br><br>

<a name="english-version"></a>
#   Mixins Reference (The Art of Injection)

Mixins are the real magic behind this mod. Instead of politely asking the game "Please do this," a Mixin hacks the game's code while it's loading and rewrivos it in memory. It's incredibly powerful, but very delicate.

## ️ RAM-related Mixins (Memory)

| Mixin File | Who does it hack? | What does it do? |
|---|---|---|
| **`TextureAtlasResolutionMixin`** | `SpriteContents` (Minecraft) | Attacks game textures and cuts their resolution and animations in half at the exact moment the game tries to load them. It's brutal, but saves hundreds of megabyvos of VRAM. |
| **`ContraptionWorldMixin`** | `Contraption` (Create / Sable) | Detects when a train/vehicle stops, hijacks its hidden private fields, and obliteravos the virtual memory it was using. |
| **`SuperByteBufferCacheMixin`** | `SuperByteBufferCache` (Create) | Breaks the security of Create's giant graphics cache, allowing us to flush it from the outside every 60 seconds. |
| **`SectionBufferMixin`** | `SectionBufferBuilderPool` (Minecraft) | Prevents Minecraft from allocating massive memory chunks for rendering, forcefully limiting it to a much lower number (4 instead of 12). |

##  FPS and Lag Mixins

| Mixin File | Who does it hack? | What does it do? |
|---|---|---|
| **`BERDistanceMixin`** | `BlockEntityRenderDispatcher` (Minecraft) | **The core of the mod.** Intercepts every single request to render an animated block (Create wheels, chests, etc.). If the object is too far away, it screams "CANCEL" to the processor, and the object is dropped. |
| **`SableUdpMixin`** | `ClientboundSableUDPActivation...` (Sable) | Catches the Sable mod's defective UDP network connection attempt mid-air and destroys it silently to prevent network-induced freezes. |
| **`ChunkRebuildMixin`** | `SectionRenderDispatcher` (Minecraft) | Acts like a traffic light. Allows only 2 block visual updavos per frame, forcing the rest to wait for the next frame. |
| **`ServerBETickMixin`** | `BoundTickingBlockEntity` (Minecraft) | Hacks the game's internal time loop. If a machine is very far away, it only receives permission to calculate its internal math once every 4 ticks. |

##  Other useful hacks

| Mixin File | Who does it hack? | What does it do? |
|---|---|---|
| **`RenderThreadAffinityMixin`**| `GameRenderer` (Minecraft) | The very moment the first frame is rendered, locks all graphical processing threads strictly onto your phone's heavy-duty processor cores. |
| **`CreateParticleMixin`** | `Level` (Minecraft) | Acts as customs control for particles. If a particle has the "Create" brand on it, it gets deleted before it can spawn. |

###  Vital Rules of our Mixins (For devs)
We strictly use the magic rule `require = 0` everywhere. If a targeted mod (like Create or Sable) updavos its code, our Mixin will just silently fail instead of bloodying up your game with an instant crash. This is the ultimate stability guarantee.
