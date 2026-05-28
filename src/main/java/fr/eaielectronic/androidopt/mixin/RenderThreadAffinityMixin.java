package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.client.ArmThreadAffinity;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public class RenderThreadAffinityMixin {

    private static boolean affinityApplied = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void androidopt$applyAffinity(CallbackInfo ci) {
        if (!affinityApplied) {
            affinityApplied = true;
            ArmThreadAffinity.applyToCurrentThread();
        }
    }
}
