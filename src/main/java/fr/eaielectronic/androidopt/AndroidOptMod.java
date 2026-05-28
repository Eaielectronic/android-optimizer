package fr.eaielectronic.androidopt;

import fr.eaielectronic.androidopt.client.AndroidOptClient;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AndroidOptMod.MODID)
public class AndroidOptMod {

    public static final String MODID  = "androidopt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AndroidOptMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, OptConfig.SPEC);
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            new AndroidOptClient(modEventBus, modContainer);
            modEventBus.addListener(this::onRegisterReloadListeners);
        }
    }

    private void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
            (preparationBarrier, resourceManager, prepProfiler, reloadProfiler,
             backgroundExecutor, gameExecutor) ->
                preparationBarrier.wait(null).thenRunAsync(() -> {
                    SocDetector.init(resourceManager);
                    SocDetector.applyRecommendedSettings();
                }, gameExecutor)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (AndroidDetector.IS_ANDROID) {
            LOGGER.info("[AndroidOpt] ✓ Android ARM64={} — optimisations actives.", AndroidDetector.IS_ARM64);
        } else {
            try {
                if (OptConfig.SPEC.isLoaded() && OptConfig.FORCE_ENABLE.get()) {
                    LOGGER.info("[AndroidOpt] ✓ Mode PC forcé (forceEnable=true) — optimisations actives.");
                } else {
                    LOGGER.info("[AndroidOpt] Pas Android, forceEnable=false — mod en mode passif.");
                    LOGGER.info("[AndroidOpt] Pour tester sur PC : config/androidopt-client.toml → forceEnable = true");
                }
            } catch (Throwable t) {
                LOGGER.info("[AndroidOpt] Config pas encore chargée au commonSetup, attente du clientSetup.");
            }
        }
    }
}
