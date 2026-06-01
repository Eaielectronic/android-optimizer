package fr.eaielectronic.nativeglengine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gère le chargement de la bibliothèque native libNativeGLEngine.so.
 * 
 * La .so est empaquetée dans le JAR sous assets/nativeglengine/native/arm64-v8a/
 * et est extraite vers un fichier temporaire au premier chargement.
 * 
 * Si le chargement échoue (PC, architecture non supportée, NDK absent),
 * le mod continue en mode "Java seul" avec des fonctionnalités réduites.
 */
public final class NativeLib {

    private static boolean loaded = false;
    private static boolean attemptedLoad = false;
    private static String loadError = null;

    private static final String LIB_NAME = "NativeGLEngine";
    private static final String LIB_RESOURCE_PATH = "/assets/nativeglengine/native/arm64-v8a/lib" + LIB_NAME + ".so";
    private static final String BHOOK_RESOURCE_PATH = "/assets/nativeglengine/native/arm64-v8a/libbytehook.so";

    private NativeLib() {}

    /**
     * Tente de charger la bibliothèque native.
     * Thread-safe, ne charge qu'une seule fois.
     */
    public static synchronized void tryLoad() {
        if (attemptedLoad) return;
        attemptedLoad = true;

        // Vérifier si on est sur une architecture ARM64 (Android)
        String osArch = System.getProperty("os.arch", "");
        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isAndroidArm64 = osArch.contains("aarch64") || osArch.contains("arm64");

        if (!isAndroidArm64) {
            loadError = "Architecture non ARM64 (" + osArch + "/" + osName + "), skip chargement natif";
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] {}", loadError);
            return;
        }

        try {
            // Extraire la .so du JAR vers un fichier temporaire
            Path tempDir = Files.createTempDirectory("nativeglengine");
            Path bhookFile = tempDir.resolve("libbytehook.so");
            Path libFile = tempDir.resolve("lib" + LIB_NAME + ".so");

            // 1. Extraire et charger libbytehook.so (dépendance requise)
            try (InputStream is = NativeLib.class.getResourceAsStream(BHOOK_RESOURCE_PATH)) {
                if (is != null) {
                    Files.copy(is, bhookFile, StandardCopyOption.REPLACE_EXISTING);
                    System.load(bhookFile.toAbsolutePath().toString());
                    bhookFile.toFile().deleteOnExit();
                } else {
                    NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] libbytehook.so non trouvée dans le JAR (optionnelle si déjà chargée)");
                }
            }

            // 2. Extraire et charger libNativeGLEngine.so
            try (InputStream is = NativeLib.class.getResourceAsStream(LIB_RESOURCE_PATH)) {
                if (is == null) {
                    loadError = "Bibliothèque native non trouvée dans le JAR : " + LIB_RESOURCE_PATH;
                    NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] {}", loadError);
                    return;
                }
                Files.copy(is, libFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Charger la bibliothèque via chemin absolu
            System.load(libFile.toAbsolutePath().toString());
            loaded = true;
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Bibliothèque native chargée avec succès");

            // Marquer le fichier pour suppression à la fermeture
            libFile.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();

        } catch (UnsatisfiedLinkError e) {
            loadError = "UnsatisfiedLinkError : " + e.getMessage();
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Chargement natif échoué : {}", loadError);
        } catch (IOException e) {
            loadError = "IOException extraction .so : " + e.getMessage();
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Extraction .so échouée : {}", loadError);
        } catch (SecurityException e) {
            loadError = "SecurityException : " + e.getMessage();
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Permissions insuffisantes : {}", loadError);
        }
    }

    /** @return true si la bibliothèque native est chargée et fonctionnelle */
    public static boolean isLoaded() { return loaded; }

    /** @return message d'erreur si le chargement a échoué, null sinon */
    public static String getLoadError() { return loadError; }

    // ════════════════════════════════════════════════════════════
    // V9 — Méthodes natives : Particle Pool (C++)
    // Pool de 4096 particules en RAM native, zéro GC
    // ════════════════════════════════════════════════════════════

    /** Crée une particule dans le pool natif. @return index ou -1 si plein */
    public static native int nativeSpawnParticle(
        float x, float y, float z,
        float vx, float vy, float vz,
        float maxAge, int texIndex,
        int r, int g, int b, int a);

    /** Met à jour toutes les particules (gravité, vélocité, durée de vie).
     *  @return nombre de particules encore vivantes */
    public static native int nativeTickParticles(float deltaTime);

    /** Supprime toutes les particules (ex: changement de dimension) */
    public static native void nativeClearParticles();

    /** @return nombre de particules actives dans le pool */
    public static native int nativeGetActiveParticleCount();

    /** @return nombre total de particules créées depuis le démarrage */
    public static native long nativeGetTotalParticlesSpawned();

    // ════════════════════════════════════════════════════════════
    // V9 — Thermal Monitor (C++)
    // Lecture température CPU via sysfs, sans root
    // ════════════════════════════════════════════════════════════

    /**
     * @return niveau de throttling : 0=froid, 1=tiède, 2=chaud, 3=critique
     */
    public static native int nativeGetThermalLevel();

    /** @return température CPU en degrés Celsius */
    public static native int nativeGetCpuTemperature();

    // ════════════════════════════════════════════════════════════
    // V9 — Memory Purge (C++)
    // mallopt(M_PURGE) + madvise(MADV_DONTNEED)
    // ════════════════════════════════════════════════════════════

    /**
     * Force la libération de la mémoire native inutilisée.
     * @return quantité de RAM récupérée en Ko (estimation)
     */
    public static native long nativePurgeNativeMemory();

    /** @return mémoire système disponible en Mo */
    public static native long nativeGetSystemAvailableMemoryMB();

    // ════════════════════════════════════════════════════════════
    // V9 — LZ4 Compression (C++)
    // Compression/décompression via DirectByteBuffer
    // ════════════════════════════════════════════════════════════

    /**
     * Compresse avec LZ4 HC (meilleur ratio, pour stockage long terme).
     * @param srcBuf DirectByteBuffer source
     * @param srcLen nombre d'octets à compresser
     * @param dstBuf DirectByteBuffer destination (capacité >= lz4CompressBound)
     * @param level niveau HC (1-12, 9 recommandé)
     * @return taille compressée en octets, -1 si erreur
     */
    public static native int nativeLz4CompressHC(
        java.nio.ByteBuffer srcBuf, int srcLen,
        java.nio.ByteBuffer dstBuf, int level);

    /**
     * Compresse avec LZ4 rapide (pour les données qui changent souvent).
     * @param acceleration 1=normal, 2+=plus rapide mais moins compressé
     */
    public static native int nativeLz4CompressFast(
        java.nio.ByteBuffer srcBuf, int srcLen,
        java.nio.ByteBuffer dstBuf, int acceleration);

    /**
     * Décompresse des données LZ4 (ultra-rapide : >2 Go/s sur ARM64).
     * @return nombre d'octets décompressés, -1 si erreur
     */
    public static native int nativeLz4Decompress(
        java.nio.ByteBuffer srcBuf, int srcLen,
        java.nio.ByteBuffer dstBuf, int maxDecompressed);

    /** @return taille max du buffer de destination pour compresser srcLen octets */
    public static native int nativeLz4CompressBound(int srcLen);

    // ════════════════════════════════════════════════════════════
    // V9 — Thread Performance Boost (C++)
    // Bind sur P-Cores via sched_setaffinity
    // ════════════════════════════════════════════════════════════

    /**
     * Booste le thread actuel en le plaçant sur les Performance Cores.
     * @return 0 si succès, -1 si échec
     */
    public static native int nativeBoostCurrentThread();

    /** @return nombre de P-Cores détectés sur cet appareil */
    public static native int nativeGetPCoreCount();
}
