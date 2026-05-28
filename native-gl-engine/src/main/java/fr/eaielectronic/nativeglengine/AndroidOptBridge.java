package fr.eaielectronic.nativeglengine;

import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridge vers Android Optimizer.
 * 
 * Détecte la présence du mod androidopt et accède à ses données par réflexion
 * pour éviter toute dépendance de compilation dure.
 * 
 * Similaire à l'approche Sable ↔ Aeronautics : les deux mods fonctionnent
 * indépendamment mais se connectent s'ils sont tous les deux présents.
 */
public final class AndroidOptBridge {

    private static boolean initialized = false;
    private static boolean androidOptPresent = false;

    // Cache des valeurs récupérées par réflexion
    private static String socName = "unknown";
    private static int bigCoreCount = 4;
    private static String bigCoreName = "unknown";
    private static boolean thermalMonitorActive = false;
    private static Object thermalMonitorCfg = null;
    private static Object thermalWarningCfg = null;
    private static Object thermalCriticalCfg = null;
    private static Object verboseLogCfg = null;
    private static Object gpuBudgetCfg = null;
    private static Object texCompressCfg = null;
    private static Object vertexQuantCfg = null;
    private static Method forgeConfigGetMethod = null;

    private AndroidOptBridge() {}

    /**
     * Initialise le bridge. Appelé une seule fois au commonSetup.
     * Détecte androidopt et récupère les données par réflexion.
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        androidOptPresent = ModList.get().isLoaded("androidopt");
        if (!androidOptPresent) return;

        try {
            // Accéder au SocDetector d'Android Optimizer
            Class<?> socDetectorClass = Class.forName("fr.eaielectronic.androidopt.SocDetector");

            // Lire DETECTED_PROFILE (public static final)
            Field profileField = socDetectorClass.getField("DETECTED_PROFILE");
            Object profile = profileField.get(null);

            if (profile != null) {
                Class<?> profileClass = profile.getClass();

                // Lire les champs du SocProfile
                Field nameField = profileClass.getField("name");
                socName = (String) nameField.get(profile);

                Field bigCountField = profileClass.getField("bigCoreCount");
                bigCoreCount = bigCountField.getInt(profile);

                Field bigNameField = profileClass.getField("bigCoreName");
                bigCoreName = (String) bigNameField.get(profile);
            }

            // Accéder à OptConfig pour les seuils thermiques
            Class<?> optConfigClass = Class.forName("fr.eaielectronic.androidopt.OptConfig");

            try {
                Field thermalMonitorField = optConfigClass.getField("THERMAL_MONITOR");
                thermalMonitorCfg = thermalMonitorField.get(null);
                forgeConfigGetMethod = thermalMonitorCfg.getClass().getMethod("get");

                Field warningField = optConfigClass.getField("THERMAL_WARNING_TEMP");
                thermalWarningCfg = warningField.get(null);

                Field criticalField = optConfigClass.getField("THERMAL_CRITICAL_TEMP");
                thermalCriticalCfg = criticalField.get(null);

                Field verboseField = optConfigClass.getField("DEBUG_VERBOSE_LOG");
                verboseLogCfg = verboseField.get(null);

                try {
                    Field gpuField = optConfigClass.getField("NATIVE_GL_GPU_BUDGET_PERCENT");
                    gpuBudgetCfg = gpuField.get(null);

                    Field texField = optConfigClass.getField("NATIVE_GL_TEX_COMPRESS");
                    texCompressCfg = texField.get(null);

                    Field vertField = optConfigClass.getField("NATIVE_GL_VERTEX_QUANT");
                    vertexQuantCfg = vertField.get(null);
                } catch (NoSuchFieldException ignored) {
                    // Ancienne version sans options NativeGL
                }
            } catch (NoSuchFieldException e) {
                // Version ancienne d'androidopt sans thermal — utiliser les valeurs par défaut
                NativeGLEngineMod.LOGGER.debug("[NativeGLEngine] androidopt sans ThermalMonitor, défauts utilisés");
            }

            NativeGLEngineMod.LOGGER.info(
                "[NativeGLEngine] Bridge androidopt OK — SoC={}, BigCores={}x{}",
                socName, bigCoreCount, bigCoreName
            );

        } catch (Exception e) {
            NativeGLEngineMod.LOGGER.warn(
                "[NativeGLEngine] Bridge androidopt échoué (version incompatible ?) : {}",
                e.getMessage()
            );
            // On continue en mode autonome avec les valeurs par défaut
        }
    }

    // ═══ Getters publics ═══

    public static boolean isAndroidOptPresent() { return androidOptPresent; }
    public static String getSocName() { return socName; }
    public static int getBigCoreCount() { return bigCoreCount; }
    public static String getBigCoreName() { return bigCoreName; }
    public static boolean isThermalMonitorActive() {
        if (thermalMonitorCfg != null && forgeConfigGetMethod != null) {
            try { return (Boolean) forgeConfigGetMethod.invoke(thermalMonitorCfg); } catch (Exception ignored) {}
        }
        return false;
    }
    public static int getThermalWarningTemp() {
        if (thermalWarningCfg != null && forgeConfigGetMethod != null) {
            try { return (Integer) forgeConfigGetMethod.invoke(thermalWarningCfg); } catch (Exception ignored) {}
        }
        return 55; // Default fallback to 55 instead of 42
    }
    public static int getThermalCriticalTemp() {
        if (thermalCriticalCfg != null && forgeConfigGetMethod != null) {
            try { return (Integer) forgeConfigGetMethod.invoke(thermalCriticalCfg); } catch (Exception ignored) {}
        }
        return 65;
    }
    public static boolean isVerboseLogActive() {
        if (verboseLogCfg != null && forgeConfigGetMethod != null) {
            try { return (Boolean) forgeConfigGetMethod.invoke(verboseLogCfg); } catch (Exception ignored) {}
        }
        return false;
    }
    public static int getGpuBudgetPercent() {
        if (gpuBudgetCfg != null && forgeConfigGetMethod != null) {
            try { return (Integer) forgeConfigGetMethod.invoke(gpuBudgetCfg); } catch (Exception ignored) {}
        }
        return 75; // Default fallback
    }
    public static boolean isTexCompressActive() {
        if (texCompressCfg != null && forgeConfigGetMethod != null) {
            try { return (Boolean) forgeConfigGetMethod.invoke(texCompressCfg); } catch (Exception ignored) {}
        }
        return true;
    }
    public static boolean isVertexQuantActive() {
        if (vertexQuantCfg != null && forgeConfigGetMethod != null) {
            try { return (Boolean) forgeConfigGetMethod.invoke(vertexQuantCfg); } catch (Exception ignored) {}
        }
        return true;
    }

    /**
     * Retourne le vendor SoC simplifié pour les optimisations shader.
     */
    public static SocVendor getSocVendor() {
        String lower = socName.toLowerCase();
        if (lower.contains("snapdragon") || lower.contains("adreno")) return SocVendor.QUALCOMM;
        if (lower.contains("dimensity") || lower.contains("mediatek")) return SocVendor.MEDIATEK;
        if (lower.contains("exynos")) return SocVendor.SAMSUNG;
        if (lower.contains("tensor")) return SocVendor.GOOGLE_TENSOR;
        if (lower.contains("mali")) return SocVendor.ARM_MALI;
        if (lower.contains("kirin")) return SocVendor.HUAWEI;
        return SocVendor.UNKNOWN;
    }

    public enum SocVendor {
        QUALCOMM,
        ARM_MALI,
        MEDIATEK,
        SAMSUNG,
        GOOGLE_TENSOR,
        HUAWEI,
        UNKNOWN
    }
}
