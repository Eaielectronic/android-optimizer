package fr.eaielectronic.nativeglengine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Bridge JNI vers le compilateur de shaders natif (shader_compiler.cpp).
 * 
 * Si la bibliothèque native n'est pas chargée, les méthodes retournent
 * le source GLSL tel quel (mode passthrough).
 */
public final class ShaderCompilerBridge {

    /** Pool de threads dédié à la compilation shader (max 2 threads pour ne pas surcharger le CPU mobile) */
    private static final ExecutorService COMPILER_POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int count = 0;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NativeGLEngine-ShaderCompiler-" + (count++));
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY); // Priorité basse pour ne pas bloquer le jeu
            return t;
        }
    });

    private ShaderCompilerBridge() {}

    // ═══ Méthodes JNI natives ═══
    // Ces méthodes sont implémentées dans jni_bridge.cpp
    // Elles ne sont disponibles que si NativeLib.isLoaded() == true

    /**
     * Compile du GLSL vers du SPIR-V binaire.
     * @param glslSource le code source GLSL
     * @param shaderType 0=VERTEX, 1=FRAGMENT, 2=GEOMETRY, 3=COMPUTE
     * @param socVendor 0=UNKNOWN, 1=QUALCOMM, 2=ARM_MALI, 3=MEDIATEK, 4=SAMSUNG, 5=GOOGLE_TENSOR
     * @return le bytecode SPIR-V, ou null en cas d'erreur
     */
    private static native byte[] nativeCompileGLSLtoSPIRV(String glslSource, int shaderType, int socVendor);

    /**
     * Convertit du SPIR-V vers du ESSL (GLSL for ES).
     * @param spirvBytes le bytecode SPIR-V
     * @return le source ESSL, ou null en cas d'erreur
     */
    private static native String nativeConvertSPIRVtoESSL(byte[] spirvBytes);

    /**
     * Retourne la version du driver GPU.
     */
    private static native String nativeGetDriverVersion();

    // ═══ API publique (avec fallback Java) ═══

    /**
     * Compile un shader GLSL de manière synchrone.
     * Utilise le pipeline natif si disponible, sinon retourne le GLSL tel quel.
     */
    public static byte[] compileGLSLtoSPIRV(String glslSource, int shaderType) {
        if (!NativeLib.isLoaded()) return null;

        try {
            int socVendor = AndroidOptBridge.getSocVendor().ordinal();
            return nativeCompileGLSLtoSPIRV(glslSource, shaderType, socVendor);
        } catch (UnsatisfiedLinkError e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] JNI compileGLSLtoSPIRV non disponible");
            return null;
        }
    }

    /**
     * Convertit du SPIR-V vers du ESSL.
     */
    public static String convertSPIRVtoESSL(byte[] spirvBytes) {
        if (!NativeLib.isLoaded() || spirvBytes == null) return null;

        try {
            return nativeConvertSPIRVtoESSL(spirvBytes);
        } catch (UnsatisfiedLinkError e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] JNI convertSPIRVtoESSL non disponible");
            return null;
        }
    }

    /**
     * Retourne la version du driver GPU détectée via l'API native.
     */
    public static String getDriverVersion() {
        if (!NativeLib.isLoaded()) return "unknown";

        try {
            String version = nativeGetDriverVersion();
            return version != null ? version : "unknown";
        } catch (UnsatisfiedLinkError e) {
            return "unknown";
        }
    }

    /**
     * Lance une compilation asynchrone (hors render thread).
     * Le callback est appelé sur le thread du pool quand la compilation est terminée.
     * 
     * @param glslSource code GLSL source
     * @param shaderType type du shader (0=VERT, 1=FRAG, 2=GEOM, 3=COMP)
     * @param hash hash SHA-256 pour le cache
     * @param onComplete callback avec le SPIR-V compilé (ou null si erreur)
     */
    public static void compileAsync(String glslSource, int shaderType, String hash,
                                     Consumer<byte[]> onComplete) {
        COMPILER_POOL.submit(() -> {
            try {
                NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Starting async compile for shader {}", hash.substring(0, 8));
                byte[] spirv = compileGLSLtoSPIRV(glslSource, shaderType);
                if (spirv != null) {
                    NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Async compile SUCCESS for shader {}", hash.substring(0, 8));
                    // Sauvegarder dans le cache disque
                    ShaderCacheManager.saveSpirvToDisk(hash, spirv);
                } else {
                    NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Async compile FAILED (native STUB returned null?) for shader {}", hash.substring(0, 8));
                }
                onComplete.accept(spirv);
            } catch (Exception e) {
                NativeGLEngineMod.LOGGER.error("[NativeGLEngine] Compilation async exception: {}", e.getMessage(), e);
                onComplete.accept(null);
            }
        });
    }
}
