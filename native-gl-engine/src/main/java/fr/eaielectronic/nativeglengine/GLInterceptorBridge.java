package fr.eaielectronic.nativeglengine;

import java.nio.IntBuffer;

/**
 * Bridge JNI vers l'intercepteur GL natif (gl_interceptor.cpp + texture_manager.cpp).
 *
 * Architecture async pour la compression de textures :
 * 1. Le Mixin GlStateManagerMixin appelle submitAsyncCompress() sur le render thread
 * 2. Le C++ copie les pixels immédiatement et lance un worker thread ETC2
 * 3. Le résultat compressé est mis dans une queue MPSC (lock-free)
 * 4. drainCompressedQueue() est appelé chaque tick sur le GL thread
 *    → appelle glCompressedTexImage2D pour chaque résultat prêt
 */
public final class GLInterceptorBridge {

    private static boolean installed = false;

    // Stats (mises à jour depuis le natif ou incrémentées côté Java)
    private static long totalGLCalls = 0;
    private static long dedupedCalls = 0;
    private static long deferredTextures = 0;
    private static long subImageCount = 0;
    private static long asyncSubmitted = 0;
    private static long asyncUploaded = 0;

    private GLInterceptorBridge() {}

    // ═══ Méthodes JNI natives — hooks legacy (PLT) ═══

    private static native boolean nativeInstallHooks();
    private static native void nativeUninstallHooks();
    private static native long nativeGetTotalGLCalls();
    private static native long nativeGetDedupedCalls();
    private static native long nativeGetDeferredTextures();
    private static native void nativeDrainTextureQueue(int maxUploads);

    // ═══ Méthodes JNI natives — pipeline async (Java Mixin → C++) ═══

    /**
     * Soumet une texture pour compression async côté C++.
     * Le C++ copie les pixels du IntBuffer immédiatement (zéro-copie via GetDirectBufferAddress),
     * puis lance un worker thread pour la compression ETC2.
     * Le résultat est mis dans la queue MPSC interne.
     *
     * @return true si la soumission a été acceptée (queue pas pleine, format OK)
     */
    public static native boolean nativeSubmitAsyncCompress(
        int target, int level, int internalFormat,
        int width, int height, int border,
        int format, int type,
        IntBuffer pixels
    );

    /**
     * Compression synchrone (bloquante).
     */
    public static native boolean nativeCompressAndUploadSync(
        int target, int level, int internalFormat,
        int width, int height, int border,
        int format, int type,
        IntBuffer pixels
    );

    /**
     * Drain la queue de résultats compressés.
     * DOIT être appelé sur le GL thread (render thread).
     * Appelle glCompressedTexImage2D pour chaque résultat prêt.
     *
     * @param maxUploads nombre max d'uploads par appel (ex: 3)
     * @param maxTimeUs budget temps max en microsecondes (ex: 2000 = 2ms)
     * @return nombre de textures effectivement uploadées
     */
    public static native int nativeDrainCompressedQueue(int maxUploads, int maxTimeUs);

    /**
     * @return nombre de résultats compressés en attente dans la queue
     */
    public static native int nativeGetPendingCount();

    // ═══ API publique ═══

    /**
     * Installe les hooks GL legacy (PLT).
     * Utile en complément des Mixins pour le state dedup et les stats.
     */
    public static synchronized void install() {
        if (installed || !NativeLib.isLoaded()) return;

        try {
            installed = nativeInstallHooks();
            if (installed) {
                NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Hooks GL installés avec succès");
            } else {
                NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Installation des hooks GL échouée (handle LWJGL non trouvé ?)");
            }
        } catch (UnsatisfiedLinkError e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] JNI hooks GL non disponible");
        }
    }

    /**
     * Désinstalle les hooks GL legacy.
     */
    public static synchronized void uninstall() {
        if (!installed) return;

        try {
            nativeUninstallHooks();
            installed = false;
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Hooks GL désinstallés");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    /**
     * Soumet une texture pour compression async.
     * Appelé par GlStateManagerMixin sur le render thread.
     *
     * @return true si accepté → le Mixin doit ci.cancel()
     */
    public static boolean submitAsyncCompress(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, IntBuffer pixels) {
        if (!NativeLib.isLoaded()) return false;
        try {
            boolean accepted = nativeSubmitAsyncCompress(
                target, level, internalFormat,
                width, height, border,
                format, type, pixels
            );
            if (accepted) {
                asyncSubmitted++;
            }
            return accepted;
        } catch (UnsatisfiedLinkError e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] nativeSubmitAsyncCompress non disponible");
            return false;
        }
    }

    /**
     * Compression synchrone.
     * Appelé par GlStateManagerMixin pour les petites textures en jeu.
     */
    public static boolean compressAndUploadSync(
            int target, int level, int internalFormat,
            int width, int height, int border,
            int format, int type, IntBuffer pixels) {
        if (!NativeLib.isLoaded()) return false;
        try {
            boolean accepted = nativeCompressAndUploadSync(
                target, level, internalFormat,
                width, height, border,
                format, type, pixels
            );
            if (accepted) {
                // On incrémente asyncUploaded même si c'est sync pour garder la stat simple
                asyncUploaded++;
            }
            return accepted;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Drain la queue de textures compressées (appeler chaque tick sur le GL thread).
     * @param maxUploads nombre max d'uploads (ex: 3)
     * @param maxTimeUs budget temps max en µs (ex: 2000 = 2ms)
     */
    public static void drainCompressedQueue(int maxUploads, int maxTimeUs) {
        if (!NativeLib.isLoaded()) return;
        try {
            int uploaded = nativeDrainCompressedQueue(maxUploads, maxTimeUs);
            asyncUploaded += uploaded;
        } catch (UnsatisfiedLinkError ignored) {}
    }

    /**
     * Drain la queue legacy de textures différées (ancien pipeline).
     */
    public static void drainTextureQueue(int maxUploads) {
        if (!installed) return;
        try {
            nativeDrainTextureQueue(maxUploads);
        } catch (UnsatisfiedLinkError ignored) {}
    }

    /**
     * Met à jour les stats depuis le natif.
     */
    public static void refreshStats() {
        if (!NativeLib.isLoaded()) return;
        try {
            if (installed) {
                totalGLCalls = nativeGetTotalGLCalls();
                dedupedCalls = nativeGetDedupedCalls();
                deferredTextures = nativeGetDeferredTextures();
            }
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static void incrementSubImageCount() {
        subImageCount++;
    }

    // ═══ Getters ═══

    public static boolean isInstalled() { return installed; }
    public static long getTotalGLCalls() { return totalGLCalls; }
    public static long getDedupedCalls() { return dedupedCalls; }
    public static long getDeferredTextures() { return deferredTextures; }
    public static long getSubImageCount() { return subImageCount; }
    public static long getAsyncSubmitted() { return asyncSubmitted; }
    public static long getAsyncUploaded() { return asyncUploaded; }

    public static int getDedupPercent() {
        if (totalGLCalls == 0) return 0;
        return (int) ((dedupedCalls * 100) / totalGLCalls);
    }

    public static int getPendingCount() {
        if (!NativeLib.isLoaded()) return 0;
        try {
            return nativeGetPendingCount();
        } catch (UnsatisfiedLinkError e) {
            return 0;
        }
    }
}
