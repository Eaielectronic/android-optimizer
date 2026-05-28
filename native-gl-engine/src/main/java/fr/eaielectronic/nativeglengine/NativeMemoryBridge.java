package fr.eaielectronic.nativeglengine;

/**
 * Bridge JNI vers le gestionnaire de mémoire natif (native_memory.cpp).
 * 
 * Utilise VMA (Vulkan Memory Allocator) + VK_EXT_memory_budget
 * pour obtenir le budget GPU réel sur Android.
 * 
 * Si la .so n'est pas chargée, les méthodes retournent des estimations
 * basées sur Runtime.getRuntime() (moins précis mais fonctionnel).
 */
public final class NativeMemoryBridge {

    private static boolean initialized = false;

    private NativeMemoryBridge() {}

    // ═══ Méthodes JNI natives ═══

    private static native boolean nativeInit();
    private static native long nativeGetGPUBudget();
    private static native long nativeGetGPUUsage();
    private static native void nativeSetVerbose(boolean verbose);
    private static native float nativeGetGPUPressure();
    private static native long nativeGetSystemAvailableMB();
    private static native int nativeGetTemperature();
    private static native boolean nativeIsThermalThrottling();
    private static native void nativeDestroy();
    
    // NEW: Sync config from Java to C++
    private static native void nativeUpdateConfig(int gpuBudget, boolean texCompress, boolean vertexQuant);

    // ═══ API publique ═══

    public static void setVerboseLogging(boolean verbose) {
        if (initialized) {
            nativeSetVerbose(verbose);
        }
    }

    public static synchronized void init() {
        if (initialized) return;

        if (NativeLib.isLoaded()) {
            try {
                initialized = nativeInit();
                if (initialized) {
                    NativeGLEngineMod.LOGGER.info("[NativeGLEngine] NativeMemoryManager (VMA) initialisé");
                } else {
                    NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] nativeInit() a retourné false — Vulkan non disponible ?");
                }
            } catch (UnsatisfiedLinkError e) {
                NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] JNI NativeMemoryManager non disponible");
            }
        }
    }

    /**
     * Récupère un rapport complet sur l'état de la mémoire GPU et système.
     */
    public static MemoryReport getReport() {
        if (initialized) {
            try {
                return new MemoryReport(
                    nativeGetGPUBudget(),
                    nativeGetGPUUsage(),
                    nativeGetGPUPressure(),
                    nativeGetSystemAvailableMB(),
                    nativeGetTemperature(),
                    nativeIsThermalThrottling()
                );
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }

        // Fallback Java : estimation basée sur le heap JVM
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        float pressure = (float) usedMem / maxMem;

        return new MemoryReport(
            maxMem,           // approximation du budget GPU = heap max
            usedMem,          // approximation de l'usage GPU = heap utilisé
            pressure,         // pression estimée
            -1,               // sys available inconnu en Java
            -1,               // température inconnue sans natif
            false             // pas de throttling détecté
        );
    }

    public static boolean isNativeInitialized() { return initialized; }

    public static boolean isThermalThrottling() {
        if (!initialized) return false;
        try { return nativeIsThermalThrottling(); }
        catch (UnsatisfiedLinkError e) { return false; }
    }
    
    public static void updateConfig(int gpuBudget, boolean texCompress, boolean vertexQuant) {
        if (!initialized) return;
        try { nativeUpdateConfig(gpuBudget, texCompress, vertexQuant); }
        catch (UnsatisfiedLinkError ignored) {}
    }

    public static void destroy() {
        if (initialized) {
            try {
                nativeDestroy();
            } catch (UnsatisfiedLinkError ignored) {}
            initialized = false;
        }
    }
}
