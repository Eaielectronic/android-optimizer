package fr.eaielectronic.androidopt;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;


public class OptConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue FORCE_ENABLE;
    public static final ModConfigSpec.ConfigValue<String> MANUAL_SOC_ID;
    public static final ModConfigSpec.BooleanValue AUTO_APPLY_SOC_PROFILE;

    public static final ModConfigSpec.BooleanValue RENDER_OPTS;
    public static final ModConfigSpec.IntValue     RENDER_DISTANCE;
    public static final ModConfigSpec.BooleanValue DISABLE_CLOUDS;
    public static final ModConfigSpec.BooleanValue DISABLE_AMBIENT_OCCLUSION;
    public static final ModConfigSpec.BooleanValue DISABLE_ENTITY_SHADOWS;
    public static final ModConfigSpec.BooleanValue MINIMAL_PARTICLES;
    public static final ModConfigSpec.IntValue     MIPMAP_LEVELS;

    public static final ModConfigSpec.BooleanValue MEMORY_WATCHDOG;
    public static final ModConfigSpec.IntValue     GC_THRESHOLD_PERCENT;
    public static final ModConfigSpec.IntValue     GC_CRITICAL_PERCENT;     // NEW
    public static final ModConfigSpec.IntValue     GC_CHECK_INTERVAL;
    public static final ModConfigSpec.BooleanValue TEXTURE_CACHE_EVICTOR;
    public static final ModConfigSpec.BooleanValue SECTION_BUFFER_LIMIT;
    public static final ModConfigSpec.BooleanValue PONDER_CACHE_CLEAR;      // NEW
    public static final ModConfigSpec.BooleanValue TEXTURE_DOWNSCALE_ENABLED;
    public static final ModConfigSpec.IntValue     MAX_TOTAL_RAM_MB;        // NEW

    public static final ModConfigSpec.BooleanValue ENTITY_THROTTLER;
    public static final ModConfigSpec.IntValue     ENTITY_THROTTLE_DISTANCE; // NEW
    public static final ModConfigSpec.BooleanValue TICK_SKIPPER;
    public static final ModConfigSpec.IntValue     TICK_SKIP_DISTANCE;      // NEW
    public static final ModConfigSpec.BooleanValue SERVER_BE_TICK_THROTTLE;  // NEW

    public static final ModConfigSpec.BooleanValue CREATE_BER_CULLER;
    public static final ModConfigSpec.IntValue     BER_CULL_DISTANCE;       // NEW
    public static final ModConfigSpec.BooleanValue CHUNK_REBUILD_THROTTLER;
    public static final ModConfigSpec.IntValue     CHUNK_REBUILDS_PER_FRAME;
    public static final ModConfigSpec.BooleanValue RENDER_SCALE_ENABLED;
    public static final ModConfigSpec.BooleanValue CREATE_PARTICLES_FILTER;
    public static final ModConfigSpec.BooleanValue STOPPED_CONTRAPTION_SKIP;
    public static final ModConfigSpec.IntValue     CREATE_RENDER_FPS;
    public static final ModConfigSpec.BooleanValue FLYWHEEL_BUFFER_CULLER;
    public static final ModConfigSpec.BooleanValue FLYWHEEL_BACKEND_FORCE;
    public static final ModConfigSpec.BooleanValue FLUID_RENDER_CULLER;
    public static final ModConfigSpec.IntValue     FLUID_CULL_DISTANCE;     // NEW
    public static final ModConfigSpec.IntValue     GOGGLE_CULL_DISTANCE;    // NEW
    public static final ModConfigSpec.BooleanValue KINETIC_NETWORK_SKIP;
    public static final ModConfigSpec.BooleanValue CONTRAPTION_MEMORY_CLEANUP;

    public static final ModConfigSpec.BooleanValue AMBIENT_SOUND_SUPPRESSOR; // NEW

    public static final ModConfigSpec.BooleanValue WEATHER_SUPPRESSOR;
    public static final ModConfigSpec.BooleanValue CHUNK_UNLOADER;
    public static final ModConfigSpec.BooleanValue ASYNC_WORLD_SAVE;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;
    public static final ModConfigSpec.BooleanValue SABLE_UDP_FIX;

    public static final ModConfigSpec.BooleanValue THERMAL_MONITOR;
    public static final ModConfigSpec.IntValue     THERMAL_WARNING_TEMP;
    public static final ModConfigSpec.IntValue     THERMAL_CRITICAL_TEMP;

    public static final ModConfigSpec.BooleanValue FREEZE_DEBUGGER;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_FPS;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_HEAP;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_BUDGET;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_SOC;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_OPTIMS;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_FREEZE;
    public static final ModConfigSpec.BooleanValue DEBUG_VERBOSE_LOG;

    public static final ModConfigSpec.IntValue     NATIVE_GL_GPU_BUDGET_PERCENT;
    public static final ModConfigSpec.BooleanValue NATIVE_GL_TEX_COMPRESS;
    public static final ModConfigSpec.BooleanValue NATIVE_GL_VERTEX_QUANT;

    static {
        BUILDER.comment("Android Optimizer — Configuration");

        BUILDER.comment("=== GLOBAL ===");
        FORCE_ENABLE = BUILDER
            .comment("Force toutes les optimisations même sur PC (utile pour tester).",
                     "Mets à true pour tester sans Android.")
            .define("forceEnable", false);

        MANUAL_SOC_ID = BUILDER
            .comment("ID du profil SoC à utiliser. 'auto' = détection automatique.",
                     "IDs disponibles : snapdragon_865, snapdragon_888, snapdragon_8gen1,",
                     "snapdragon_8gen2, snapdragon_8gen3, dimensity_1200, dimensity_9000,",
                     "exynos_2100, exynos_2200, kirin_990, generic_arm64_high, generic_arm64_mid")
            .define("manualSocId", "auto");

        AUTO_APPLY_SOC_PROFILE = BUILDER
            .comment("Applique automatiquement les paramètres recommandés du profil SoC détecté.",
                     "Écrase renderDistance et gcThresholdPercent au démarrage.")
            .define("autoApplySocProfile", false);

        BUILDER.push("render");
        RENDER_OPTS = BUILDER
            .comment("Active les optimisations de rendu au démarrage.")
            .define("enabled", true);
        RENDER_DISTANCE = BUILDER
            .comment("Distance de rendu forcée (chunks). 0 = ne pas forcer.")
            .defineInRange("renderDistance", 4, 0, 32);
        DISABLE_CLOUDS = BUILDER
            .comment("Désactive les nuages.")
            .define("disableClouds", true);
        DISABLE_AMBIENT_OCCLUSION = BUILDER
            .comment("Désactive l'ambient occlusion (smooth lighting).")
            .define("disableAmbientOcclusion", true);
        DISABLE_ENTITY_SHADOWS = BUILDER
            .comment("Désactive les ombres des entités.")
            .define("disableEntityShadows", true);
        MINIMAL_PARTICLES = BUILDER
            .comment("Réduit les particles au minimum.",
                     "Active aussi le filtre des particules Create.")
            .define("minimalParticles", true);
        MIPMAP_LEVELS = BUILDER
            .comment("Niveau de mipmap. 0 = désactivé (recommandé mobile).")
            .defineInRange("mipmapLevels", 0, 0, 4);
        BUILDER.pop();

        BUILDER.push("memory");
        MEMORY_WATCHDOG = BUILDER
            .comment("Active la surveillance mémoire (GC préventif).")
            .define("enabled", true);
        GC_THRESHOLD_PERCENT = BUILDER
            .comment("Seuil de declenchement du GC preventif (%).",
                     "75 recommande pour 2.6 Go. Plus bas = GC plus frequent mais plus court.")
            .defineInRange("gcThresholdPercent", 75, 50, 95);
        GC_CRITICAL_PERCENT = BUILDER
            .comment("Seuil critique d'urgence (%).",
                     "Force un System.gc() bloquant si la RAM atteint ce pourcentage pour eviter le crash.")
            .defineInRange("gcCriticalPercent", 88, 70, 100);
        GC_CHECK_INTERVAL = BUILDER
            .comment("Intervalle de verification memoire (ticks). 100 = 5 secondes.",
                     "Plus bas = detection plus rapide des pics memoire.")
            .defineInRange("gcCheckInterval", 100, 20, 1200);
        TEXTURE_CACHE_EVICTOR = BUILDER
            .comment("Libère les textures inutilisées toutes les 2 min.",
                     "Gain : -50 à -150 Mo RAM.")
            .define("textureCacheEvictor", true);
        SECTION_BUFFER_LIMIT = BUILDER
            .comment("Réduit le pool de buffers de compilation de chunks à 4 (au lieu de 12).",
                     "Gain : -80 Mo RAM.")
            .define("sectionBufferLimit", true);
        PONDER_CACHE_CLEAR = BUILDER
            .comment("Vide le cache des scènes Ponder Create au démarrage.",
                     "Gain : -30 à -80 Mo RAM. Ponder reste fonctionnel.")
            .define("ponderCacheClear", true);
        TEXTURE_DOWNSCALE_ENABLED = BUILDER
            .comment("Divise la résolution des textures par 2 au chargement.",
                     "Gain énorme de RAM GPU (-150 à -300 Mo).")
            .define("textureDownscale", true);
        BUILDER.pop();

        BUILDER.push("ram_budget");
        MAX_TOTAL_RAM_MB = BUILDER
            .comment("RAM totale maximale allouable à Java (Mo).",
                     "Le mod calcule automatiquement le Xmx safe.",
                     "Formule : Xmx safe = maxTotalRamMB - overhead natif (~250 Mo)",
                     "Défaut : 2700 Mo → Xmx effectif ~2450 Mo",
                     "Sur S20 FE (6 Go RAM) : ne pas dépasser 2700.")
            .defineInRange("maxTotalRamMB", 2700, 1500, 4096);
        BUILDER.pop();

        BUILDER.push("entities");
        ENTITY_THROTTLER = BUILDER
            .comment("Gèle les animations des entités lointaines.")
            .define("entityThrottler", true);
        ENTITY_THROTTLE_DISTANCE = BUILDER
            .comment("Distance en blocs au-delà de laquelle les entités sont throttlées.")
            .defineInRange("entityThrottleDistance", 32, 8, 128);
        TICK_SKIPPER = BUILDER
            .comment("Réduit les updates des BlockEntities lointains.")
            .define("tickSkipper", true);
        TICK_SKIP_DISTANCE = BUILDER
            .comment("Distance en blocs au-delà de laquelle les BEs sont skip.")
            .defineInRange("tickSkipDistance", 24, 8, 128);
        SERVER_BE_TICK_THROTTLE = BUILDER
            .comment("Réduit le tick rate des BEs lointains côté serveur (solo uniquement, ÷4 à >24 blocs).")
            .define("serverBeTickThrottle", true);
        BUILDER.pop();

        BUILDER.push("create");
        CREATE_BER_CULLER = BUILDER
            .comment("Coupe le rendu des BlockEntityRenderers Create au-delà de la distance configurée.",
                     "Gain massif avec une grosse base Create. +5-15 FPS.")
            .define("createBerCuller", true);
        BER_CULL_DISTANCE = BUILDER
            .comment("Distance en blocs au-delà de laquelle les BERs Create sont cachés.",
                     "Plus bas = plus de FPS, mais les machines lointaines ne s'animent plus.")
            .defineInRange("berCullDistance", 12, 4, 64);
        CHUNK_REBUILD_THROTTLER = BUILDER
            .comment("Limite les recompilations de chunks par frame.",
                     "Élimine les micro-freezes causés par les machines Create.")
            .define("chunkRebuildThrottler", true);
        CHUNK_REBUILDS_PER_FRAME = BUILDER
            .comment("Nombre max de recompilations de chunks par frame.",
                     "Plus bas = moins de freezes, mais chunks se chargent plus lentement.",
                     "Recommandé : 1 sur mobile, 4-8 sur PC.")
            .defineInRange("chunkRebuildsPerFrame", 1, 1, 16);
        CREATE_PARTICLES_FILTER = BUILDER
            .comment("Supprime les particules Create (fumée, poussière, étincelles).",
                     "Les particules vanilla (feu, eau, explosion) ne sont pas affectées.")
            .define("createParticlesFilter", true);
        STOPPED_CONTRAPTION_SKIP = BUILDER
            .comment("Skip le rendu des contraptions Create arrêtées (vitesse = 0).",
                     "Actif seulement en mode LOW/CRITICAL du frame budget.")
            .define("stoppedContraptionSkip", true);
        CREATE_RENDER_FPS = BUILDER
            .comment("FPS de rendu des buffers Create (contraptions, machines).",
                     "Create met à jour ses buffers à chaque frame par défaut.",
                     "20 FPS suffit largement pour les animations de roues/engrenages.",
                     "Plus bas = plus de FPS en jeu. 1 = mise à jour 1x/sec.")
            .defineInRange("createRenderFps", 20, 1, 60);
        FLYWHEEL_BUFFER_CULLER = BUILDER
            .comment("Libère les buffers GPU Flywheel des contraptions hors render distance.",
                     "Gain : -50 à -100 Mo RAM GPU. Toutes les 60s.")
            .define("flywheelBufferCuller", true);
        FLYWHEEL_BACKEND_FORCE = BUILDER
            .comment("Force le backend Flywheel en mode Instancing sur Android.",
                     "Évite le fallback sur le rendu vanilla (très lent pour Create).")
            .define("flywheelBackendForce", true);
        FLUID_RENDER_CULLER = BUILDER
            .comment("Coupe le rendu des fluides Create dans les tuyaux au-delà de la distance configurée.")
            .define("fluidRenderCuller", true);
        FLUID_CULL_DISTANCE = BUILDER
            .comment("Distance en blocs au-delà de laquelle les fluides Create ne sont plus rendus.")
            .defineInRange("fluidCullDistance", 12, 4, 64);
        GOGGLE_CULL_DISTANCE = BUILDER
            .comment("Distance en blocs au-delà de laquelle les overlays Goggle sont masqués.",
                     "Le texte est illisible sur mobile au-delà de 8 blocs.")
            .defineInRange("goggleCullDistance", 8, 2, 32);
        KINETIC_NETWORK_SKIP = BUILDER
            .comment("Skip le recalcul de stress des réseaux cinétiques lointains (solo uniquement).",
                     "Gain : +5-15% CPU en solo avec beaucoup de machines.")
            .define("kineticNetworkSkip", true);
        RENDER_SCALE_ENABLED = BUILDER
            .comment("Réduit la résolution de rendu à 75% (upscale automatique).",
                     "Gain : +40-60% FPS. Rendu légèrement flou mais jouable.",
                     "ATTENTION : désactivé par défaut car casse le GUI sur PC.")
            .define("renderScale", false);
        CONTRAPTION_MEMORY_CLEANUP = BUILDER
            .comment("Supprime le monde virtuel des contraptions arrêtées (Create 6.x / Sable).",
                     "Gain : -30 à -80 Mo RAM par machine.")
            .define("contraptionMemoryCleanup", true);
        BUILDER.pop();

        BUILDER.push("sound");
        AMBIENT_SOUND_SUPPRESSOR = BUILDER
            .comment("Coupe les sons ambiants (cave sounds, pluie) et réduit la musique à 30%.",
                     "Gain CPU : réduit les appels OpenAL par tick.",
                     "ATTENTION : modifie les options de volume Minecraft.")
            .define("ambientSoundSuppressor", true);
        BUILDER.pop();

        BUILDER.push("misc");
        WEATHER_SUPPRESSOR = BUILDER
            .comment("Désactive le rendu de la pluie/neige côté client.")
            .define("weatherSuppressor", true);
        CHUNK_UNLOADER = BUILDER
            .comment("Libère les chunks hors portée toutes les 90s.")
            .define("chunkUnloader", true);
        ASYNC_WORLD_SAVE = BUILDER
            .comment("Désolidarise la sauvegarde automatique du monde sur un thread séparé.",
                     "Évite que le jeu freeze à 100% pendant la sauvegarde (Solo/Integrated Server).")
            .define("asyncWorldSave", true);
        SHOW_HUD = BUILDER
            .comment("Affiche un HUD en jeu avec FPS, heap, budget frame et état des optims.")
            .define("showHud", true);
        SABLE_UDP_FIX = BUILDER
            .comment("Désactive l'authentification UDP de Sable pour empêcher les freezes de reconnexion.",
                     "Gain : -200 à -300ms de micro-freezes réseau.")
            .define("sableUdpFix", true);
        BUILDER.pop();

        BUILDER.push("thermal");
        THERMAL_MONITOR = BUILDER
            .comment("Surveille la température du CPU et bride le rendu si surchauffe.")
            .define("thermalMonitor", true);
        THERMAL_WARNING_TEMP = BUILDER
            .comment("Température (en °C) pour activer le Frame Budget REDUCED.")
            .defineInRange("thermalWarningTemp", 55, 35, 99);
        THERMAL_CRITICAL_TEMP = BUILDER
            .comment("Température (en °C) pour forcer le Frame Budget CRITICAL.")
            .defineInRange("thermalCriticalTemp", 65, 40, 99);
        BUILDER.pop();

        BUILDER.push("debug");
        FREEZE_DEBUGGER = BUILDER
            .comment("Active le FreezeDebugger — log les freezes avec diagnostic (GC/rendu).",
                     "Les données apparaissent dans latest.log et dans le HUD.")
            .define("freezeDebugger", true);
        HUD_SHOW_FPS = BUILDER
            .comment("Afficher les FPS dans le HUD.")
            .define("hudShowFps", true);
        HUD_SHOW_HEAP = BUILDER
            .comment("Afficher la consommation RAM/Heap dans le HUD.")
            .define("hudShowHeap", true);
        HUD_SHOW_BUDGET = BUILDER
            .comment("Afficher le niveau de budget frame dans le HUD.")
            .define("hudShowBudget", true);
        HUD_SHOW_SOC = BUILDER
            .comment("Afficher le SoC détecté dans le HUD.")
            .define("hudShowSoc", true);
        HUD_SHOW_OPTIMS = BUILDER
            .comment("Afficher l'état des optimisations (ON/OFF) dans le HUD.")
            .define("hudShowOptims", true);
        HUD_SHOW_FREEZE = BUILDER
            .comment("Afficher les stats FreezeDebugger dans le HUD.",
                     "(compteur freezes, pic, heap %)")
            .define("hudShowFreeze", false);
        DEBUG_VERBOSE_LOG = BUILDER
            .comment("Active les logs détaillés de TOUTES les optimisations.",
                     "Utile pour debug, très verbeux. Désactiver en jeu normal.")
            .define("verboseLog", false);
        BUILDER.pop();

        BUILDER.push("native_engine");
        NATIVE_GL_GPU_BUDGET_PERCENT = BUILDER
            .comment("Pourcentage de la RAM unifiée allouée au GPU par le moteur natif.")
            .defineInRange("gpuBudgetPercent", 75, 10, 100);
        NATIVE_GL_TEX_COMPRESS = BUILDER
            .comment("Active la compression matérielle des textures (ASTC/ETC2) via C++.")
            .define("texCompress", true);
        NATIVE_GL_VERTEX_QUANT = BUILDER
            .comment("Active la quantisation des vertices en C++.")
            .define("vertexQuant", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    
    public static boolean isActive() {
        return AndroidDetector.IS_ANDROID || FORCE_ENABLE.get();
    }

    private OptConfig() {}
}
