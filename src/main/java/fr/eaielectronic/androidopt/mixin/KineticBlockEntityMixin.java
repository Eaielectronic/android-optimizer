package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.memory.offheap.KineticSoABuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.simibubi.create.content.kinetics.base.KineticBlockEntity", remap = false)
public class KineticBlockEntityMixin {
    private int androidopt$soaIndex;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void androidopt$init(CallbackInfo ci) {
        this.androidopt$soaIndex = KineticSoABuffer.INSTANCE.allocateIndex();
    }

    @Inject(method = "setRemoved", at = @At("RETURN"), remap = true, require = 0)
    private void androidopt$onRemoved(CallbackInfo ci) {
        KineticSoABuffer.INSTANCE.freeIndex(this.androidopt$soaIndex);
    }

    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void androidopt$onGetSpeed(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(KineticSoABuffer.INSTANCE.getSpeed(this.androidopt$soaIndex));
    }

    @Inject(method = "setSpeed", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void androidopt$onSetSpeed(float speed, CallbackInfo ci) {
        KineticSoABuffer.INSTANCE.setSpeed(this.androidopt$soaIndex, speed);
        // On laisse le champ d'origine se mettre à jour également pour la compatibilité
    }
}
