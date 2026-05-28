package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class ChunkUnloader {

    private static final int INTERVAL = 1800;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.CHUNK_UNLOADER.get()) return;

        if (++ticks < INTERVAL) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        int renderDist = mc.options.renderDistance().get();
        level.getChunkSource().updateViewRadius(renderDist);
        AndroidOptMod.LOGGER.debug("[AndroidOpt] ChunkUnloader : vue rechargée (renderDist={})", renderDist);
    }
}
