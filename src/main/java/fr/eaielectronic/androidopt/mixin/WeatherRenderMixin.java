package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LevelRenderer.class)
public class WeatherRenderMixin {

    @Inject(
        method = "renderSnowAndRain",
        at = @At("HEAD"),
        cancellable = true,
        require = 0  // silencieux si la méthode est renommée
    )
    private void androidopt$skipWeatherRender(CallbackInfo ci) {
        if (!ConfigGuard.isReady()) return;
        if (!ConfigGuard.getBool(OptConfig.WEATHER_SUPPRESSOR, true)) return;

        ci.cancel();
    }
}
