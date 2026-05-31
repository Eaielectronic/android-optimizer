# PLAN V5 (DÉFINITIF) : Vulkan, Agrona SoA et C++ NDK

Suite à un audit technique rigoureux, les anciennes architectures (OpenGL 4.6, FFM API Preview, LMAX Disruptor) ont été corrigées ou abandonnées car elles se heurtaient aux limites matérielles d'Android (PojavLauncher) et de l'écosystème Forge. 

Voici le plan définitif, réaliste et concentré **uniquement sur les vraies innovations** qui n'existent dans aucun autre mod.

---

## 1. LES FAUSSES BONNES IDÉES (Déjà fait par d'autres mods)
> [!WARNING]
> Nous n'allons pas réinventer la roue. Ces optimisations sont vitales, mais elles sont déjà parfaitement implémentées par la communauté. Notre mod se contentera de recommander leur installation.

*   **Collections Primitives (fastutil) & Déduplication :** Déjà fait par **FerriteCore**. Il réduit la RAM de manière drastique sur les `BlockStates` et modèles.
*   **Object Pooling (`MutableBlockPos`) :** Déjà fait par **Lithium** (depuis la v0.6). Faire notre propre pool créerait des deadlocks.
*   **Lazy Baking (Virtual Geometry) :** Déjà fait par **ModernFix** via son flag `dynamic_resources`. Il décharge les modèles 3D non utilisés et sauve ~300 Mo.
*   **LMAX Disruptor pour le Tick Loop :** **[ABANDONNÉ]** Rendre le tick asynchrone casse les invariants de Forge et détruirait des mods comme Create ou AE2.

---

## 2. LES VRAIES INNOVATIONS (Notre plan d'attaque C++ / Java)
> [!IMPORTANT]
> Voici les véritables optimisations "Hors-Sol" et matérielles que nous allons coder. Personne ne l'a fait sur Minecraft Java, encore moins sur Android.

### 2.1. Agrona DirectBuffer (Struct of Arrays)
*   **Le Problème :** L'API FFM nécessite `--enable-preview` sur Java 21, ce que PojavLauncher ne gère pas par défaut.
*   **La Solution :** Utiliser **Agrona**. C'est du Java 17+ stable, basé sur `Unsafe`.
*   **L'Implémentation :** Nous allons transformer les `KineticBlockEntity` de Create en *Struct of Arrays* (SoA) stockées dans des buffers Agrona Off-Heap. Cela détruit l'overhead des objets Java et rend les données invisibles pour le Garbage Collector.

### 2.2. Compression LZ4 via C++ (ZRAM d'Application)
*   **Le Problème :** Même avec Agrona, 55 mods vont saturer les 2.5 Go de RAM physique.
*   **La Solution :** Compresser les réseaux Create "inactifs" en RAM.
*   **L'Implémentation :** On passe les buffers Agrona au C++ via JNI. Le C++ utilise l'algorithme LZ4 pour diviser la taille par 3 en quelques millisecondes, puis stocke le résultat dans un buffer Off-Heap compressé. Le *JNI Overhead* est nul car on ne le fait que sur des machines inactives, pas dans le hot path.

### 2.3. C++ NDK : Les optimisations exclusives Mobile
L'Android NDK nous permet d'accéder au matériel bas niveau du téléphone, ce que Java ne peut pas faire.
*   **Thread Affinity sur P-Cores :** Le noyau Linux d'Android bascule souvent les threads Minecraft sur les cœurs "Efficiency" (lents). En 20 lignes de code C++ NDK, nous allons forcer le Thread Serveur (Tick Loop) et le Thread de Rendu sur les "Performance Cores" du Snapdragon. **Gain immédiat de TPS.**
*   **SIMD NEON pour le NBT :** Récrire la lecture des fichiers de sauvegarde NBT en C++ en utilisant les instructions vectorielles ARM NEON. Le chargement des mondes sera instantané.
*   **Thermal Detector :** Coder un pont C++ qui lit la température réelle du SoC (batterie/CPU). Si le téléphone surchauffe, notre mod baisse dynamiquement le render distance ou ralentit les ticks de Create pour éviter le *Thermal Throttling* massif.

### 2.4. Vulkan Compute (Le Rendu Ultime)
*   **Le Problème :** OpenGL 4.6 est impossible sur Android. PojavLauncher émule au mieux OpenGL 4.5 via Zink (Turnip/Adreno) ou 3.1 sur Mali. Les fonctions avancées d'OpenGL seraient bridées ou planteraient.
*   **La Solution :** Cibler **Vulkan**. C'est l'API native et moderne d'Android.
*   **L'Implémentation :** C'est le "Boss Final". Nous allons créer un backend de rendu natif en Vulkan spécifiquement pour afficher nos données Agrona (les usines Create). Le GPU lira directement les données Off-Heap.

---

## 3. PROCHAINES ÉTAPES (Exécution)

1. **Nettoyage du code existant :** S'assurer qu'aucun code expérimental C++ ou FFM des plans précédents ne reste dans le projet.
2. **Setup d'Agrona :** Intégrer Agrona au `build.gradle` (sans conflit avec Forge).
3. **Implémentation C++ NDK (La plus facile) :** Commencer par coder le **Thread Affinity** pour binder le serveur sur les P-Cores. C'est le gain de performance le plus rapide à obtenir.
