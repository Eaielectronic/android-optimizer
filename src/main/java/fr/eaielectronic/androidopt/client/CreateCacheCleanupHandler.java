package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Method;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class CreateCacheCleanupHandler {

    private static int ticks = 0;
    private static final int CACHE_CLEANUP_INTERVAL = 1200; // 60s
    private static final int SCHEMATIC_CHECK_INTERVAL = 2400; // 2 min

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ConfigGuard.isReady()) return;

        ticks++;

        // Nettoyage SuperByteBufferCache toutes les 60s
        if (ticks % CACHE_CLEANUP_INTERVAL == 0) {
            periodicCacheCleanup();
        }

        if (ticks % SCHEMATIC_CHECK_INTERVAL == 0) {
            clearSchematicIfUnused();
        }
    }

    // Appele par le MemoryWatchdog en cas de pression RAM
    public static void forceCacheCleanup() {
        periodicCacheCleanup();
    }

    private static void periodicCacheCleanup() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Class<?> cacheClass = Class.forName("net.createmod.catnip.render.SuperByteBufferCache");
                Method getInstance = cacheClass.getMethod("getInstance");
                Object cache = getInstance.invoke(null);

                Method invalidate = cacheClass.getMethod("invalidate");
                invalidate.invoke(cache);

                AndroidOptMod.LOGGER.info("[AndroidOpt] CreateCacheCleanup : cache Create vide async (60s).");
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                AndroidOptMod.LOGGER.debug("[AndroidOpt] CreateCacheCleanup : {}", e.getMessage());
            }
        });
    }

    
    private static void clearSchematicIfUnused() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Class<?> createClientClass = Class.forName("com.simibubi.create.CreateClient");
                java.lang.reflect.Field schematicField = createClientClass.getField("SCHEMATIC_HANDLER");
                Object handler = schematicField.get(null);
                if (handler == null) return;

                Method isActive = handler.getClass().getMethod("isActive");
                boolean active = (boolean) isActive.invoke(handler);
                if (active) return;

                for (Method m : handler.getClass().getMethods()) {
                    if (m.getName().equals("reset") || m.getName().equals("clear")) {
                        m.invoke(handler);
                        AndroidOptMod.LOGGER.debug("[AndroidOpt] CreateCacheCleanup : SchematicHandler vide async.");
                        break;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                AndroidOptMod.LOGGER.debug("[AndroidOpt] CreateCacheCleanup : {}", e.getMessage());
            }
        });
    }
}

