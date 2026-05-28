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
            Path libFile = tempDir.resolve("lib" + LIB_NAME + ".so");

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
}
