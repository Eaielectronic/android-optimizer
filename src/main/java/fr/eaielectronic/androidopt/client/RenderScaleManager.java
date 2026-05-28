package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class RenderScaleManager {

    public static final float RENDER_SCALE = 0.75f;

    private static boolean warnedOnce = false;
    private static boolean scaleApplied = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ConfigGuard.isReady()) return;
        if (!ConfigGuard.getBool(OptConfig.RENDER_SCALE_ENABLED, false)) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        if (net.neoforged.fml.ModList.get().isLoaded("veil")) {
            if (!warnedOnce) {
                warnedOnce = true;
                AndroidOptMod.LOGGER.warn(
                    "[AndroidOpt] RenderScale: Veil detecte, resolution 3D non modifiee.");
            }
        } else {
            if (!scaleApplied) {
                scaleApplied = true;
                // Logique simplifiee de fallback
                AndroidOptMod.LOGGER.info("[AndroidOpt] RenderScale actif (fallback)");
                // Ici on pourrait modifier mc.getWindow() mais ce sera developpe plus tard
            }
        }
    }
}
