package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "mezz.jei.gui.filter.FilterTextSource",
       remap = false)
public class JeiSearchThrottleMixin {

    private static long lastSearchMs = 0L;
    private static final long THROTTLE_MS = 300L;

    @Inject(
        method = "setFilterText",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void androidopt$throttleJeiSearch(String text, CallbackInfoReturnable<?> cir) {
        if (!ConfigGuard.isReady()) return;

        long now = System.currentTimeMillis();
        if (now - lastSearchMs < THROTTLE_MS) {
            cir.cancel(); 
            return;
        }
        lastSearchMs = now;
    }
}
