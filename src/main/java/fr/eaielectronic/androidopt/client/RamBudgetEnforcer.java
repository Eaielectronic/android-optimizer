package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class RamBudgetEnforcer {

    private static final long NATIVE_OVERHEAD_MB = 250;
    private static final int RECHECK_INTERVAL = 6000; // 5 minutes

    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ConfigGuard.isReady()) return;

        if (++ticks < RECHECK_INTERVAL && ticks != 1) return;
        if (ticks >= RECHECK_INTERVAL) ticks = 0;

        long currentXmx = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long maxTotal = ConfigGuard.getInt(OptConfig.MAX_TOTAL_RAM_MB, 2700);
        long safeXmx = maxTotal - NATIVE_OVERHEAD_MB;

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        AndroidOptMod.LOGGER.info("[AndroidOpt] RAM Budget | total={}Mo overhead={}Mo safeXmx={}Mo currentXmx={}Mo used={}Mo",
            maxTotal, NATIVE_OVERHEAD_MB, safeXmx, currentXmx, usedMB);

        if (currentXmx > safeXmx) {
            AndroidOptMod.LOGGER.warn("[AndroidOpt] DANGER : Xmx {} > safe {} Mo — risque de kill par Android OOM",
                currentXmx, safeXmx);
        }
    }
}
