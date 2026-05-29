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

    private static final ThreadLocal<String> currentShaderName = new ThreadLocal<>();
    private static final ThreadLocal<Integer> currentShaderType = new ThreadLocal<>();

    @Inject(
        method = "compileShaderInternal",
        at = @At("HEAD")
    )
    private static void onCompileShaderInternalHead(
            Program.Type type,
            String name,
            InputStream inputStream,
            String sourceName,
            GlslPreprocessor preprocessor,
            CallbackInfoReturnable<Integer> cir
    ) {
        currentShaderName.set(name);
        currentShaderType.set(type.ordinal());
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(
        method = "compileShaderInternal",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"),
        index = 1
    )
    private static java.util.List<String> captureProcessedShader(java.util.List<String> processedLines) {
        if (processedLines == null || processedLines.isEmpty()) {
            return processedLines;
        }

        try {
            if (!fr.eaielectronic.nativeglengine.NativeGLConfig.SHADER_CACHE_ENABLED.get()) {
                return processedLines;
            }
        } catch (Exception e) {
            // Config pas encore prête
        }

        String name = currentShaderName.get();
        Integer typeOrdinal = currentShaderType.get();
        
        if (name == null || typeOrdinal == null) {
            return processedLines;
        }

        currentShaderName.remove();
        currentShaderType.remove();

        java.util.List<String> translatedLines = new java.util.ArrayList<>();
        boolean precisionAdded = false;
        boolean glHooksEnabled = false;
        
        try {
            glHooksEnabled = fr.eaielectronic.nativeglengine.NativeGLConfig.GL_HOOKS_ENABLED.get();
        } catch (Exception ignored) {}

        if (glHooksEnabled) {
            for (String line : processedLines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#version")) {
                    translatedLines.add("#version 320 es\n");
                } else {
                    if (!precisionAdded && !trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("#")) {
                        translatedLines.add("precision highp float;\n");
                        precisionAdded = true;
                    }
                    translatedLines.add(line);
                }
            }
        } else {
            translatedLines.addAll(processedLines);
        }

        String glslSource = String.join("\n", translatedLines);

        // ═══ Étape 1 : Hash SHA-256 ═══
        String driverVersion = ShaderCompilerBridge.getDriverVersion();
        String socName = AndroidOptBridge.getSocName();
        String hash = ShaderCacheManager.computeHash(glslSource, socName, driverVersion);

        // ═══ Étape 2 : Lancement compilation Async ═══
        if (NativeLib.isLoaded()) {
            try {
                if (fr.eaielectronic.nativeglengine.NativeGLConfig.ASYNC_COMPILATION.get()) {
                    compilationsAsync++;
                    ShaderCompilerBridge.compileAsync(glslSource, typeOrdinal, hash, (spirv) -> {
                        if (spirv != null) {
                            NativeGLEngineMod.LOGGER.debug(
                                "[NativeGLEngine] Shader '{}' compilé en arrière-plan ({} bytes SPIR-V)",
                                name, spirv.length);
                        }
                    });
                }
            } catch (Exception ignored) {}
        }

        return translatedLines;
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
