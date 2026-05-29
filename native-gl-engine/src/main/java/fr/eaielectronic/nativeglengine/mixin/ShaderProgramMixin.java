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
        // BYPASS COMPLET : On ne touche pas au flux pour tester si c'est la cause du crash !
        return inputStream;
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
        // BYPASS COMPLET : On laisse Minecraft compiler normalement sans cache ni async
        return;
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
