package fr.eaielectronic.nativeglengine;

/**
 * Détecte le renderer graphique actif sur le device Android.
 * 
 * Les renderers possibles sont :
 * - MobileGlues : traduction OpenGL → GLES 3.2 (le plus courant sur Android)
 * - GL4ES / HolyGL4ES : OpenGL 2.1 → GLES 2.0 (vieux appareils)
 * - Zink : Vulkan → OpenGL via Mesa (rare sur Android)
 * - ANGLE : GLES → Vulkan/D3D (Google, optionnel dans MobileGlues)
 * - LWJGL Desktop : PC normal (pas d'interception nécessaire)
 * 
 * La détection se fait via les strings GL_VENDOR et GL_RENDERER,
 * qui ne sont disponibles qu'après l'initialisation du contexte EGL.
 */
public final class RendererDetector {

    public enum Renderer {
        MOBILEGLUES("MobileGlues"),
        GL4ES("GL4ES/HolyGL4ES"),
        ZINK("Zink (Mesa Vulkan)"),
        ANGLE("ANGLE (Google)"),
        LWJGL_DESKTOP("LWJGL Desktop"),
        UNKNOWN("Unknown");

        public final String displayName;
        Renderer(String displayName) { this.displayName = displayName; }
    }

    private static Renderer cached = null;

    private RendererDetector() {}

    /**
     * Détecte le renderer actif. Le résultat est mis en cache.
     * Doit être appelé APRÈS l'initialisation du contexte GL (client setup ou premier frame).
     */
    public static Renderer detect() {
        if (cached != null) return cached;

        try {
            // org.lwjgl.opengl.GL11 est disponible dans l'environnement NeoForge
            String vendor = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
            String renderer = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            String version = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);

            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] GL Vendor={}, Renderer={}, Version={}",
                vendor, renderer, version);

            if (vendor == null || renderer == null) {
                cached = Renderer.UNKNOWN;
                return cached;
            }

            // MobileGlues s'identifie dans GL_VERSION (ex: "4.0.0 MobileGlues 1.3.4")
            // et parfois dans GL_RENDERER — on vérifie les deux
            String versionSafe = (version != null) ? version : "";
            if (renderer.contains("MobileGlues") || versionSafe.contains("MobileGlues")) {
                cached = Renderer.MOBILEGLUES;
            } else if (renderer.contains("Zink") || renderer.contains("zink")) {
                cached = Renderer.ZINK;
            } else if (vendor.contains("Google") || renderer.contains("ANGLE")) {
                cached = Renderer.ANGLE;
            } else if (renderer.contains("GL4ES") || renderer.contains("gl4es") ||
                       renderer.contains("HolyGL4ES")) {
                cached = Renderer.GL4ES;
            } else if (vendor.contains("NVIDIA") || vendor.contains("AMD") ||
                       vendor.contains("Intel")) {
                cached = Renderer.LWJGL_DESKTOP;
            } else {
                cached = Renderer.UNKNOWN;
            }

        } catch (Throwable e) {
            NativeGLEngineMod.LOGGER.debug("[NativeGLEngine] GL context non disponible encore : {}", e.getMessage());
            cached = Renderer.UNKNOWN;
        }

        return cached;
    }

    /** Réinitialise le cache (utile si le contexte GL est recréé) */
    public static void reset() {
        cached = null;
    }

    /** @return le renderer détecté, ou UNKNOWN si pas encore détecté */
    public static Renderer getCurrent() {
        return cached != null ? cached : Renderer.UNKNOWN;
    }

    /** @return true si on est sur Android avec un renderer connu */
    public static boolean isAndroidRenderer() {
        Renderer r = getCurrent();
        return r == Renderer.MOBILEGLUES || r == Renderer.GL4ES ||
               r == Renderer.ZINK || r == Renderer.ANGLE;
    }
}
