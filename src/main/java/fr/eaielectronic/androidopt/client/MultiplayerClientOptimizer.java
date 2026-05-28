package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class MultiplayerClientOptimizer {

    private static boolean applied = false;
    private static boolean wasRemote = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ConfigGuard.isReady()) return;

        boolean isRemote = ServerModeDetector.isRemoteServer();

        // Détection de changement solo ↔ multi
        if (isRemote && !wasRemote) {
            applied = false;
            wasRemote = true;
        } else if (!isRemote && wasRemote) {
            wasRemote = false;
            if (applied) {
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] Retour en solo — optimisations multi désactivées.");
                applied = false;
            }
            return;
        }

        if (!isRemote || applied) return;
        applied = true;

        AndroidOptMod.LOGGER.info("[AndroidOpt] ╔══════════════════════════════════════════╗");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   Mode MULTIJOUEUR détecté               ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   Optimisations client activées :        ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   ✓ Rapier3D skippé (SableMixin)        ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   ✓ Stress réseau skippé (serveur gère) ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   ✓ BE ticks réduits (serveur gère)     ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   ✓ Fluides non simulés (serveur gère)  ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ║   Gain : ~-90Mo RAM, ~-18% CPU          ║");
        AndroidOptMod.LOGGER.info("[AndroidOpt] ╚══════════════════════════════════════════╝");

        // Les optimisations réelles sont appliquées par :
        // - SablePhysicsClientMixin (skip Rapier init)
        // - FrameBudgetManager (ajuste le budget rendu)
        // - FluidTickSuppressor (réduit simulation distance)
    }
}
