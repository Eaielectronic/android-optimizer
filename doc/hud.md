#  Le HUD (Interface In-Game) : Comment le lire

 *[English translation available below!](#english-version)*

Le mod affiche un panneau d'informations en haut à droite de votre écran (le "HUD"). C'est un tableau de bord exhaustif pour savoir exactement ce que fait votre appareil. Il se masque automatiquement quand vous ouvrez le menu F3 classique de Minecraft.

## Exemple d'affichage en jeu

```text
[AndroidOpt]
FPS: 24
RAM: 1420/1536Mo
Budget: REDUCED
Mode: Android
SoC: Snapdragon 865
BER: -47 BE: -12
──────────────
✓Rendu ✓Clouds
✓Ptcl  ✓AO
✓Météo ✓Tex/2
✓GC    ✓TexEvict
✓BufLim ✓Chunks
✓Entity ✓BESkip
✓BERCull ✓Rebuild
✓CrPtcl ✓CleanRAM
✓Sound  ✓SableFix
```

## Décryptage : Que veulent dire ces lignes ?

| Ligne | Ce que ça signifie en vrai |
|---|---|
| `FPS: 24` | Votre fluidité. Vert = jouable (≥25), Orange = limite (≥15), Rouge = injouable (<15). |
| `RAM: 1420/1536Mo` | Votre mémoire. L'Optimizer s'assurera de la vider avant qu'elle n'atteigne 100% pour éviter le freeze mortel. |
| `Budget: REDUCED` | Le niveau d'alerte actuel de votre appareil (Voir "Les Niveaux d'Alerte" plus bas). |
| `Mode: Android` | Dit si le mod a bien capté qu'il tourne sur mobile. S'il dit "PC forcé", c'est que vous avez forcé le mod sur ordi. |
| `SoC: Snapdragon` | Le processeur de votre appareil. S'il est écrit en vert, c'est que le mod l'a reconnu et s'est adapté. |
| `BER: -47` | Le Culler : Cela signifie que **47** machines Create ont été effacées de votre écran parce qu'elles étaient trop loin. |

### Les Niveaux d'Alerte (Frame Budget)

L'optimizer surveille la température et le lag de votre appareil en temps réel :
- ** NORMAL** : Tout va bien, le jeu affiche tout.
- ** REDUCED** (FPS sous les 30) : Le téléphone chauffe, on commence à zapper les machines lointaines.
- ** LOW** (FPS sous les 20) : On gèle l'animation des animaux lointains et on stoppe les véhicules arrêtés.
- ** CRITICAL** (Gros lag sous les 10 FPS) : Coupure d'urgence, on diminue votre distance de rendu d'un chunk temporairement pour survivre.

## Comment cacher le HUD ?
Dans le jeu : **Menu Config → Rendu & FPS → Afficher HUD → [OFF]**.

---
<br><br>

<a name="english-version"></a>
#   The HUD (In-Game Overlay): How to read it

The mod displays an information panel in the top right corner of your screen (the "HUD"). Think of it as a complete dashboard to know exactly what your phone is struggling with. It automatically hides itself when you open the classic F3 debug menu.

## Decoding the dashboard

| Line | What it actually means |
|---|---|
| `FPS: 24` | Your frames per second. Green = playable (≥25), Orange = borderline (≥15), Red = slideshow (<15). |
| `RAM: 1420/1536Mo` | Your memory. The Optimizer will aggressively try to flush it before it hits 100% to avoid death-freezes. |
| `Budget: REDUCED` | Your phone's current stress level (See "Stress Levels" below). |
| `Mode: Android` | Confirms if the mod detected your mobile. If it says "Forced PC", you're forcing mobile optimizations on your desktop. |
| `SoC: Snapdragon` | Your phone's brain. If it's green, the mod recognized it and auto-configured itself. |
| `BER: -47` | The Culler (Sniper)! This means **47** Create machines were completely wiped from your screen because they were too far away. |

### Stress Levels (Frame Budget)

The optimizer monitors your phone's lag in real-time:
- ** NORMAL**: Everything is fine, rendering is normal.
- ** REDUCED** (Under 30 FPS): The phone is struggling, we start cutting off distant machines.
- ** LOW** (Under 20 FPS): We freeze distant animal animations and stop rendering parked vehicles.
- ** CRITICAL** (Huge lag under 10 FPS): Emergency cut, we temporarily lower your render distance by 1 chunk to survive.

## How to hide the HUD?
In-game: **Config Menu → Render & FPS → Show HUD → [OFF]**.
