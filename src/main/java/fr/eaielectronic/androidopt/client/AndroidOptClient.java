package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public class AndroidOptClient {

    public AndroidOptClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onClientSetup);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (container, modListScreen) -> new OptConfigScreen(modListScreen));

        NeoForge.EVENT_BUS.register(FrameBudgetManager.class);
        // Nouveaux handlers P1/P2/P3
        NeoForge.EVENT_BUS.register(TextureCacheEvictor.class);
        NeoForge.EVENT_BUS.register(AmbientSoundSuppressor.class);
        NeoForge.EVENT_BUS.register(StaticBERBatcher.class);
        NeoForge.EVENT_BUS.register(ServerModeDetector.class);
        NeoForge.EVENT_BUS.register(CreateCacheCleanupHandler.class);
        NeoForge.EVENT_BUS.register(ThermalMonitor.class);

        // Export du rapport de session a la deconnexion
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            FreezeDebugger.exportReport();
        });
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        fr.eaielectronic.androidopt.ConfigGuard.markLoaded();

        if (!OptConfig.isActive()) return;

        // Applique les options graphiques
        RenderOptimizer.apply(event);

        // Optimisations JEI
        JeiCacheLimiter.apply();

        SodiumCompanion.init();
        
        if (net.neoforged.fml.ModList.get().isLoaded("embeddium")) {
            fr.eaielectronic.androidopt.integration.sodium.EmbeddiumConfigRegistrar.register();
        }

        AndroidOptMod.LOGGER.info("[AndroidOpt] ╔══════════════════════════════════════╗");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║     Android Optimizer — ACTIF        ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  forceEnable : {}                 ║", OptConfig.FORCE_ENABLE.get() ? "OUI" : "NON");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Rendu réduit + FAST               ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Particles MINIMAL                 ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ GC préventif                      ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Entity throttle                   ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Frame budget adaptatif            ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ BER culling (Mixin)               ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Chunk rebuild throttle (Mixin)    ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ Thread affinity ARM               ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║  ✓ HUD overlay actif                 ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ╚══════════════════════════════════════╝");
    }
}
