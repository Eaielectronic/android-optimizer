package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import fr.eaielectronic.androidopt.client.AndroidVolumeDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class AmbientSoundSuppressor {

    private static final int CHECK_INTERVAL = 200; // 10s
    private static int ticks = 0;
    private static boolean applied = false;
    private static double originalAmbient = -1;
    private static double originalWeather = -1;
    private static double originalMusic   = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!OptConfig.isActive() || !OptConfig.AMBIENT_SOUND_SUPPRESSOR.get()) {
            if (applied) {
                restoreVolumes(mc);
                applied = false;
            }
            return;
        }

        if (++ticks < CHECK_INTERVAL) return;
        ticks = 0;

        if (!applied) {
            applyVolumes(mc);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!OptConfig.isActive()) return;
        if (!OptConfig.AMBIENT_SOUND_SUPPRESSOR.get()) return;

        var sound = event.getSound();
        if (sound == null) return;

        String path = sound.getLocation().getPath();
        if (path.startsWith("ambient.cave") || path.startsWith("ambient.underwater")) {
            event.setSound(null); // annule le son
        }
    }

    private static void applyVolumes(Minecraft mc) {
        if (AndroidVolumeDetector.isSystemMuted()) {
            applied = true;
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] AmbientSoundSuppressor : systeme deja muet — volumes Minecraft inchanges.");
            return;
        }

        // Sauvegarde les volumes originaux
        originalAmbient = mc.options.getSoundSourceVolume(SoundSource.AMBIENT);
        originalWeather = mc.options.getSoundSourceVolume(SoundSource.WEATHER);
        originalMusic   = mc.options.getSoundSourceVolume(SoundSource.MUSIC);

        // Coupe AMBIENT (cave sounds, sons de biome)
        setVolume(mc, SoundSource.AMBIENT, 0.0);
        // Coupe WEATHER (pluie, tonnerre)
        setVolume(mc, SoundSource.WEATHER, 0.0);
        // Reduit MUSIC a 30%
        if (originalMusic > 0.3) {
            setVolume(mc, SoundSource.MUSIC, 0.3);
        }

        mc.options.save();
        applied = true;
        AndroidOptMod.LOGGER.info(
            "[AndroidOpt] AmbientSoundSuppressor : AMBIENT=0, WEATHER=0, MUSIC=30%.");
    }

    private static void restoreVolumes(Minecraft mc) {
        if (originalAmbient >= 0) setVolume(mc, SoundSource.AMBIENT, originalAmbient);
        if (originalWeather >= 0) setVolume(mc, SoundSource.WEATHER, originalWeather);
        if (originalMusic >= 0)   setVolume(mc, SoundSource.MUSIC, originalMusic);
        mc.options.save();
        AndroidOptMod.LOGGER.info("[AndroidOpt] AmbientSoundSuppressor : volumes originaux restaures.");
        
        originalAmbient = -1;
        originalWeather = -1;
        originalMusic = -1;
    }

    private static void setVolume(Minecraft mc, SoundSource source, double value) {
        try {
            mc.options.getSoundSourceOptionInstance(source).set(value);
        } catch (Exception e) {
            AndroidOptMod.LOGGER.warn(
                "[AndroidOpt] AmbientSoundSuppressor : impossible de modifier {} : {}",
                source.getName(), e.getMessage());
        }
    }
}
