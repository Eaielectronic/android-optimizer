package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class TextureCacheEvictor {

    private static final int  EVICT_INTERVAL = 2400; // 2 min (20 ticks/s * 120s)
    private static int ticks = 0;

    private static final String[] EVICTABLE_PREFIXES = {
        "textures/painting/",       // tableaux décoratifs
        "textures/map/",            // textures de cartes
        "textures/misc/",           // divers (vignette, etc.)
        "textures/gui/presets/",    // présets de création de monde
        "textures/gui/title/",      // écran titre
        "textures/gui/demo/",       // mode démo
    };

    private static Field byPathField    = null;
    private static boolean initFailed   = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.TEXTURE_CACHE_EVICTOR.get()) return;

        if (++ticks < EVICT_INTERVAL) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return; // seulement en jeu, pas dans les menus

        TextureManager tm = mc.getTextureManager();
        if (tm == null) return;

        if (!initFailed && byPathField == null) {
            initReflection(tm);
        }
        if (initFailed) return;

        evictUnused(tm);
    }

    private static void initReflection(TextureManager tm) {
        try {
            for (Field f : TextureManager.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<ResourceLocation, AbstractTexture> map =
                        (Map<ResourceLocation, AbstractTexture>) f.get(tm);
                    if (!map.isEmpty()) {
                        byPathField = f;
                        AndroidOptMod.LOGGER.info(
                            "[AndroidOpt] TextureCacheEvictor : champ byPath trouvé → {}",
                            f.getName());
                        return;
                    }
                }
            }
            initFailed = true;
        } catch (Exception e) {
            initFailed = true;
            AndroidOptMod.LOGGER.warn("[AndroidOpt] TextureCacheEvictor : réflexion échouée : {}", e.getMessage());
        }
    }

    public static void forceEvict() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        TextureManager tm = mc.getTextureManager();
        if (tm == null) return;

        if (!initFailed && byPathField == null) {
            initReflection(tm);
        }
        if (initFailed) return;

        evictUnused(tm);
    }

    @SuppressWarnings("unchecked")
    private static void evictUnused(TextureManager tm) {
        try {
            Map<ResourceLocation, AbstractTexture> byPath =
                (Map<ResourceLocation, AbstractTexture>) byPathField.get(tm);

            List<ResourceLocation> toEvict = new ArrayList<>();
            for (ResourceLocation loc : byPath.keySet()) {
                String path = loc.getPath();
                for (String prefix : EVICTABLE_PREFIXES) {
                    if (path.startsWith(prefix)) {
                        toEvict.add(loc);
                        break;
                    }
                }
            }

            int freed = 0;
            for (ResourceLocation loc : toEvict) {
                try {
                    tm.release(loc);
                    freed++;
                } catch (Exception ignored) {}
            }

            if (freed > 0) {
                AndroidOptMod.LOGGER.info(
                    "[AndroidOpt] TextureCacheEvictor : {} textures libérées ({} restantes)",
                    freed, byPath.size());
            }
        } catch (Exception e) {
            AndroidOptMod.LOGGER.warn("[AndroidOpt] TextureCacheEvictor : erreur éviction : {}", e.getMessage());
            initFailed = true;
        }
    }
}
