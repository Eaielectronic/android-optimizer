package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Level.class)
public class CreateParticleMixin {

    @Inject(
        method = "addParticle",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void androidopt$filterCreateParticles(
            ParticleOptions particleData,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            CallbackInfo ci) {

        if (!ConfigGuard.isReady()) return;
        if (!ConfigGuard.getBool(OptConfig.CREATE_PARTICLES_FILTER, true)) return;

        // Filtre les particules Create (namespace "create")
        try {
            net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                    .getKey(particleData.getType());
            if (key != null && "create".equals(key.getNamespace())) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }
}
