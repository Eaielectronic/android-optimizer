package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.SocDetector;
import fr.eaielectronic.androidopt.SocProfile;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SodiumCompanion {

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        boolean hasSodium = ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium");
        if (!hasSodium) return;

        try {
            // Tentative d'accès aux options Sodium via réflexion pour éviter une dépendance dure
            Class<?> sodiumClass = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            Method optionsMethod = sodiumClass.getMethod("options");
            Object sodiumOptions = optionsMethod.invoke(null);

            if (sodiumOptions != null) {
                Class<?> optionsClass = sodiumOptions.getClass();

                // setAnimateOnlyVisibleTextures
                try {
                    Field animateOnlyVisibleTexturesField = getFieldIgnoreCase(optionsClass, "animateOnlyVisibleTextures");
                    if (animateOnlyVisibleTexturesField != null) {
                        animateOnlyVisibleTexturesField.setAccessible(true);
                        animateOnlyVisibleTexturesField.set(sodiumOptions, true);
                        AndroidOptMod.LOGGER.info("[AndroidOpt] Sodium Companion: animateOnlyVisibleTextures forced to true");
                    }
                } catch (Exception ignored) {}

                // setChunkRenderThreads
                try {
                    SocProfile profile = SocDetector.DETECTED_PROFILE;
                    int optimalThreads = Math.max(1, profile.bigCoreCount - 1);
                    Field renderThreadsField = getFieldIgnoreCase(optionsClass, "chunkBuilderThreads");
                    if (renderThreadsField != null) {
                        renderThreadsField.setAccessible(true);
                        renderThreadsField.set(sodiumOptions, optimalThreads);
                        AndroidOptMod.LOGGER.info("[AndroidOpt] Sodium Companion: chunkBuilderThreads adjusted to {}", optimalThreads);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AndroidOptMod.LOGGER.debug("[AndroidOpt] Sodium Companion: Could not adjust Sodium settings (may be using a different fork)");
        }
    }

    private static Field getFieldIgnoreCase(Class<?> clazz, String fieldName) {
        for (Field f : clazz.getFields()) {
            if (f.getName().equalsIgnoreCase(fieldName)) return f;
        }
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(fieldName)) return f;
        }
        return null;
    }
}
