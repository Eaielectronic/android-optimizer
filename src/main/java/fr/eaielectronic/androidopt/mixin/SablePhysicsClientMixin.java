package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.client.ServerModeDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D",
       remap = false)
public class SablePhysicsClientMixin {

    @Inject(
        method = "initialize",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void androidopt$skipRapierInit(CallbackInfo ci) {
        if (ServerModeDetector.isRemoteServer()) {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] Rapier3D init skippé — serveur distant gère la physique. "
                + "Gain : ~40-60 Mo RAM native + ~5-10% CPU.");
            ci.cancel();
        }
    }
}
