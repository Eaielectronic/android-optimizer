package fr.eaielectronic.nativeglengine;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration NeoForge pour NativeGLEngine.
 * Fichier TOML généré dans config/nativeglengine-client.toml
 */
public class NativeGLConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // ═══ Shader Compiler ═══
    public static final ModConfigSpec.BooleanValue SHADER_CACHE_ENABLED;
    public static final ModConfigSpec.BooleanValue ASYNC_COMPILATION;
    public static final ModConfigSpec.IntValue     COMPILER_THREADS;

    // ═══ GL Hooks ═══
    public static final ModConfigSpec.BooleanValue GL_HOOKS_ENABLED;
    public static final ModConfigSpec.BooleanValue STATE_DEDUP;
    public static final ModConfigSpec.BooleanValue TEXTURE_THROTTLING;
    public static final ModConfigSpec.IntValue     MAX_TEXTURE_UPLOADS_PER_FRAME;

    // ═══ Memory Manager ═══
    public static final ModConfigSpec.BooleanValue GPU_MEMORY_MONITOR;
    public static final ModConfigSpec.IntValue     GPU_PRESSURE_SOFT_PERCENT;
    public static final ModConfigSpec.IntValue     GPU_PRESSURE_HARD_PERCENT;

    // ═══ Debug ═══
    public static final ModConfigSpec.BooleanValue VERBOSE_LOG;
    public static final ModConfigSpec.BooleanValue SHOW_HUD;

    static {
        BUILDER.comment("NativeGLEngine — Configuration");

        BUILDER.push("shader");
        SHADER_CACHE_ENABLED = BUILDER
            .comment("Active le cache de shaders compilés (SPIR-V / ESSL).",
                     "Élimine les stutters de compilation shader au démarrage.")
            .define("shaderCacheEnabled", true);
        ASYNC_COMPILATION = BUILDER
            .comment("Compile les shaders de manière asynchrone (hors render thread).",
                     "Un shader placeholder est affiché pendant la compilation.")
            .define("asyncCompilation", true);
        COMPILER_THREADS = BUILDER
            .comment("Nombre de threads dédiés à la compilation shader.",
                     "1-2 recommandé sur mobile pour ne pas surcharger le CPU.")
            .defineInRange("compilerThreads", 2, 1, 4);
        BUILDER.pop();

        BUILDER.push("hooks");
        GL_HOOKS_ENABLED = BUILDER
            .comment("Active les hooks GL (PLT hooking) pour intercepter les appels OpenGL.",
                     "Nécessite la bibliothèque native (.so). Désactiver en cas de crash.")
            .define("glHooksEnabled", true);
        STATE_DEDUP = BUILDER
            .comment("Filtre les appels GL redondants (glEnable/glDisable).",
                     "Gain : 2-5ms par frame avec beaucoup d'entités.")
            .define("stateDedup", true);
        TEXTURE_THROTTLING = BUILDER
            .comment("Queue les uploads de texture si le budget GPU est tendu.",
                     "Évite les freezes de 5-15ms lors du chargement de chunks.")
            .define("textureThrottling", true);
        MAX_TEXTURE_UPLOADS_PER_FRAME = BUILDER
            .comment("Nombre max de textures différées à uploader par frame.",
                     "Plus bas = moins de stutters, mais textures apparaissent plus lentement.")
            .defineInRange("maxTextureUploadsPerFrame", 3, 1, 10);
        BUILDER.pop();

        BUILDER.push("memory");
        GPU_MEMORY_MONITOR = BUILDER
            .comment("Surveille le budget mémoire GPU via VMA + VK_EXT_memory_budget.",
                     "Nécessite la bibliothèque native (.so).")
            .define("gpuMemoryMonitor", true);
        GPU_PRESSURE_SOFT_PERCENT = BUILDER
            .comment("Seuil de pression GPU pour un cleanup doux (%).",
                     "Au-dessus de ce seuil : éviction des textures inutilisées.")
            .defineInRange("gpuPressureSoft", 80, 50, 95);
        GPU_PRESSURE_HARD_PERCENT = BUILDER
            .comment("Seuil de pression GPU pour un cleanup agressif (%).",
                     "Au-dessus de ce seuil : vidage complet des caches.")
            .defineInRange("gpuPressureHard", 92, 70, 100);
        BUILDER.pop();

        BUILDER.push("debug");
        VERBOSE_LOG = BUILDER
            .comment("Active les logs détaillés (très verbeux).")
            .define("verboseLog", false);
        SHOW_HUD = BUILDER
            .comment("Affiche un HUD avec les stats NativeGLEngine.",
                     "Renderer, shader cache hits, GPU pressure, hooks stats.")
            .define("showHud", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private NativeGLConfig() {}
}
