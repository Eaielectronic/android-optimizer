package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidDetector;
import fr.eaielectronic.androidopt.AndroidOptMod;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(SectionBufferBuilderPool.class)
public class SectionBufferMixin {

    private static final int MAX_BUFFERS_ANDROID = 4;

    @Inject(
        method = "allocate",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void androidopt$limitPoolSize(
            int bufferCount,
            CallbackInfoReturnable<SectionBufferBuilderPool> cir) {

        if (!AndroidDetector.IS_ANDROID) return;

        if (bufferCount > MAX_BUFFERS_ANDROID) {
            AndroidOptMod.LOGGER.info(
                "[AndroidOpt] SectionBufferMixin : pool {} → {} buffers (~{}Mo économisés)",
                bufferCount, MAX_BUFFERS_ANDROID,
                (bufferCount - MAX_BUFFERS_ANDROID) * 20);
            cir.setReturnValue(SectionBufferBuilderPool.allocate(MAX_BUFFERS_ANDROID));
        }
    }
}
