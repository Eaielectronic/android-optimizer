package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = SectionRenderDispatcher.class, remap = true)
public class ChunkRebuildMixin {

    private static final AtomicInteger rebuildsThisFrame = new AtomicInteger(0);
    private static long lastFrameNano = 0L;

    @Inject(
        method = "uploadAllPendingUploads",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void androidopt$throttleUploads(CallbackInfo ci) {
        if (!ConfigGuard.isReady() || !ConfigGuard.getBool(OptConfig.CHUNK_REBUILD_THROTTLER, true)) return;

        int configMax = ConfigGuard.getInt(OptConfig.CHUNK_REBUILDS_PER_FRAME, 2);

        int effectiveMax;
        switch (fr.eaielectronic.androidopt.client.FrameBudgetManager.currentLevel) {
            case CRITICAL:
                effectiveMax = 1;
                break;
            case LOW:
            case REDUCED:
                effectiveMax = Math.max(1, configMax / 2);
                break;
            default:
                effectiveMax = configMax;
                break;
        }

        // Garde la securite heap
        Runtime rt = Runtime.getRuntime();
        double heapRatio = (double)(rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
        if (heapRatio > 0.85) {
            effectiveMax = 1;
        }

        long now = System.nanoTime();

        if (now - lastFrameNano > 8_000_000L) {
            rebuildsThisFrame.set(0);
            lastFrameNano = now;
        }

        if (rebuildsThisFrame.incrementAndGet() > effectiveMax) {
            ci.cancel();
        }
    }
}
