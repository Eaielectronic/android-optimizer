package fr.eaielectronic.nativeglengine.mixin;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import fr.eaielectronic.nativeglengine.AndroidOptBridge;
import fr.eaielectronic.nativeglengine.NativeGLEngineMod;
import fr.eaielectronic.nativeglengine.NativeLib;
import fr.eaielectronic.nativeglengine.ShaderCacheManager;
import fr.eaielectronic.nativeglengine.ShaderCompilerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

/**
 * Mixin qui intercepte la compilation de shaders dans Minecraft.
 * 
 * Cible : com.mojang.blaze3d.shaders.Program (Mojang Mappings NeoForge 1.21.1)
 * 
 * Fonctionnement :
 * 1. Hash SHA-256 du source GLSL + contexte hardware
 * 2. Vérification cache L1 (mémoire, ~1µs) → retour immédiat si trouvé
 * 3. Vérification cache L2 (disque, ~5ms) → retour si trouvé
 * 4. Si pas en cache et natif disponible → compilation async + placeholder
 * 5. Si pas de natif → laisser Minecraft compiler normalement (passthrough)
 */
@Mixin(Program.class)
public class ShaderProgramMixin {

    // Compteurs pour les stats HUD
    private static int cacheHitsMemory = 0;
    private static int cacheHitsDisk = 0;
    private static int cacheMisses = 0;
    private static int compilationsAsync = 0;

    private static final ThreadLocal<String> currentGlslSource = new ThreadLocal<>();

    /**
     * Intercepte l'InputStream au tout début pour le lire, stocker le GLSL
     * dans un ThreadLocal, et retourner un nouveau flux pour que MC puisse continuer.
     */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "compileShader",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static InputStream captureGlslSource(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            currentGlslSource.set(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            return new java.io.ByteArrayInputStream(bytes);
        } catch (Exception e) {
            NativeGLEngineMod.LOGGER.error("Erreur capture GLSL : {}", e.getMessage());
            return inputStream;
        }
    }

    /**
     * Intercepte la compilation de shader.
     * 
     * Cible : Program.compileShader(Type, String, InputStream, String, GlslPreprocessor)
     * Signature Mojang Mappings NeoForge 1.21.1 — retourne un Program.
     */
    @Inject(
        method = "compileShader",
        at = @At("HEAD"),
        cancellable = true,
        require = 0  // Ne pas crasher si la méthode n'existe pas (compatibilité)
    )
    private static void onCompileShader(
            Program.Type type,
            String name,
            InputStream inputStream,
            String sourceName,
            GlslPreprocessor preprocessor,
            CallbackInfoReturnable<Program> cir
    ) {
        // Ne rien faire si le cache shader est désactivé
        // (la config n'est peut-être pas encore chargée au premier appel)
        try {
            if (!fr.eaielectronic.nativeglengine.NativeGLConfig.SHADER_CACHE_ENABLED.get()) return;
        } catch (Exception e) {
            // Config pas encore prête — continuer avec le cache activé par défaut
        }

        NativeGLEngineMod.LOGGER.info("[NativeGLEngine] onCompileShader trigger: type={} name={}", type, name);

        String glslSource = currentGlslSource.get();
        if (glslSource == null) glslSource = ""; // Fallback
        currentGlslSource.remove();

        // ═══ Étape 0 : Résoudre les #moj_import ═══
        if (preprocessor != null && glslSource.contains("#moj_import")) {
            try {
                java.lang.reflect.Method processMethod = preprocessor.getClass().getMethod("process", String.class);
                Object processed = processMethod.invoke(preprocessor, glslSource);
                if (processed instanceof java.util.List) {
                    glslSource = String.join("\n", (java.util.List<String>) processed);
                } else if (processed instanceof String) {
                    glslSource = (String) processed;
                }
            } catch (Exception e) {
                NativeGLEngineMod.LOGGER.error("[NativeGLEngine] Erreur résolution #moj_import : {}", e.getMessage());
            }
        }

        // ═══ Étape 1 : Hash SHA-256 ═══
        String driverVersion = ShaderCompilerBridge.getDriverVersion();
        String socName = AndroidOptBridge.getSocName();
        String hash = ShaderCacheManager.computeHash(glslSource, socName, driverVersion);

        // ═══ Étape 2 : Cache L1 (mémoire) ═══
        // Note : compileShader retourne un Program, pas un int.
        // On ne peut pas court-circuiter avec un ID ici.
        // On enregistre les stats de cache pour le monitoring HUD.
        // Le vrai gain viendra quand les hooks GL natifs seront actifs
        // et pourront fournir le SPIR-V pré-compilé au driver directement.
        Integer cachedId = ShaderCacheManager.getFromMemoryCache(hash);
        if (cachedId != null) {
            cacheHitsMemory++;
            // Cache SPIR-V disponible — MC compile quand même mais le
            // driver GPU pourra réutiliser le pipeline cache plus vite
            return;
        }

        // ═══ Étape 3 : Cache L2 (disque) ═══
        Integer diskId = ShaderCacheManager.loadFromDisk(hash);
        if (diskId != null) {
            cacheHitsDisk++;
            ShaderCacheManager.putToMemoryCache(hash, diskId);
            // Même logique — on laisse MC compiler, le SPIR-V est prêt
            return;
        }

        // ═══ Étape 4 : Compilation ═══
        cacheMisses++;

        // Si le natif est disponible et l'async est activé,
        // lancer la compilation en arrière-plan
        if (NativeLib.isLoaded()) {
            try {
                if (fr.eaielectronic.nativeglengine.NativeGLConfig.ASYNC_COMPILATION.get()) {
                    compilationsAsync++;

                    // Compiler en arrière-plan
                    ShaderCompilerBridge.compileAsync(glslSource, type.ordinal(), hash, (spirv) -> {
                        if (spirv != null) {
                            NativeGLEngineMod.LOGGER.debug(
                                "[NativeGLEngine] Shader '{}' compilé en arrière-plan ({} bytes SPIR-V)",
                                name, spirv.length);
                        }
                    });

                    // On ne cancel PAS ici — on laisse Minecraft compiler normalement
                    // pendant que notre compilation async tourne.
                    // Au prochain chargement, le cache disque sera déjà rempli → 0ms.
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Pas de natif ou pas d'async — laisser Minecraft compiler normalement
        // mais sauvegarder le hash pour le futur
        NativeGLEngineMod.LOGGER.debug(
            "[NativeGLEngine] Shader '{}' compilé par MC (cache miss, natif={})",
            name, NativeLib.isLoaded());
    }

    // ═══ Getters pour les stats ═══
    // IMPORTANT : dans un Mixin, toute méthode statique non annotée @Inject
    // doit être private, sinon elle entre en conflit avec la classe cible
    // (com.mojang.blaze3d.shaders.Program) et provoque un crash au démarrage.
    private static int getCacheHitsMemory() { return cacheHitsMemory; }
    private static int getCacheHitsDisk() { return cacheHitsDisk; }
    private static int getCacheMisses() { return cacheMisses; }
    private static int getCompilationsAsync() { return compilationsAsync; }
    private static int getTotalCompilations() { return cacheHitsMemory + cacheHitsDisk + cacheMisses; }
}
