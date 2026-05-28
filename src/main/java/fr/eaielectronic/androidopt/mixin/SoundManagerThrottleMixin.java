package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.client.AndroidVolumeDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.eaielectronic.androidopt.OptConfig;


@Mixin(SoundManager.class)
public class SoundManagerThrottleMixin {

    private static int tickCounter = 0;
    private static final int THROTTLE_INTERVAL = 3;

    @Inject(
        method = "tick",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void androidopt$optimizeSoundTick(boolean isGamePaused, CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;
        if (isGamePaused) return;

        // Niveau 1 : skip total si muet
        if (ConfigGuard.getBool(OptConfig.AMBIENT_SOUND_SUPPRESSOR, true)
                && AndroidVolumeDetector.isSystemMuted()) {
            ci.cancel();
            return;
        }

        // Niveau 2 : throttle sur serveur distant
        if (ConfigGuard.getBool(OptConfig.AMBIENT_SOUND_SUPPRESSOR, true)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getCurrentServer() != null) {
                tickCounter++;
                if (tickCounter % THROTTLE_INTERVAL != 0) {
                    ci.cancel();
                }
            }
        }
    }
}
