package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "net.createmod.catnip.render.SuperByteBufferCache", remap = false)
public class CreateRenderThrottleMixin {
    private static long lastUpdate = 0L;

    @Inject(method = "invalidate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void androidopt$throttle(CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;

        int fps = ConfigGuard.getInt(OptConfig.CREATE_RENDER_FPS, 20);
        long interval = 1000L / Math.max(1, fps);
        long now = System.currentTimeMillis();

        if (now - lastUpdate < interval) {
            ci.cancel();
            return;
        }
        lastUpdate = now;
    }
}
