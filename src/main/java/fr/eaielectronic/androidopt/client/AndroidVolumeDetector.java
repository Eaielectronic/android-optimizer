package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


public class AndroidVolumeDetector {

    private static final long CACHE_DURATION_NS = 5_000_000_000L; // 5s

    private static boolean cachedMuted     = false;
    private static long    lastCheckNano   = 0L;
    private static boolean initLogged      = false;

    
    public static boolean isSystemMuted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            float master = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
            if (master <= 0f) return true;
        }

        // Sur PC, on ne lit pas getprop
        if (!AndroidDetector.IS_ANDROID) return false;

        long now = System.nanoTime();
        if (now - lastCheckNano < CACHE_DURATION_NS) {
            return cachedMuted;
        }
        lastCheckNano = now;

        boolean muted = checkAndroidVolume();
        if (muted != cachedMuted || !initLogged) {
            initLogged = true;
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] AndroidVolumeDetector : système {} (muet={})",
                muted ? "MUET" : "actif", muted);
        }
        cachedMuted = muted;
        return muted;
    }

    
    private static boolean checkAndroidVolume() {
        String ringerMode = readProp("audio.ringer.mode");
        if (!ringerMode.isEmpty()) {
            try {
                return Integer.parseInt(ringerMode.trim()) == 0;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private static String readProp(String prop) {
        try {
            ProcessBuilder pb = new ProcessBuilder("getprop", prop);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean done = proc.waitFor(150, TimeUnit.MILLISECONDS);
            if (!done) { proc.destroyForcibly(); return ""; }
            return new String(proc.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private AndroidVolumeDetector() {}
}
