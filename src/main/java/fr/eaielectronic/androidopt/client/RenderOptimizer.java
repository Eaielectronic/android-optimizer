package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class RenderOptimizer {

    public static void apply(FMLClientSetupEvent event) {
        if (!OptConfig.isActive() || !OptConfig.RENDER_OPTS.get()) return;

        event.enqueueWork(() -> {
            Minecraft mc   = Minecraft.getInstance();
            Options   opts = mc.options;

            // Distance de rendu
            int rd = OptConfig.RENDER_DISTANCE.get();
            if (rd > 0 && opts.renderDistance().get() > rd)
                opts.renderDistance().set(rd);

            // Simulation distance
            if (rd > 0 && opts.simulationDistance().get() > rd + 1)
                opts.simulationDistance().set(rd + 1);

            // Particles
            if (OptConfig.MINIMAL_PARTICLES.get())
                opts.particles().set(ParticleStatus.MINIMAL);

            // Nuages — CloudStatus.OFF
            if (OptConfig.DISABLE_CLOUDS.get())
                opts.cloudStatus().set(CloudStatus.OFF);

            // Biome blend
            opts.biomeBlendRadius().set(0);

            // Ambient occlusion
            if (OptConfig.DISABLE_AMBIENT_OCCLUSION.get())
                opts.ambientOcclusion().set(false);

            // Entity shadows
            if (OptConfig.DISABLE_ENTITY_SHADOWS.get())
                opts.entityShadows().set(false);

            // Mipmap
            opts.mipmapLevels().set(OptConfig.MIPMAP_LEVELS.get());

            opts.entityDistanceScaling().set(0.5);

            // Graphics FAST
            opts.graphicsMode().set(GraphicsStatus.FAST);


            // Sauvegarde les options
            opts.save();

            // automatiquement au prochain chargement de monde.

            AndroidOptMod.LOGGER.info("[AndroidOpt] ✓ RenderOptimizer appliqué (options sauvegardées).");
        });
    }

    private RenderOptimizer() {}
}
