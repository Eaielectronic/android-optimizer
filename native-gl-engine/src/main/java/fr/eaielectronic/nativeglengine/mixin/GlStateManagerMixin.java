package fr.eaielectronic.nativeglengine.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import fr.eaielectronic.nativeglengine.GLInterceptorBridge;
import fr.eaielectronic.nativeglengine.NativeLib;
import fr.eaielectronic.nativeglengine.NativeEngineState;
import fr.eaielectronic.nativeglengine.NativeGLEngineMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;

/**
 * Intercepte les uploads de texture au niveau GlStateManager.
 *
 * Architecture async :
 * 1. Pré-filtre rapide côté Java (dimensions, format, null)
 * 2. Copie des pixels dans un buffer off-heap (le buffer Java sera réutilisé)
 * 3. Soumission au thread pool C++ pour compression ETC2 en arrière-plan
 * 4. ci.cancel() — on prend la main, l'upload original est annulé
 * 5. Le résultat compressé est mis dans une queue MPSC côté C++
 * 6. NativeGLTickHandler drain la queue sur le GL thread chaque tick
 *    via glCompressedTexImage2D
 *
 * Priorité 500 = s'exécute AVANT MobileGlues (priorité par défaut = 1000).
 */
@Mixin(value = GlStateManager.class, priority = 500)
public class GlStateManagerMixin {

    /**
     * Intercepte _texImage2D(int, int, int, int, int, int, int, int, IntBuffer).
     * Seule surcharge présente dans MC 1.21.1 pour _texImage2D.
     */
    @Inject(method = "_texImage2D", at = @At("HEAD"), cancellable = true)
    private static void nativegl$onTexImage2D(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, IntBuffer pixels,
            CallbackInfo ci
    ) {
        // ═══ Guard : natif dispo ? ═══
        if (!NativeLib.isLoaded()) return;

        // ═══ Guard : initialisation terminée ? ═══
        // Empêche d'intercepter les textures d'initialisation de mods comme Veil
        if (!NativeEngineState.renderingReady) return;

        // ═══ Pré-filtre rapide côté Java (évite le JNI inutile) ═══
        if (pixels == null) return;                        // pas de données
        if (width < 64 || height < 64) return;             // trop petit, pas de gain
        if (width % 4 != 0 || height % 4 != 0) return;    // ETC2 exige alignement 4x4

        // Ne compresser que les formats RGBA/RGB non-compressés
        if (!isCompressibleFormat(internalFormat)) return;

        try {
            // ═══ Cas 1 : Loading screen (async total) ═══
            // Invisible pour le joueur, donc aucun risque de flash noir/rose.
            // Gain maximal pour les gros atlas (4096x4096).
            if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
                boolean accepted = GLInterceptorBridge.submitAsyncCompress(
                    target, level, internalFormat,
                    width, height, border,
                    format, type, pixels
                );
                if (accepted) {
                    ci.cancel();
                }
            }
            // ═══ Cas 2 : En jeu (synchrone rapide ou passthrough) ═══
            // Evite les artefacts visuels lors du chargement de nouvelles textures dynamiques.
            else {
                if (width <= 256 && height <= 256) {
                    boolean compressed = GLInterceptorBridge.compressAndUploadSync(
                        target, level, internalFormat,
                        width, height, border,
                        format, type, pixels
                    );
                    if (compressed) {
                        ci.cancel();
                    }
                }
                // Si > 256px en jeu, on laisse passer (passthrough) pour éviter les freezes.
            }
        } catch (Exception e) {
            // Ne JAMAIS crasher le render thread — fallback silencieux
            NativeGLEngineMod.LOGGER.error("[NativeGLEngine] Erreur interception texture", e);
        }
    }

    /**
     * Intercepte _texSubImage2D.
     * On ne compresse PAS les sub-images : ETC2 exige des blocs 4x4 alignés,
     * et les sub-updates ne garantissent pas cet alignement.
     * On laisse Minecraft/MobileGlues gérer normalement.
     */
    @Inject(method = "_texSubImage2D", at = @At("HEAD"), cancellable = false)
    private static void nativegl$onTexSubImage2D(
            int target, int level, int xOffset, int yOffset,
            int width, int height, int format, int type, long pixels,
            CallbackInfo ci
    ) {
        // Compteur seulement — pas d'interception
        if (NativeLib.isLoaded()) {
            GLInterceptorBridge.incrementSubImageCount();
        }
    }

    /**
     * Filtre rapide : est-ce un format interne qu'on sait compresser ?
     * Exclut les formats déjà compressés, les formats depth/stencil, etc.
     */
    private static boolean isCompressibleFormat(int internalFormat) {
        switch (internalFormat) {
            // Formats RGBA non-compressés
            case 0x8058: // GL_RGBA8
            case 0x1908: // GL_RGBA
            case 0x8056: // GL_RGBA4
            case 0x8057: // GL_RGB5_A1

            // Formats RGB non-compressés
            case 0x8051: // GL_RGB8
            case 0x1907: // GL_RGB
                return true;

            default:
                return false;
        }
    }
}
