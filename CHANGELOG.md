# Changelog

## [1.0.5] - 2026-06-01

### Added
- **NativeGL Engine Integration** : Intégration du moteur C++ `NativeGLEngine` et détection de présence.
- **Off-Heap Arena (C++)** : Implémentation d'une arène mémoire Off-Heap pour réduire la pression sur le GC.
- **Lazy JEI Search Indexing** : Indexation paresseuse des items en arrière-plan pour éviter les freezes au démarrage (gain RAM massif).
- **Lazy Unicode Font Paging** : Chargement à la volée des pages de caractères (Unicode) pour économiser la VRAM.
- **LRU-LFU 3D Model Eviction** : Éviction dynamique des modèles 3D complexes non vus récemment avec un fallback "FlatSprite" (quad 2D) (gain RAM très significatif).
- **Distant NBT Off-Heap Compression** : Compression LZ4 et stockage Off-Heap des NBT d'entités inactives/distantes.
- **Agrona KineticSoABuffer** : Remplacement des objets Java lourds de Create par une architecture SoA Off-Heap pour les BlockEntities Cinétiques.
- **Audio Pool Bridge** : Pool natif pour les sons afin d'alléger la mémoire Java.
- **Toggles UI (Sodium/Embeddium/OptConfigScreen)** : Ajout des bascules (ON/OFF) dans les pages d'options pour contrôler ces nouvelles optimisations (JEI, Fonts, Model Eviction, NBT Off-Heap).
- **SodiumCompanion** : Ajustement automatique du nombre de threads de rendu des chunks selon le SoC mobile.
- **FerriteCore Companion** : Le MemoryWatchdog détecte l'absence de FerriteCore et abaisse automatiquement le seuil de purge.
- **ThermalMonitor** : Surveille la température via `/sys/class/thermal` et force le `FrameBudgetManager` en REDUCED/CRITICAL.

### Fixed
- **Comportement de sauvegarde (UI)** : La touche ECHAP dans le menu `OptConfigScreen` annule désormais les modifications en cours. La sauvegarde ne se fait que via les boutons "Appliquer" ou "Terminer" conformément à l'intégration Sodium/Embeddium.

### Removed
- **ChunkPreloader** : Suppression complète du pré-chargement des chunks (sur Android eMMC/UFS lent, cela consommait trop de RAM) remplacé par le `FreezeDebugger` intelligent.

## [1.0.4] - 2024-05-25

### Changed (Purge de caches intelligente)
- **MemoryWatchdog** : Au lieu de forcer le Garbage Collector quand la RAM atteint 80%, le mod declenche desormais une purge intelligente des caches (textures inutilisees, SuperByteBufferCache de Create, et SchematicHandler) de maniere **asynchrone**. Cela libere des centaines de Megaoctets de RAM sans jamais bloquer le thread principal, repoussant ainsi le besoin d'un GC natif.

## [1.0.3] - 2024-05-25

### Changed (Strategie GC Zero-Force)
- **MemoryWatchdog** : Suppression de l'appel manuel a `System.gc()` lorsque le "soft threshold" de la RAM est atteint (ex: 80%). Bien que cela aidait a eviter un crash, forcer le GC manuellement bloquait instantanement le thread principal. Desormais, le mod delegue ce nettoyage en arriere-plan au G1GC/ZGC natif de Java, offrant une experience beaucoup plus lisse sans micro-freezes. Le GC force n'est conserve que pour le seuil critique d'urgence (88%) pour sauver le jeu d'un plantage.

## [1.0.2] - 2024-05-25

### Fixed (Resolution des freezes I/O Thread bloquants et Diagnostics)
- **FreezeDebugger** : Le diagnostic des freezes superieurs a 200ms en multijoueur indiquait faussement "I/O DISK". Il indique desormais "[NETWORK] Paquets serveur (Create/reseau) traites sur render thread" lorsque le joueur est connecte a un serveur distant. L'ecriture du rapport `.txt` a la deconnexion (ou en jeu) est maintenant asynchrone (`runAsync()`), eitant de bloquer le thread principal.
- **CreateCacheCleanupHandler** : Le nettoyage du cache `SuperByteBufferCache` et `SchematicHandler` s'executait sur le thread de rendu principal toutes les minutes, causant un freeze I/O de ~250ms. Les nettoyages sont desormais decharges dans un `CompletableFuture.runAsync()`.
- **ServerModeDetector** : Le mode MULTIJOUEUR etait detecte trop tard (aprs le chargement du monde), provoquant un enorme pic de lag lors de l'apparition des premiers chunks. Le mode est desormais detecte via l'evenement `ClientPlayerNetworkEvent.LoggingIn` avec un GC preventif pre-affichage.

## [1.0.1] - 2024-05-25
- **ContraptionWorldMixin** : Le champ `world` pour le nettoyage de la memoire Create (Contraptions) n'etait pas trouve a cause de l'obfuscation. Le mixin recherche desormais par type (`Level` ou contenant `World`) plutot que par nom, resolvant les fuites de memoire a l'arret des machines.
- **ChunkPreloader** : Appelait `mc.level.getChunk()` meme si le chunk etait deja charge, provoquant des lectures inutiles sur le disque. Le preloader verifie desormais `hasChunk` avant de precharger.
- **RenderScaleManager** : Interferences avec le pipeline framebuffer du mod Veil 4.0.0. Le mod detecte maintenant la presence de Veil dynamiquement et desactive le `rt.resize()` en fallback automatique.
- **AmbientSoundSuppressor** : Les volumes (Ambiant, Meteo, Musique) n'etaient pas restaures si l'option etait desactivee en plein jeu. Les volumes originaux sont maintenant sauvegardes et restitues correctement a la desactivation.
- **StaticBERBatcher** et **ServerBETickMixin** : La liste d'exceptions (fours, coffres) etait "hardcodee". Remplacee par des verifications d'interfaces et de noms de classes dynamiques, prevenant les crashs silencieux si un mod ou le jeu met a jour un de ces blocs.

### Changed (Ameliorations de la logique)
- **MemoryWatchdog (Seuil GC Adaptatif)** : Le seuil de declenchement du Garbage Collector preventif n'est plus fixe a 65%. Il s'adapte dynamiquement a la RAM totale allouee a Minecraft (ex: 75% si < 2.5Go, 85% si >= 4Go).
- **ChunkRebuildThrottler (Dynamique)** : La limite des rebuilds de chunks (qui etait fixee a 2 par frame) s'adapte maintenant directement selon le stress de la frame (FrameBudgetManager) : passe a 1 en CRITICAL, 2 en LOW, et sa valeur par defaut sinon.
- **TickSkipper (Reactivite)** : L'intervalle de verification est passe de 40 ticks a 10 ticks (0.5s), permettant de re-afficher les entites masquees beaucoup plus rapidement lors d'un deplacement.
- **CreateBerCuller** : La distance de masquage des engrenages Create se reduit desormais dynamiquement de 25% a 50% quand le FrameBudgetManager passe en LOW ou CRITICAL.

### Added
- **Rapport de Freezes (Optimizations Efficiency)** : Le compteur interne des "GC preventifs" est desormais integre dans l'export du rapport de session `FreezeDebugger` a la deconnexion.
