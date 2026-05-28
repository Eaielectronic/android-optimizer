package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class EntityThrottler {

    private static final double THROTTLE_DIST_SQ = 32.0 * 32.0;
    private static final int    SCAN_INTERVAL    = 20;

    private static int  ticks   = 0;
    private static int  skipped = 0;
    private static int  logTick = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.ENTITY_THROTTLER.get()) return;

        ticks++;
        logTick++;

        if (logTick >= 1200) {
            if (skipped > 0)
                AndroidOptMod.LOGGER.debug("[AndroidOpt] EntityThrottler : {} animations gelées/min", skipped);
            logTick = 0;
            skipped = 0;
        }

        if (ticks % SCAN_INTERVAL != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof Player) continue;

            double distSq = living.distanceToSqr(mc.player);
            if (distSq > THROTTLE_DIST_SQ) {
                living.walkAnimation.setSpeed(0f);
                skipped++;
            }
        }
    }
}
