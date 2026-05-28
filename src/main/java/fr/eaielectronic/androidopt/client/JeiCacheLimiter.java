package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;


public class JeiCacheLimiter {

    public static void apply() {
        if (!AndroidDetector.IS_ANDROID) return;

        // Propriété standard Java — fonctionne garantie
        String key = "java.util.concurrent.ForkJoinPool.common.parallelism";
        if (System.getProperty(key) == null) {
            System.setProperty(key, "2");
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] JeiCacheLimiter : ForkJoinPool.commonPool limité à 2 threads.");
        } else {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] JeiCacheLimiter : ForkJoinPool déjà configuré ({}).",
                System.getProperty(key));
        }
    }

    private JeiCacheLimiter() {}
}
