package fr.eaielectronic.nativeglengine.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import fr.eaielectronic.nativeglengine.GLInterceptorBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin {

    @Inject(method = "_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onTexImage2DInt(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels, CallbackInfo ci) {
        if (pixels != null && pixels.isDirect()) {
            long ptr = org.lwjgl.system.MemoryUtil.memAddress(pixels);
            boolean handled = GLInterceptorBridge.nativeInterceptTexImage2D(target, level, internalFormat, width, height, format, type, ptr);
            if (handled) ci.cancel();
        }
    }
}
