package fr.eaielectronic.androidopt;


public final class ConfigGuard {

    
    private static boolean isConfigLoaded() {
        try {
            return OptConfig.SPEC.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static void markLoaded() {
        AndroidOptMod.LOGGER.info("[AndroidOpt] ConfigGuard : SPEC.isLoaded()={}, Mixins activés.",
            isConfigLoaded());
    }

    
    public static boolean isReady() {
        if (!isConfigLoaded()) return false;
        try {
            return OptConfig.isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static boolean getBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value, boolean defaultValue) {
        if (!isConfigLoaded()) return defaultValue;
        try {
            return value.get();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    
    public static int getInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int defaultValue) {
        if (!isConfigLoaded()) return defaultValue;
        try {
            return value.get();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private ConfigGuard() {}
}
