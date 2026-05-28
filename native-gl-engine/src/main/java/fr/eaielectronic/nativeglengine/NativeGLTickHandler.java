package fr.eaielectronic.nativeglengine;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Tick handler client pour NativeGLEngine.
 * 
 * Responsabilités :
 * - Drainer la queue de textures différées entre les frames
 * - Surveiller la pression mémoire GPU (toutes les 100 ticks)
 * - Mettre à jour les stats GL hooks (toutes les 200 ticks)
 */
@EventBusSubscriber(modid = NativeGLEngineMod.MOD_ID, value = Dist.CLIENT)
public class NativeGLTickHandler {

    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ticks++;

        // ═══ Chaque tick : drain texture queues ═══
        if (NativeLib.isLoaded()) {
            try {
                int maxUploads = NativeGLConfig.MAX_TEXTURE_UPLOADS_PER_FRAME.get();

                // Pipeline async (Java Mixin → C++ compression → GL upload)
                GLInterceptorBridge.drainCompressedQueue(maxUploads, 2000); // 2ms budget

                // Pipeline legacy PLT (si les hooks natifs sont installés)
                if (GLInterceptorBridge.isInstalled()) {
                    GLInterceptorBridge.drainTextureQueue(maxUploads);
                }
            } catch (Exception ignored) {}
        }

        // ═══ Toutes les 100 ticks (~5s) : monitoring mémoire GPU ═══
        if (ticks % 100 == 0) {
            monitorGPUMemory();
        }

        // ═══ Toutes les 200 ticks (~10s) : refresh stats GL hooks ═══
        if (ticks % 200 == 0) {
            GLInterceptorBridge.refreshStats();
        }
    }

    private static void monitorGPUMemory() {
        if (!NativeMemoryBridge.isNativeInitialized()) return;

        try {
            if (!NativeGLConfig.GPU_MEMORY_MONITOR.get()) return;
        } catch (Exception e) {
            return;
        }

        MemoryReport report = NativeMemoryBridge.getReport();

        // ═══ Logs verbeux si activé ═══
        boolean verbose = false;
        if (AndroidOptBridge.isAndroidOptPresent()) {
            verbose = AndroidOptBridge.isVerboseLogActive();
        } else {
            try {
                verbose = NativeGLConfig.VERBOSE_LOG.get();
            } catch (Exception ignored) {}
        }

        if (verbose) {
            NativeGLEngineMod.LOGGER.info(
                "[NativeGLEngine] [VERBOSE] Tick #{} | Native={} | Renderer={} | " +
                "SysAvail={}MB | GPU budget={}MB usage={}MB pressure={}% | " +
                "Temp={}°C throttle={} | " +
                "GL total={} deduped={} deferred={} | " +
                "Async submitted={} uploaded={} pending={} | " +
                "ShaderCache mem={} disk={}",
                ticks,
                NativeLib.isLoaded() ? "OK" : "NO",
                RendererDetector.getCurrent().displayName,
                report.sysAvailableMB(),
                report.gpuBudgetMB(),
                report.gpuUsageMB(),
                report.gpuPressurePercent(),
                report.tempCelsius(),
                report.thermalThrottling() ? "YES" : "no",
                GLInterceptorBridge.getTotalGLCalls(),
                GLInterceptorBridge.getDedupedCalls(),
                GLInterceptorBridge.getDeferredTextures(),
                GLInterceptorBridge.getAsyncSubmitted(),
                GLInterceptorBridge.getAsyncUploaded(),
                GLInterceptorBridge.getPendingCount(),
                ShaderCacheManager.getMemoryCacheSize(),
                ShaderCacheManager.getDiskCacheSize()
            );
            
            // Envoyer la verbosité au layer natif C++
            NativeMemoryBridge.setVerboseLogging(verbose);
        }

        // Sync des configs C++ dynamiques
        if (AndroidOptBridge.isAndroidOptPresent()) {
            NativeMemoryBridge.updateConfig(
                AndroidOptBridge.getGpuBudgetPercent(),
                AndroidOptBridge.isTexCompressActive(),
                AndroidOptBridge.isVertexQuantActive()
            );
        }

        // ═══ Status périodique toutes les 600 ticks (~30s) ═══
        if (ticks % 600 == 0) {
            NativeGLEngineMod.LOGGER.info(
                "[NativeGLEngine] Status | Renderer={} | SysAvail={}MB | GPU={}% | Temp={}°C | " +
                "ShaderCache={}/{} (mem/disk) | Hooks={}",
                RendererDetector.getCurrent().displayName,
                report.sysAvailableMB(),
                report.gpuPressurePercent(),
                report.tempCelsius(),
                ShaderCacheManager.getMemoryCacheSize(),
                ShaderCacheManager.getDiskCacheSize(),
                GLInterceptorBridge.isInstalled() ? "ON" : "OFF"
            );
        }

        float softThreshold;
        float hardThreshold;
        try {
            softThreshold = NativeGLConfig.GPU_PRESSURE_SOFT_PERCENT.get() / 100f;
            hardThreshold = NativeGLConfig.GPU_PRESSURE_HARD_PERCENT.get() / 100f;
        } catch (Exception e) {
            softThreshold = 0.80f;
            hardThreshold = 0.92f;
        }

        if (report.gpuPressure() >= hardThreshold) {
            // Cleanup agressif
            NativeGLEngineMod.LOGGER.warn(
                "[NativeGLEngine] GPU pressure CRITICAL {}% — aggressive cleanup",
                report.gpuPressurePercent());
            System.gc();
            ShaderCacheManager.trimMemoryCache();
        } else if (report.gpuPressure() >= softThreshold) {
            // Cleanup doux
            NativeGLEngineMod.LOGGER.debug(
                "[NativeGLEngine] GPU pressure {}% — soft cleanup",
                report.gpuPressurePercent());
            System.gc();
        }

        // Thermal monitoring (si connecté à Android Optimizer, utiliser ses seuils)
        if (report.tempCelsius() > 0) {
            int warningTemp = AndroidOptBridge.isAndroidOptPresent()
                ? AndroidOptBridge.getThermalWarningTemp() : 99;

            if (report.tempCelsius() > warningTemp) {
                NativeGLEngineMod.LOGGER.warn(
                    "[NativeGLEngine] Temperature {}°C > warning {}°C",
                    report.tempCelsius(), warningTemp);
            }
        }
    }
}
