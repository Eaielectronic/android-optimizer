package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class FluidTickSuppressor {

    private static final int INTERVAL = 600; // 30 s
    private static int ticks = 0;
    private static boolean applied = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive()) {
            applied = false;
            return;
        }

        if (++ticks < INTERVAL) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // (les fluides lointains ne sont plus animés)
        int currentSim = mc.options.simulationDistance().get();
        int renderDist = mc.options.renderDistance().get();
        int targetSim  = Math.max(5, Math.min(currentSim, renderDist + 1));

        if (currentSim != targetSim) {
            mc.options.simulationDistance().set(targetSim);
            mc.options.save();
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] FluidTickSuppressor : simulationDistance {} → {}",
                currentSim, targetSim);
        }

        if (!applied) {
            applied = true;
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] FluidTickSuppressor : simulation distance réduite à {} chunks.",
                targetSim);
        }
    }
}
