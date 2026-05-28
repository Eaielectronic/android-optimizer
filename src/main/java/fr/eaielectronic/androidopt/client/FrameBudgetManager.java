package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class FrameBudgetManager {

    public enum BudgetLevel { NORMAL, REDUCED, LOW, CRITICAL }

    // Seuils en nanosecondes
    private static final long THRESHOLD_REDUCED  = 33_000_000L;  // 33ms
    private static final long THRESHOLD_LOW      = 50_000_000L;  // 50ms
    private static final long THRESHOLD_CRITICAL = 100_000_000L; // 100ms

    // Moyenne glissante sur 10 frames
    private static final int   WINDOW_SIZE = 10;
    private static final long[] frameTimes = new long[WINDOW_SIZE];
    private static int frameIndex = 0;
    private static long frameStartNano = 0L;

    
    public static volatile BudgetLevel currentLevel = BudgetLevel.NORMAL;

    // Distance de rendu originale (avant réduction d'urgence)
    private static int originalRenderDistance = -1;
    private static boolean renderDistanceReduced = false;

    // Log périodique
    private static int logTick = 0;

    
    public static void onFrameStart() {
        frameStartNano = System.nanoTime();
    }

    
    public static void onFrameEnd() {
        if (frameStartNano == 0L) return;

        long frameTime = System.nanoTime() - frameStartNano;
        frameTimes[frameIndex % WINDOW_SIZE] = frameTime;
        frameIndex++;

        // Calcule la moyenne glissante
        long sum = 0;
        int count = Math.min(frameIndex, WINDOW_SIZE);
        for (int i = 0; i < count; i++) {
            sum += frameTimes[i];
        }
        long avgFrameTime = sum / count;

        // Met à jour le niveau de budget
        BudgetLevel newLevel;
        if (avgFrameTime >= THRESHOLD_CRITICAL || ThermalMonitor.isCritical()) {
            newLevel = BudgetLevel.CRITICAL;
        } else if (avgFrameTime >= THRESHOLD_LOW) {
            newLevel = BudgetLevel.LOW;
        } else if (avgFrameTime >= THRESHOLD_REDUCED || ThermalMonitor.isOverheating()) {
            newLevel = BudgetLevel.REDUCED;
        } else {
            newLevel = BudgetLevel.NORMAL;
        }

        if (newLevel != currentLevel) {
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] FrameBudget : {} → {} (avg frame: {}ms)",
                currentLevel, newLevel, avgFrameTime / 1_000_000);
            currentLevel = newLevel;
        }
    }

    
    public static boolean shouldCullDistantBER() {
        return currentLevel != BudgetLevel.NORMAL;
    }

    
    public static boolean shouldThrottleEntities() {
        return currentLevel == BudgetLevel.LOW || currentLevel == BudgetLevel.CRITICAL;
    }

    
    public static boolean isCritical() {
        return currentLevel == BudgetLevel.CRITICAL;
    }

    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive()) return;

        logTick++;
        if (logTick % 200 == 0) {
            long avgMs = 0;
            int count = Math.min(frameIndex, WINDOW_SIZE);
            if (count > 0) {
                long sum = 0;
                for (int i = 0; i < count; i++) sum += frameTimes[i];
                avgMs = (sum / count) / 1_000_000;
            }
            if (currentLevel != BudgetLevel.NORMAL) {
                AndroidOptMod.LOGGER.debug(
                    "[AndroidOpt] FrameBudget : niveau={}, avgFrame={}ms",
                    currentLevel, avgMs);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (currentLevel == BudgetLevel.CRITICAL) {
            // Sauvegarde la distance originale
            if (originalRenderDistance < 0) {
                originalRenderDistance = mc.options.renderDistance().get();
            }
            if (!renderDistanceReduced) {
                int current = mc.options.renderDistance().get();
                int reduced = Math.max(2, current - 1);
                if (reduced < current) {
                    mc.options.renderDistance().set(reduced);
                    renderDistanceReduced = true;
                    AndroidOptMod.LOGGER.warn(
                        "[AndroidOpt] FrameBudget CRITIQUE : renderDistance {} → {} (frame >100ms)",
                        current, reduced);
                }
            }
        } else if (renderDistanceReduced && currentLevel == BudgetLevel.NORMAL) {
            if (originalRenderDistance > 0) {
                mc.options.renderDistance().set(originalRenderDistance);
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] FrameBudget : renderDistance restaurée à {}", originalRenderDistance);
            }
            renderDistanceReduced = false;
            originalRenderDistance = -1;
        }
    }

    
    public static String getBudgetLevelDisplay() {
        return switch (currentLevel) {
            case NORMAL   -> "§aNORMAL";
            case REDUCED  -> "§eREDUCED";
            case LOW      -> "§6LOW";
            case CRITICAL -> "§cCRITICAL";
        };
    }

    private FrameBudgetManager() {}
}
