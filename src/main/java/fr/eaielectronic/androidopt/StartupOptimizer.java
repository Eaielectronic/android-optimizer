package fr.eaielectronic.androidopt;


public final class StartupOptimizer {

    static {
        if (AndroidDetector.IS_ANDROID) {
            setIfAbsent("fml.earlyprogresswindow", "false");

            // Réduit les logs pour accélérer le démarrage
            setIfAbsent("forge.logging.console.level", "warn");

            // Évite l'init AWT inutile sur Android
            setIfAbsent("java.awt.headless", "true");

            // Skip le wizard de premier démarrage
            setIfAbsent("fml.skipFirstTimeSetup", "true");

            setIfAbsent("minecraft.telemetry.disabled", "true");


            setIfAbsent("java.lang.ref.SoftReference.maxAge", "2000");

            setIfAbsent("java.lang.Integer.IntegerCache.high", "256");

            AndroidOptMod.LOGGER.info("[AndroidOpt] StartupOptimizer : propriétés système appliquées.");
            AndroidOptMod.LOGGER.info("[AndroidOpt] CONSEIL : Pour éliminer les freezes GC, ajouter dans les JVM args du launcher :");
            AndroidOptMod.LOGGER.info("[AndroidOpt]   -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M");
            AndroidOptMod.LOGGER.info("[AndroidOpt]   -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20");
        }
    }

    
    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private StartupOptimizer() {}
}
