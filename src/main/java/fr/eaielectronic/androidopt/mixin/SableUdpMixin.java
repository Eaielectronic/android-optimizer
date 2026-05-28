package fr.eaielectronic.androidopt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "dev.ryanhcode.sable.network.packets.tcp.ClientboundSableUDPActivationPacket", remap = false)
public class SableUdpMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void androidopt$blockUdpActivation(CallbackInfo ci) {
        if (!fr.eaielectronic.androidopt.ConfigGuard.isReady() || !fr.eaielectronic.androidopt.OptConfig.SABLE_UDP_FIX.get()) return;

        // Annuler silencieusement la requête d'activation UDP
        ci.cancel();
    }
}
