package fr.eaielectronic.nativeglengine;

/**
 * Bridge JNI vers l'intercepteur GL natif (gl_interceptor.cpp).
 * 
 * Installe des hooks PLT sur les fonctions OpenGL critiques de LWJGL
 * pour intercepter les appels AVANT qu'ils atteignent MobileGlues.
 * 
 * Hooks principaux :
 * - glShaderSource / glCompileShader : redirige vers le ShaderCompiler natif
 * - glTexImage2D : queue les uploads si le budget GPU est tendu
 * - glEnable / glDisable : filtre les appels redondants (state dedup)
 * - glDrawArrays / glDrawElements : compteurs de stats
 */
public final class GLInterceptorBridge {

    private static boolean installed = false;

    // Stats (mises à jour depuis le natif)
    private static long totalGLCalls = 0;
    private static long dedupedCalls = 0;
    private static long deferredTextures = 0;

    private GLInterceptorBridge() {}

    // ═══ Méthodes JNI natives ═══

    private static native boolean nativeInstallHooks();
    private static native void nativeUninstallHooks();
    private static native long nativeGetTotalGLCalls();
    private static native long nativeGetDedupedCalls();
    private static native long nativeGetDeferredTextures();
    private static native void nativeDrainTextureQueue(int maxUploads);

    /**
     * Tente de compresser et d'uploader une texture (ASTC/ETC2).
     * @return true si la texture a été gérée par le compresseur natif, false sinon.
     */
    public static native boolean nativeInterceptTexImage2D(
            int target, int level, int internalformat,
            int width, int height, int format, int type, long pixelsPtr);

    // ═══ API publique ═══

    /**
     * Installe les hooks GL. Doit être appelé APRÈS l'initialisation du contexte GL.
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
     * Désinstalle les hooks GL.
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
     * Drain la queue de textures différées (appeler entre les frames).
     * @param maxUploads nombre max d'uploads à traiter (ex: 3)
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
        if (!installed) return;
        try {
            totalGLCalls = nativeGetTotalGLCalls();
            dedupedCalls = nativeGetDedupedCalls();
            deferredTextures = nativeGetDeferredTextures();
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static boolean isInstalled() { return installed; }
    public static long getTotalGLCalls() { return totalGLCalls; }
    public static long getDedupedCalls() { return dedupedCalls; }
    public static long getDeferredTextures() { return deferredTextures; }

    /**
     * @return pourcentage d'appels GL économisés par la déduplication
     */
    public static int getDedupPercent() {
        if (totalGLCalls == 0) return 0;
        return (int) ((dedupedCalls * 100) / totalGLCalls);
    }
}
