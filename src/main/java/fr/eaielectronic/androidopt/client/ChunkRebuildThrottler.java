package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class ChunkRebuildThrottler {

    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!OptConfig.isActive() || !OptConfig.CHUNK_REBUILD_THROTTLER.get()) return;

        if (++ticks % 600 != 0) return; // log toutes les 30s

        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;

        // Le throttle réel est dans ChunkRebuildMixin
        AndroidOptMod.LOGGER.debug(
            "[AndroidOpt] ChunkRebuildThrottler : actif (throttle via Mixin, max 2 rebuilds/frame)");
    }
}
