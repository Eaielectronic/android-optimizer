package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class FreezeDebugger {

    private static final long FREEZE_THRESHOLD_MS = 100;
    private static final long STUTTER_THRESHOLD_MS = 50;
    private static final int  LOG_INTERVAL_TICKS = 200;

    private static long lastTickNano = 0L;
    private static int tickCounter = 0;
    private static int freezeCount = 0;
    private static int stutterCount = 0;
    private static long worstTickMs = 0;

    // GC tracking
    private static long lastGcCount = 0;
    private static long lastGcTimeMs = 0;

    // Stats periode
    private static long periodMaxTickMs = 0;
    private static long periodTotalTickMs = 0;
    private static int periodTickCount = 0;

    // Detection freeze I/O : 3 freezes UNKNOWN consecutifs -> reduire render distance
    private static int consecutiveUnknownFreezes = 0;
    private static boolean ioThrottleActive = false;
    private static long ioThrottleEndTick = 0;
    private static int savedRenderDistance = -1;

    // Stats globales pour le rapport
    private static int totalGcFreezes = 0;
    private static int totalRenderFreezes = 0;
    private static int totalIoFreezes = 0;
    private static long sessionStartMs = System.currentTimeMillis();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        lastTickNano = System.nanoTime();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (!ConfigGuard.isReady()) return;
        if (!ConfigGuard.getBool(fr.eaielectronic.androidopt.OptConfig.FREEZE_DEBUGGER, true)) return;
        if (lastTickNano == 0L) return;

        long tickMs = (System.nanoTime() - lastTickNano) / 1_000_000;
        tickCounter++;

        // Stats periode
        periodTickCount++;
        periodTotalTickMs += tickMs;
        if (tickMs > periodMaxTickMs) periodMaxTickMs = tickMs;

        // Donnees memoire
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        int heapPercent = (int) ((usedMB * 100) / maxMB);

        // Donnees GC
        long gcCount = 0;
        long gcTimeMs = 0;
        try {
            for (java.lang.management.GarbageCollectorMXBean gc :
                    java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += gc.getCollectionCount();
                gcTimeMs += gc.getCollectionTime();
            }
        } catch (Throwable ignored) {}

        long gcDeltaCount = gcCount - lastGcCount;
        long gcDeltaTimeMs = gcTimeMs - lastGcTimeMs;

        // Detection freeze
        if (tickMs >= FREEZE_THRESHOLD_MS) {
            freezeCount++;
            if (tickMs > worstTickMs) worstTickMs = tickMs;

            String cause = diagnoseCause(tickMs, heapPercent, gcDeltaCount, gcDeltaTimeMs);

            // Tracker les types pour le rapport
            boolean gcHeavy = gcDeltaCount > 0 && gcDeltaTimeMs > 30;
            boolean renderOverload = FrameBudgetManager.isCritical();
            if (gcHeavy) totalGcFreezes++;
            else if (renderOverload) totalRenderFreezes++;
            else totalIoFreezes++;

            // Calcul de la vitesse du joueur (simplifie)
            double speed = 0.0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                speed = mc.player.getDeltaMovement().lengthSqr(); // Approximation simple sans history
            }
            int requiredUnknownFreezes = speed > 0.5 ? 2 : 3;

            // Detection I/O : freeze sans GC et sans render overload
            if (!gcHeavy && !renderOverload && tickMs > 200) {
                consecutiveUnknownFreezes++;
                if (consecutiveUnknownFreezes >= requiredUnknownFreezes && !ioThrottleActive) {
                    activateIoThrottle();
                }
            } else {
                consecutiveUnknownFreezes = 0;
            }

            AndroidOptMod.LOGGER.warn(
                "[AndroidOpt] FREEZE {}ms | Heap {}% ({}/{}MB) | GC +{}runs +{}ms | Budget {} | Cause: {}",
                tickMs, heapPercent, usedMB, maxMB,
                gcDeltaCount, gcDeltaTimeMs,
                FrameBudgetManager.currentLevel, cause);

        } else if (tickMs >= STUTTER_THRESHOLD_MS) {
            stutterCount++;
        } else {
            consecutiveUnknownFreezes = 0;
        }

        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;

        // Gerer la fin du throttle I/O (10s)
        if (ioThrottleActive && tickCounter > ioThrottleEndTick) {
            deactivateIoThrottle();
        }

        // Log periodique
        if (tickCounter % LOG_INTERVAL_TICKS == 0) {
            long avgTickMs = periodTickCount > 0 ? periodTotalTickMs / periodTickCount : 0;
            int chunksPending = getChunkRebuildsPending();

            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] FreezeDebugger | avg={}ms peak={}ms | Heap {}% ({}/{}MB) | "
                + "Freezes={} Stutters={} | Worst={}ms | Budget {} | ChunkQueue={}",
                avgTickMs, periodMaxTickMs, heapPercent, usedMB, maxMB,
                freezeCount, stutterCount, worstTickMs,
                FrameBudgetManager.currentLevel, chunksPending);

            periodMaxTickMs = 0;
            periodTotalTickMs = 0;
            periodTickCount = 0;
        }
    }

    private static void activateIoThrottle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        savedRenderDistance = mc.options.renderDistance().get();
        int reduced = Math.max(2, savedRenderDistance - 2);
        mc.options.renderDistance().set(reduced);
        ioThrottleActive = true;
        ioThrottleEndTick = tickCounter + 200; // 10 seconds

        AndroidOptMod.LOGGER.warn(
            "[AndroidOpt] I/O throttle active : 3 freezes I/O consecutifs, render distance {} -> {} (10s)",
            savedRenderDistance, reduced);
    }

    private static void deactivateIoThrottle() {
        if (savedRenderDistance > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null) {
                mc.options.renderDistance().set(savedRenderDistance);
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] I/O throttle desactive, render distance restauree a {}",
                    savedRenderDistance);
            }
        }
        ioThrottleActive = false;
        savedRenderDistance = -1;
        consecutiveUnknownFreezes = 0;
    }

    private static String diagnoseCause(long tickMs, int heapPercent,
                                         long gcRuns, long gcTimeMs) {
        boolean gcHeavy = gcRuns > 0 && gcTimeMs > 30;
        boolean heapCritical = heapPercent >= 85;
        boolean renderOverload = FrameBudgetManager.isCritical();

        if (gcHeavy && heapCritical && renderOverload) {
            return "[GC THRASH + RENDER OVERLOAD] Heap saturee ET trop de rendu";
        } else if (gcHeavy && heapCritical) {
            return "[GC THRASH] Heap a " + heapPercent + "% — augmenter -Xmx";
        } else if (gcHeavy) {
            return "[GC PAUSE] GC a pris " + gcTimeMs + "ms";
        } else if (renderOverload) {
            return "[RENDER OVERLOAD] Trop de BERs/chunks en une frame";
        } else if (tickMs > 200) {
            boolean isMultiplayer = fr.eaielectronic.androidopt.client.ServerModeDetector.isRemoteServer();
            return isMultiplayer ? "[NETWORK] Paquets serveur (Create/reseau) traites sur render thread" : "[I/O DISK] Lecture stockage lent ou shader compile";
        } else {
            return "[SPIKE] Pic ponctuel — probablement chunk rebuild isole";
        }
    }

    private static int getChunkRebuildsPending() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer == null) return -1;

            for (java.lang.reflect.Field f : mc.levelRenderer.getClass().getDeclaredFields()) {
                if (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object dispatcher = f.get(mc.levelRenderer);
                    if (dispatcher == null) return -1;

                    for (java.lang.reflect.Field df : dispatcher.getClass().getDeclaredFields()) {
                        if (java.util.Collection.class.isAssignableFrom(df.getType())
                                || java.util.Queue.class.isAssignableFrom(df.getType())) {
                            df.setAccessible(true);
                            Object queue = df.get(dispatcher);
                            if (queue instanceof java.util.Collection<?> c) return c.size();
                        }
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public static String getHudSummary() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        int pct = (int) ((usedMB * 100) / maxMB);

        String color = pct >= 90 ? "§c" : pct >= 75 ? "§6" : "§a";
        String ioTag = ioThrottleActive ? " §c[I/O]" : "";
        return String.format("%sHeap %d%% §7| §eFreezes %d §7| §ePeak %dms%s",
            color, pct, freezeCount, worstTickMs, ioTag);
    }

    // Exporte un rapport de session a la deconnexion
    public static void exportReport() {
        long sessionSeconds = (System.currentTimeMillis() - sessionStartMs) / 1000;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path reportPath = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("androidopt_freeze_report_" + timestamp + ".txt");

        Runtime rt = Runtime.getRuntime();
        long maxMB = rt.maxMemory() / (1024 * 1024);

        StringBuilder sb = new StringBuilder();
        sb.append("== Android Optimizer — Freeze Report ==\n");
        sb.append("Date: ").append(timestamp).append("\n");
        sb.append("Session duration: ").append(sessionSeconds).append("s\n");
        sb.append("Max heap: ").append(maxMB).append(" MB\n");
        sb.append("\n--- Freeze Statistics ---\n");
        sb.append("Total freezes (>100ms): ").append(freezeCount).append("\n");
        sb.append("Total stutters (>50ms): ").append(stutterCount).append("\n");
        sb.append("Worst tick: ").append(worstTickMs).append("ms\n");
        sb.append("\n--- Freeze Breakdown ---\n");
        sb.append("GC-caused freezes: ").append(totalGcFreezes).append("\n");
        sb.append("Render-caused freezes: ").append(totalRenderFreezes).append("\n");
        sb.append("I/O-caused freezes: ").append(totalIoFreezes).append("\n");
        sb.append("\n--- Optimizations Efficiency ---\n");
        sb.append("Preventive GCs triggered: ").append(MemoryWatchdog.preventiveGcCount).append("\n");
        sb.append("\n--- Recommendations ---\n");
        if (totalGcFreezes > 5) {
            sb.append("- High GC freezes: lower gcThresholdPercent or reduce -Xmx\n");
        }
        if (totalIoFreezes > 3) {
            sb.append("- I/O freezes detected: storage is slow, reduce render distance\n");
        }
        if (totalRenderFreezes > 5) {
            sb.append("- Render overload: reduce BER cull distance or enable renderScale\n");
        }
        if (freezeCount == 0) {
            sb.append("- No freezes detected. Configuration is optimal.\n");
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(reportPath, sb.toString());
                AndroidOptMod.LOGGER.info("[AndroidOpt] Freeze report saved async: {}", reportPath);
            } catch (IOException e) {
                AndroidOptMod.LOGGER.warn("[AndroidOpt] Failed to write freeze report: {}", e.getMessage());
            }
        });
    }

    private FreezeDebugger() {}
}
