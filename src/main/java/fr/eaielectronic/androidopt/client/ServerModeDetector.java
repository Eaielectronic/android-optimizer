package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class ServerModeDetector {

    public enum Mode { SOLO, REMOTE_SERVER, UNKNOWN }

    public static volatile Mode currentMode = Mode.UNKNOWN;
    private static Mode lastLoggedMode = Mode.UNKNOWN;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        detectMode(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        detectMode(Minecraft.getInstance());
        // Forcer le GC + purge texture tout de suite avant l'arrivee massive des chunks
        System.gc();
        AndroidOptMod.LOGGER.info("[AndroidOpt] ServerModeDetector : Connexion detectee, garbage collection preventif effectue.");
    }

    private static void detectMode(Minecraft mc) {
        if (mc.level == null && mc.getConnection() == null) {
            currentMode = Mode.UNKNOWN;
            return;
        }

        Mode newMode;
        if (mc.hasSingleplayerServer()) {
            newMode = Mode.SOLO;
        } else if (mc.getConnection() != null) {
            newMode = Mode.REMOTE_SERVER;
        } else {
            newMode = Mode.UNKNOWN;
        }

        if (newMode != currentMode) {
            currentMode = newMode;
            if (newMode != lastLoggedMode) {
                lastLoggedMode = newMode;
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] ServerModeDetector : mode -> {}", newMode);
            }
        }
    }

    public static boolean isSolo()         { return currentMode == Mode.SOLO; }
    public static boolean isRemoteServer() { return currentMode == Mode.REMOTE_SERVER; }
    public static boolean isInGame()       { return currentMode != Mode.UNKNOWN; }
}
