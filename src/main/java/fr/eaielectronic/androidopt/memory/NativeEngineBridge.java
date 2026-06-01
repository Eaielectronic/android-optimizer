package fr.eaielectronic.androidopt.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Pont de réflexion entre Android Optimizer et NativeGLEngine.
 *
 * Android Optimizer ne dépend PAS de NativeGLEngine (optionnel).
 * Ce bridge utilise la réflexion pour appeler les méthodes natives
 * si NativeGLEngine est installé. Sinon, toutes les méthodes retournent
 * des valeurs par défaut sans crash.
 *
 * Fonctionnalités exposées :
 * - Particules C++ (pool natif zéro-GC)
 * - Thermal Monitor (température CPU)
 * - Memory Purge (mallopt + madvise)
 * - LZ4 Compression (DirectByteBuffer)
 * - Thread Boost (P-Core affinity)
 */
public class NativeEngineBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AndroidOpt");

    private static boolean available = false;
    private static boolean checked = false;

    // Méthodes natives cachées (résolues par réflexion une seule fois)
    private static Method m_spawnParticle;
    private static Method m_tickParticles;
    private static Method m_clearParticles;
    private static Method m_getActiveParticleCount;
    private static Method m_getThermalLevel;
    private static Method m_getCpuTemperature;
    private static Method m_purgeNativeMemory;
    private static Method m_getSystemAvailableMemoryMB;
    private static Method m_lz4CompressHC;
    private static Method m_lz4Decompress;
    private static Method m_lz4CompressBound;
    private static Method m_boostCurrentThread;
    private static Method m_getPCoreCount;

    private NativeEngineBridge() {}

    /**
     * Tente de résoudre la classe NativeLib de NativeGLEngine par réflexion.
     * Appelé une seule fois au démarrage.
     */
    public static synchronized void init() {
        if (checked) return;
        checked = true;

        try {
            Class<?> nativeLib = Class.forName("fr.eaielectronic.nativeglengine.NativeLib");

            // Vérifier que la lib est chargée
            Method isLoaded = nativeLib.getMethod("isLoaded");
            Boolean loaded = (Boolean) isLoaded.invoke(null);
            if (loaded == null || !loaded) {
                LOGGER.info("[NativeEngineBridge] NativeGLEngine trouvé mais lib native non chargée");
                return;
            }

            // Résoudre toutes les méthodes
            m_spawnParticle = nativeLib.getMethod("nativeSpawnParticle",
                    float.class, float.class, float.class,
                    float.class, float.class, float.class,
                    float.class, int.class,
                    int.class, int.class, int.class, int.class);
            m_tickParticles = nativeLib.getMethod("nativeTickParticles", float.class);
            m_clearParticles = nativeLib.getMethod("nativeClearParticles");
            m_getActiveParticleCount = nativeLib.getMethod("nativeGetActiveParticleCount");
            m_getThermalLevel = nativeLib.getMethod("nativeGetThermalLevel");
            m_getCpuTemperature = nativeLib.getMethod("nativeGetCpuTemperature");
            m_purgeNativeMemory = nativeLib.getMethod("nativePurgeNativeMemory");
            m_getSystemAvailableMemoryMB = nativeLib.getMethod("nativeGetSystemAvailableMemoryMB");
            m_lz4CompressHC = nativeLib.getMethod("nativeLz4CompressHC",
                    ByteBuffer.class, int.class, ByteBuffer.class, int.class);
            m_lz4Decompress = nativeLib.getMethod("nativeLz4Decompress",
                    ByteBuffer.class, int.class, ByteBuffer.class, int.class);
            m_lz4CompressBound = nativeLib.getMethod("nativeLz4CompressBound", int.class);
            m_boostCurrentThread = nativeLib.getMethod("nativeBoostCurrentThread");
            m_getPCoreCount = nativeLib.getMethod("nativeGetPCoreCount");

            available = true;
            LOGGER.info("[NativeEngineBridge] Toutes les méthodes natives résolues avec succès");
        } catch (ClassNotFoundException e) {
            LOGGER.info("[NativeEngineBridge] NativeGLEngine non installé (mode Java seul)");
        } catch (Exception e) {
            LOGGER.warn("[NativeEngineBridge] Erreur de résolution : {}", e.getMessage());
        }
    }

    /** @return true si NativeGLEngine est disponible avec la lib native chargée */
    public static boolean isAvailable() { return available; }

    // ═══ Particules ═══

    public static int spawnParticle(float x, float y, float z,
                                     float vx, float vy, float vz,
                                     float maxAge, int texIndex,
                                     int r, int g, int b, int a) {
        if (!available) return -1;
        try {
            return (int) m_spawnParticle.invoke(null, x, y, z, vx, vy, vz, maxAge, texIndex, r, g, b, a);
        } catch (Exception e) { return -1; }
    }

    public static int tickParticles(float dt) {
        if (!available) return 0;
        try { return (int) m_tickParticles.invoke(null, dt); } catch (Exception e) { return 0; }
    }

    public static void clearParticles() {
        if (!available) return;
        try { m_clearParticles.invoke(null); } catch (Exception e) { /* ignore */ }
    }

    public static int getActiveParticleCount() {
        if (!available) return 0;
        try { return (int) m_getActiveParticleCount.invoke(null); } catch (Exception e) { return 0; }
    }

    // ═══ Thermal ═══

    public static int getThermalLevel() {
        if (!available) return 0;
        try { return (int) m_getThermalLevel.invoke(null); } catch (Exception e) { return 0; }
    }

    public static int getCpuTemperature() {
        if (!available) return -1;
        try { return (int) m_getCpuTemperature.invoke(null); } catch (Exception e) { return -1; }
    }

    // ═══ Memory ═══

    public static long purgeNativeMemory() {
        if (!available) return 0;
        try { return (long) m_purgeNativeMemory.invoke(null); } catch (Exception e) { return 0; }
    }

    public static long getSystemAvailableMemoryMB() {
        if (!available) return -1;
        try { return (long) m_getSystemAvailableMemoryMB.invoke(null); } catch (Exception e) { return -1; }
    }

    // ═══ LZ4 ═══

    public static int lz4CompressHC(ByteBuffer src, int srcLen, ByteBuffer dst, int level) {
        if (!available) return -1;
        try { return (int) m_lz4CompressHC.invoke(null, src, srcLen, dst, level); } catch (Exception e) { return -1; }
    }

    public static int lz4Decompress(ByteBuffer src, int srcLen, ByteBuffer dst, int maxDst) {
        if (!available) return -1;
        try { return (int) m_lz4Decompress.invoke(null, src, srcLen, dst, maxDst); } catch (Exception e) { return -1; }
    }

    public static int lz4CompressBound(int srcLen) {
        if (!available) return srcLen + 16;
        try { return (int) m_lz4CompressBound.invoke(null, srcLen); } catch (Exception e) { return srcLen + 16; }
    }

    // ═══ Thread Boost ═══

    public static int boostCurrentThread() {
        if (!available) return -1;
        try { return (int) m_boostCurrentThread.invoke(null); } catch (Exception e) { return -1; }
    }

    public static int getPCoreCount() {
        if (!available) return 0;
        try { return (int) m_getPCoreCount.invoke(null); } catch (Exception e) { return 0; }
    }
}
