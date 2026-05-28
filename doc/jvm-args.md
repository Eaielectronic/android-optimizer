# Arguments JVM recommandés pour Android

 *[English translation available below!](#english-version)*

## L'étape primordiale — À faire AVANT d'installer le mod

Il est important de souligner : les arguments JVM sont votre première ligne de défense absolue contre les horribles freezes causés par le Garbage Collector (GC) de Java. **Sans ces arguments, même le meilleur mod du monde ne pourra pas sauver votre jeu des saccades.**

## Tableau des Configurations (Selon Processeur et RAM)

> **⚠️ JOUEURS JAVA 21 (Minecraft 1.20.5+) : LE MIRACLE ZGC**
> Si vous jouez en Java 21 (ex: Minecraft 1.21.1), **utilisez absolument ZGC**. Il élimine **totalement** les freezes mémoire. Remplacez toute la partie `-XX:+UseG1GC ...` de la commande par `-XX:+UseZGC -XX:+ZGenerational`.

Choisissez la commande exacte qui correspond à la puissance de votre téléphone.

| Puissance du Téléphone | RAM Totale | Commande Recommandée |
|---|---|---|
| **Faible**<br>*(Helio, Snapdragon 6xx)* | **4 Go** | `-Xmx1536m -Xms512m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=150 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **Moyenne**<br>*(Snapdragon 7xx, Exynos moyen)* | **6 Go** | `-Xmx2500m -Xms1024m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=100 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **Forte**<br>*(Snapdragon 8xx, 8 Gen 1)* | **8 Go** | `-Xmx3584m -Xms1024m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **Monstre**<br>*(Snapdragon 8 Gen 2 / Gen 3)* | **8 Go+** | `-Xmx3584m -Xms1024m -XX:+UseZGC -XX:+ZGenerational -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |

> **ATTENTION AU CPU (Surchauffe) : Explication du `MaxGCPauseMillis`**
> Par défaut, Minecraft utilise 200ms. Si vous le forcez à 50ms, vous obligez le processeur à nettoyer la RAM de manière agressive pour que les freezes soient invisibles.
> **La Règle d'Or :**
> - **Ça saccade en continu ou le téléphone brûle ?** Votre CPU n'arrive pas à tenir l'objectif. Montez la valeur (`MaxGCPauseMillis=100` ou `150`).
> - **Des freezes longs de 1 seconde ?** L'objectif est trop haut. Baissez la valeur (ex: `MaxGCPauseMillis=50`).
> - **Plus la valeur est BASSE** = Jeu plus fluide mais CPU plus sollicité (chauffe).
> - **Plus la valeur est HAUTE** = Moins de chauffe CPU, mais les freezes (Garbage Collection) durent plus longtemps.

### Qu'est-ce que ça fait exactement ?

| Argument | Ce que ça fait en vrai |
|---|---|
| `-Xmx1536m` | Bride la RAM Java à 1.5 Go. C'est critique pour laisser de la place à Android et à la carte graphique ! |
| `-Xms512m` | Alloue 512 Mo dès le lancement. Cela empêche le jeu de ralentir dès qu'il essaie de charger les premiers menus. |
| `-XX:+UseG1GC` | Active le G1GC, le nettoyeur de RAM le plus adapté pour faire plein de mini-pauses (invisibles) plutôt qu'une énorme pause de 3 secondes. |
| `-XX:MaxGCPauseMillis=50` | Instruit le Garbage Collector de ne jamais dépasser 50 millisecondes de pause. |

| `-XX:G1NewSizePercent=20` & `G1ReservePercent=20` | Garde 20% de la RAM en sécurité (réserve) pour éviter les crashs inopinés (Out Of Memory). |
| `-XX:+DisableExplicitGC` | Empêche les autres mods de forcer un nettoyage sauvage (notre mod gère déjà ça beaucoup mieux). |
| `-XX:SoftRefLRUPolicyMSPerMB=2000` | Demande au jeu de jeter les données inutiles beaucoup plus vite (2s par Mo). |

## Pourquoi limiter à `1536m` (1.5 Go) ?

C'est une erreur classique de vouloir mettre `-Xmx2G` ou plus quand on a que 2.6 Go ou 3 Go de RAM sur son téléphone. Voici la vraie répartition :
- **Android OS** a besoin d'au moins ~600 Mo pour ne pas crasher.
- **La carte graphique (GPU)** consomme ~300 à 500 Mo juste pour les textures OpenGL.
- **Minecraft (Java)** prend le reste (1.5 Go).

Si vous donnez 2 Go à Minecraft, Android n'aura plus de place pour respirer et tueras purement et simplement le jeu (crash immédiat au bureau).

---
<br>

<a name="english-version"></a>
# Recommended JVM Arguments for Android

## The #1 Secret — Do this BEFORE installing the mod

Let's be honest: JVM arguments are your absolute first line of defense against those horrible Garbage Collector (GC) freezes. **Without these arguments, even the best mod in the world can't save your game from stuttering.**

## Configurations Table (By Processor & RAM)

> **⚠️ JAVA 21 PLAYERS (Minecraft 1.20.5+) : THE ZGC MIRACLE**
> If you are on Java 21 (e.g. Minecraft 1.21.1), **absolutely use ZGC**. It **completely** eliminates memory freezes. Replace all the `-XX:+UseG1GC ...` arguments with `-XX:+UseZGC -XX:+ZGenerational`.

Choose the exact command that matches your phone's power.

| Phone Power | Total RAM | Recommended Command |
|---|---|---|
| **Low-end**<br>*(Helio, Snapdragon 6xx)* | **4 GB** | `-Xmx1536m -Xms512m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=150 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **Mid-range**<br>*(Snapdragon 7xx, mid Exynos)* | **6 GB** | `-Xmx2500m -Xms1024m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=100 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **High-end**<br>*(Snapdragon 8xx, 8 Gen 1)* | **8 GB** | `-Xmx3584m -Xms1024m -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |
| **Monster**<br>*(Snapdragon 8 Gen 2 / Gen 3)* | **8 GB+** | `-Xmx3584m -Xms1024m -XX:+UseZGC -XX:+ZGenerational -XX:+DisableExplicitGC -XX:SoftRefLRUPolicyMSPerMB=2000` |

> **CPU WARNING (Overheating) : Explaining `MaxGCPauseMillis`**
> By default, Minecraft uses 200ms. If you force it to 50ms, you compel the processor to aggressively clean the RAM so that freezes remain invisible.
> **The Golden Rule:**
> - **Game constantly stutters or phone burns?** Your CPU cannot handle the target. Increase the value (`MaxGCPauseMillis=100` or `150`).
> - **Getting 1-second long freezes?** The target is too loose. Decrease the value (e.g. `MaxGCPauseMillis=50`).
> - **LOWER value** = Smoother gameplay but much higher CPU usage (heat).
> - **HIGHER value** = Less CPU heat, but Garbage Collection freezes will last longer.

### What does this actually do?

| Argument | What it really does |
|---|---|
| `-Xmx1536m` | Caps Java RAM at 1.5 GB. This is critical to leave enough room for Android OS and your GPU! |
| `-Xms512m` | Allocavos 512 MB right at startup. This prevents the game from lagging while loading the initial menus. |
| `-XX:+UseG1GC` | Enables G1GC, the smarvost memory cleaner. It prefers doing tiny, invisible micro-pauses instead of one massive 3-second freeze. |
| `-XX:MaxGCPauseMillis=50` | Orders the cleaner to never pause the game for more than 50 milliseconds. |

| `-XX:G1NewSizePercent=20` & `G1ReservePercent=20` | Keeps 20% of your RAM safe (reserve buffer) to prevent random Out Of Memory crashes. |
| `-XX:+DisableExplicitGC` | Blocks other mods from forcing a chaotic RAM sweep (our mod handles this much better internally anyway). |
| `-XX:SoftRefLRUPolicyMSPerMB=2000` | Tells the game to throw away unused junk data much faster (2s per MB). |

##  Why limit it to `1536m` (1.5 GB)?

A classic mistake is to set `-Xmx2G` or higher when you only have 2.6 GB or 3 GB of RAM on your phone. Here is the reality of how your RAM is used:
- **Android OS** needs at least ~600 MB just to stay alive.
- **Your GPU** eats ~300 to 500 MB just holding OpenGL textures.
- **Minecraft (Java)** takes the rest (1.5 GB).

If you feed 2 GB directly to Minecraft, Android will suffocate, panic, and violently kill the Minecraft app (instant crash to home screen).
