package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.client.FrameBudgetManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LevelRenderer.class)
public class RenderBudgetMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void androidopt$frameStart(CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;
        FrameBudgetManager.onFrameStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void androidopt$frameEnd(CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;
        FrameBudgetManager.onFrameEnd();
    }
}
