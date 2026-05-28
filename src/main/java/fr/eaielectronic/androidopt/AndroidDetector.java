package fr.eaielectronic.androidopt;


public final class AndroidDetector {

    
    public static final boolean IS_ANDROID;

    
    public static final boolean IS_ARM64;

    static {
        String osVersion  = System.getProperty("os.version",   "").toLowerCase();
        String javaVendor = System.getProperty("java.vendor",  "").toLowerCase();
        String javaVmName = System.getProperty("java.vm.name", "").toLowerCase();
        String arch       = System.getProperty("os.arch",      "").toLowerCase();

        IS_ANDROID = osVersion.contains("android")
                  || javaVendor.contains("android")
                  || javaVmName.contains("android")
                  || javaVmName.contains("dalvik");   // fallback ART

        IS_ARM64 = arch.equals("aarch64") || arch.equals("arm64");

        if (IS_ANDROID) {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] Android ARM64={} détecté (os.version='{}', arch='{}')",
                IS_ARM64, osVersion, arch);

            StartupOptimizer.class.getName(); // force le chargement de la classe
        }
    }

    private AndroidDetector() {}
}
