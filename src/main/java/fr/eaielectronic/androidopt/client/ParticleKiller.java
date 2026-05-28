package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.Collection;
import java.util.Map;

@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class ParticleKiller {

    private static final int MAX_PARTICLES  = 50;
    private static final int CHECK_INTERVAL = 100;

    private static int   ticks           = 0;
    private static Field particlesField  = null;
    private static boolean reflectionFailed = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.MINIMAL_PARTICLES.get()) return;
        if (reflectionFailed) return;

        if (++ticks < CHECK_INTERVAL) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ParticleEngine engine = mc.particleEngine;
        if (engine == null) return;

        try {
            if (particlesField == null) {
                for (Field f : ParticleEngine.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        particlesField = f;
                        break;
                    }
                }
                if (particlesField == null) { reflectionFailed = true; return; }
            }

            @SuppressWarnings("unchecked")
            Map<?, Queue<Particle>> particleMap =
                (Map<?, Queue<Particle>>) particlesField.get(engine);

            int total = particleMap.values().stream().mapToInt(Collection::size).sum();
            if (total > MAX_PARTICLES) {
                int toRemove = total - MAX_PARTICLES;
                int removed  = 0;
                outer:
                for (Queue<Particle> queue : particleMap.values()) {
                    while (!queue.isEmpty() && removed < toRemove) {
                        queue.poll();
                        removed++;
                        if (removed >= toRemove) break outer;
                    }
                }
                AndroidOptMod.LOGGER.debug("[AndroidOpt] ParticleKiller : {}/{} → {} supprimées", total, MAX_PARTICLES, removed);
            }
        } catch (Exception e) {
            AndroidOptMod.LOGGER.warn("[AndroidOpt] ParticleKiller désactivé : {}", e.getMessage());
            reflectionFailed = true;
        }
    }
}
