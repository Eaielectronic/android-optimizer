package fr.eaielectronic.nativeglengine;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de cache shader à deux niveaux :
 * - L1 : ConcurrentHashMap en mémoire (~1µs accès)
 * - L2 : Fichiers .spv/.essl sur disque (~5ms accès)
 * 
 * Le hash SHA-256 est calculé sur : source_glsl + soc_name + driver_version + mc_version.
 * Ainsi deux appareils différents ont des caches distincts même pour le même shader.
 * 
 * Invalidation automatique quand driver, MC, ou version du mod changent.
 */
public final class ShaderCacheManager {

    // Cache L1 : hash → OpenGL shader ID
    private static final ConcurrentHashMap<String, Integer> memoryCache = new ConcurrentHashMap<>();

    // Répertoire de cache disque
    private static Path cacheDir;
    private static boolean initialized = false;

    private ShaderCacheManager() {}

    /**
     * Initialise le cache disque dans le répertoire du jeu.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            // CRITIQUE : utiliser FMLPaths.GAMEDIR.get() au lieu de Minecraft.getInstance().gameDirectory
            // car init() est maintenant appelé dans le constructeur du mod, bien AVANT que
            // Minecraft.getInstance() soit disponible.
            Path gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
            cacheDir = gameDir.resolve("nativeglengine_shader_cache");

            // Créer les sous-répertoires
            java.nio.file.Files.createDirectories(cacheDir.resolve("vulkan"));
            java.nio.file.Files.createDirectories(cacheDir.resolve("gles"));

            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Cache shader initialisé : {}", cacheDir);
        } catch (java.io.IOException e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Impossible de créer le cache shader : {}", e.getMessage());
            cacheDir = null;
        }
    }

    /**
     * Calcule le hash SHA-256 unique pour un shader dans son contexte hardware.
     */
    public static String computeHash(String glslSource, String socName, String driverVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(glslSource.getBytes());
            digest.update(socName.getBytes());
            digest.update(driverVersion.getBytes());
            digest.update("mc1.21.1".getBytes());
            digest.update(("ngle" + NativeGLEngineMod.MOD_ID).getBytes());

            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est toujours disponible dans la JVM
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ═══ Cache L1 (Mémoire) ═══

    /**
     * Cherche un shader dans le cache mémoire.
     * @return l'ID OpenGL du shader, ou null si absent
     */
    public static Integer getFromMemoryCache(String hash) {
        return memoryCache.get(hash);
    }

    /**
     * Stocke un shader dans le cache mémoire.
     */
    public static void putToMemoryCache(String hash, int shaderId) {
        memoryCache.put(hash, shaderId);
    }

    /**
     * Vide le cache mémoire (appelé en cas de pression mémoire).
     */
    public static void trimMemoryCache() {
        int size = memoryCache.size();
        memoryCache.clear();
        NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Cache shader mémoire vidé ({} entrées)", size);
    }

    /**
     * @return nombre d'entrées dans le cache mémoire
     */
    public static int getMemoryCacheSize() {
        return memoryCache.size();
    }

    // ═══ Cache L2 (Disque) ═══

    /**
     * Cherche un shader compilé sur le disque.
     * @return l'ID OpenGL après upload, ou null si absent
     */
    public static Integer loadFromDisk(String hash) {
        if (cacheDir == null) return null;

        // Chercher dans le cache GLES (fallback Java)
        Path esslPath = cacheDir.resolve("gles").resolve(hash + ".essl");
        if (Files.exists(esslPath)) {
            try {
                String esslSource = Files.readString(esslPath);
                // Le source ESSL pré-converti est stocké.
                // On ne peut pas retourner un shader ID ici car on n'a pas de contexte GL.
                // Le Mixin devra utiliser ce source pour compiler via GL.
                NativeGLEngineMod.LOGGER.debug("[NativeGLEngine] Cache disque HIT (ESSL) : {}", hash.substring(0, 16));
                return null; // TODO: intégrer avec le contexte GL pour upload
            } catch (IOException e) {
                NativeGLEngineMod.LOGGER.debug("[NativeGLEngine] Erreur lecture cache disque : {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Sauvegarde le source ESSL pré-converti sur le disque pour un accès futur.
     */
    public static void saveToDisk(String hash, String esslSource) {
        if (cacheDir == null) {
            NativeGLEngineMod.LOGGER.error("[NativeGLEngine] saveToDisk failed: cacheDir is null!");
            return;
        }

        try {
            Path esslPath = cacheDir.resolve("gles").resolve(hash + ".essl");
            Files.writeString(esslPath, esslSource);
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Cache disque WRITE OK (ESSL): {}", esslPath.toString());
        } catch (Exception e) {
            NativeGLEngineMod.LOGGER.error("[NativeGLEngine] Erreur écriture cache disque: {}", e.getMessage(), e);
        }
    }

    /**
     * Sauvegarde un binaire SPIR-V sur le disque.
     */
    public static void saveSpirvToDisk(String hash, byte[] spirvBytes) {
        if (cacheDir == null) {
            NativeGLEngineMod.LOGGER.error("[NativeGLEngine] saveSpirvToDisk failed: cacheDir is null!");
            return;
        }

        try {
            Path spvPath = cacheDir.resolve("vulkan").resolve(hash + ".spv");
            Files.write(spvPath, spirvBytes);
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] SPIR-V cache WRITE OK: {} ({} bytes)", spvPath.toString(), spirvBytes.length);
        } catch (Exception e) {
            NativeGLEngineMod.LOGGER.error("[NativeGLEngine] Erreur écriture SPIR-V: {}", e.getMessage(), e);
        }
    }

    /**
     * Charge un binaire SPIR-V depuis le disque.
     */
    public static byte[] loadSpirvFromDisk(String hash) {
        if (cacheDir == null) return null;

        Path spvPath = cacheDir.resolve("vulkan").resolve(hash + ".spv");
        if (Files.exists(spvPath)) {
            try {
                return Files.readAllBytes(spvPath);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * @return nombre d'entrées dans le cache disque (tous backends confondus)
     */
    public static int getDiskCacheSize() {
        if (cacheDir == null) return 0;
        int count = 0;
        try {
            for (String subdir : new String[]{"vulkan", "gles"}) {
                Path dir = cacheDir.resolve(subdir);
                if (Files.exists(dir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                        for (Path entry : stream) {
                            if (!entry.getFileName().toString().endsWith(".meta.json")) {
                                count++;
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
        return count;
    }

    /**
     * Supprime tout le cache disque.
     */
    public static void clearDiskCache() {
        if (cacheDir == null) return;
        try {
            for (String subdir : new String[]{"vulkan", "gles"}) {
                Path dir = cacheDir.resolve(subdir);
                if (Files.exists(dir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                        for (Path entry : stream) {
                            Files.deleteIfExists(entry);
                        }
                    }
                }
            }
            NativeGLEngineMod.LOGGER.info("[NativeGLEngine] Cache disque shader vidé");
        } catch (IOException e) {
            NativeGLEngineMod.LOGGER.warn("[NativeGLEngine] Erreur vidage cache : {}", e.getMessage());
        }
    }
}
