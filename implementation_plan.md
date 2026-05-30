# Plan Directeur : Optimisation Extrême "Zero-GC & Off-Heap ECS"

L'objectif de ce plan est de contourner les limites physiques d'un appareil Android (3000 Mo de RAM, CPU mobile) pour faire tourner des modpacks industriels massifs (ex: Create, Electrodynamics).

Suite aux recherches approfondies sur les techniques de pointe utilisées dans les moteurs de jeux AAA et le trading haute-fréquence en Java, nous abandonnons le stockage C++ (JNI Overhead trop lourd) et la simple déduplication (déjà faite par FerriteCore). 

Nous passons à une architecture **Data-Oriented Design (DOD)** et **Allocation-Free** directement en Java "bas niveau".

> [!WARNING]
> Ce plan requiert une refonte profonde de la manière dont les données des mods sont interceptées. Il ne s'agit pas de patcher des bugs, mais de réécrire la structure en mémoire des données de Create à la volée via des Mixins.

## User Review Required

Ce plan propose d'utiliser `sun.misc.Unsafe` ou les `DirectByteBuffer` pour stocker les données des mods. Es-tu prêt à manipuler la mémoire brute (pointeurs, offsets) directement en Java ? Une erreur ici provoque un crash JVM pur et dur (Segfault), pas une simple Exception Java.

## Open Questions

- Souhaites-tu utiliser l'API officielle `java.nio.ByteBuffer.allocateDirect()` (plus sûre) ou la classe secrète `sun.misc.Unsafe` (accès brut aux pointeurs, plus rapide mais risqué) pour l'allocation Off-Heap ?
- Devons-nous cibler spécifiquement les `KineticBlockEntity` du mod Create en priorité pour la Preuve de Concept (PoC) ?

## Proposed Changes

### 1. Architecture "Data-Oriented" (Off-Heap ECS)
Les mods stockent actuellement leurs données en "Array of Structures" (AoS) avec de lourds objets Java :
`[Objet(X,Y,Z,Vitesse), Objet(X,Y,Z,Vitesse), ...]`

Nous allons intercepter la création de ces objets et les stocker en "Structure of Arrays" (SoA) dans la mémoire native (Off-Heap) invisible pour le Garbage Collector :
`DirectByteBuffer = [X,X,X... Y,Y,Y... Z,Z,Z... Vitesse,Vitesse,Vitesse...]`

#### [NEW] `src/main/java/fr/eaielectronic/androidopt/memory/OffHeapKineticArena.java`
- Gestionnaire de mémoire utilisant `ByteBuffer.allocateDirect()`.
- Un seul bloc de 20 Mo peut contenir l'état mathématique de 100 000 engrenages.
- Le Garbage Collector Java voit `1` objet (le Buffer) au lieu de `100 000` objets (les entités).

### 2. Programmation "Allocation-Free" (Zero-GC Tick)
Le GC Freeze survient parce que le jeu crée des objets temporaires (Vecteurs, BlockPos, Iterators) 60 fois par seconde pendant sa boucle de mise à jour (`tick()`).

#### [NEW] `src/main/java/fr/eaielectronic/androidopt/memory/ObjectPools.java`
- Implémentation de pools d'objets (Object Pooling) ou utilisation exclusive de variables primitives (`int`, `float`) dans les "hot paths" (les boucles critiques).
- Remplacement des `Iterator` (qui allouent de la mémoire) par des boucles `for` classiques sur tableaux primitifs.

#### [MODIFY] `src/main/resources/androidopt.mixins.json`
- Nouveaux Mixins ciblés sur les méthodes `tick()` des réseaux cinétiques de Create.
- But : Empêcher la création d'objets `new BlockPos()` ou `new Vec3()` pendant la simulation physique. Utilisation de variables mutables partagées via `ThreadLocal`.

### 3. Rendu Instancié Direct (Direct VRAM Upload)
L'avantage massif du `DirectByteBuffer` est qu'il est déjà formaté en mémoire native.

#### [MODIFY] `native-gl-engine/src/main/java/fr/eaielectronic/nativeglengine/NativeBufferManager.java`
- Modification pour passer directement le pointeur mémoire (l'adresse physique) du `OffHeapKineticArena` aux fonctions OpenGL natives (`glBufferData`).
- Zéro copie de tableau (Zero-Copy) : Les positions des engrenages vont de l'Off-Heap Java directement à la carte graphique sans passer par le tas Java.

## Verification Plan

### Automated Tests
- Créer un monde avec 20 000 engrenages Create (Schématic massif).
- Connecter JVisualVM ou Eclipse MAT au processus Java.
- Vérification 1 : La mémoire "Heap" (Tas) Java doit rester sous les 300 Mo alloués aux objets temporaires, avec une courbe plate.
- Vérification 2 : La mémoire "Off-Heap" / "Direct Buffer" doit afficher l'allocation de l'arène.

### Manual Verification
- Jouer dans le monde rempli d'engrenages pendant 30 minutes sur le téléphone Android.
- Observer l'absence totale de "Micro-Stutters" (GC Pauses). Le jeu peut tourner à un framerate bas si le GPU peine, mais il ne doit **jamais** "geler" pendant 0.5s comme avant.
