package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class MemoryWatchdog {

    private static int ticks = 0;
    private static int consecutiveHighHeap = 0;
    public static int preventiveGcCount = 0;
    
    private static Boolean isFerriteLoaded = null;
    private static long lastSoftPurgeMs = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.MEMORY_WATCHDOG.get()) return;

        if (isFerriteLoaded == null) {
            isFerriteLoaded = net.neoforged.fml.ModList.get().isLoaded("ferritecore");
        }

        int checkInterval = ConfigGuard.isReady()
            ? ConfigGuard.getInt(OptConfig.GC_CHECK_INTERVAL, 100)
            : 100;

        // En mode CRITICAL, on force un check chaque tick
        if (FrameBudgetManager.isCritical()) {
            checkInterval = 1;
        }

        if (++ticks < checkInterval) return;
        ticks = 0;

        Runtime rt   = Runtime.getRuntime();
        long maxMem  = rt.maxMemory();
        long usedMem = rt.totalMemory() - rt.freeMemory();
        double ratio = (double) usedMem / maxMem;
        long usedMB  = usedMem / (1024 * 1024);
        long maxMB   = maxMem  / (1024 * 1024);

        int defaultThreshold = maxMB < 2500 ? 75 : maxMB < 4000 ? 80 : 85;
        double softThreshold = ConfigGuard.isReady()
            ? ConfigGuard.getInt(OptConfig.GC_THRESHOLD_PERCENT, defaultThreshold) / 100.0
            : defaultThreshold / 100.0;

        double hardThreshold = ConfigGuard.isReady()
            ? ConfigGuard.getInt(OptConfig.GC_CRITICAL_PERCENT, 88) / 100.0
            : 0.88;

        // Force l'adaptation si l'utilisateur a laisse la valeur par defaut de 65 qui est trop agressive
        if (softThreshold <= 0.65) {
            softThreshold = defaultThreshold / 100.0;
        }

        if (Boolean.FALSE.equals(isFerriteLoaded)) {
            softThreshold = Math.min(softThreshold, 0.58);
        }

        if (ratio >= hardThreshold) {
            consecutiveHighHeap++;
            preventiveGcCount++;
            
            // On ne fait SURTOUT PAS System.gc() car c'est ca qui cause les freezes Stop-The-World
            // On purge juste agressivement les caches de mods pour soulager la JVM
            long now = System.currentTimeMillis();
            if (now - lastSoftPurgeMs >= 5000) { 
                lastSoftPurgeMs = now;
                CreateCacheCleanupHandler.forceCacheCleanup();
                TextureCacheEvictor.forceEvict();
            }

        } else if (ratio >= softThreshold || FrameBudgetManager.isCritical()) {
            consecutiveHighHeap = 0;
            // Ne pas forcer le GC manuellement pour eviter de bloquer le thread.
            // On declenche plutot un nettoyage intelligent des caches !
            long now = System.currentTimeMillis();
            if (now - lastSoftPurgeMs >= 15000) { // Executer au max toutes les 15 secondes
                lastSoftPurgeMs = now;
                AndroidOptMod.LOGGER.debug(
                    "[AndroidOpt] Heap {}/{} MB ({}%) — Purge intelligente des caches",
                    usedMB, maxMB, String.format("%.0f", ratio * 100));
                
                CreateCacheCleanupHandler.forceCacheCleanup();
                TextureCacheEvictor.forceEvict();
            }
        } else {
            consecutiveHighHeap = 0;
        }
    }
}
