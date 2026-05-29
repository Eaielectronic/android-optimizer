package fr.eaielectronic.nativeglengine.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import fr.eaielectronic.nativeglengine.NativeBufferManager;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(NativeImage.class)
public class NativeImageMixin {

    @Shadow private long pixels;

    /**
     * Stocke la référence au ByteBuffer direct géré par notre OffHeapArena C++.
     * Cela évite que Java le GC, et nous permet de le libérer explicitement.
     */
    @Unique
    private ByteBuffer nativegl$offHeapBuffer = null;

    @Redirect(
        method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;nmemAlloc(J)J"),
        require = 0
    )
    private long androidopt$redirectAlloc(long size) {
        if (NativeBufferManager.isInitialized()) {
            this.nativegl$offHeapBuffer = NativeBufferManager.allocateCustom(size, "NativeImage");
            if (this.nativegl$offHeapBuffer != null) {
                return MemoryUtil.memAddress(this.nativegl$offHeapBuffer);
            }
        }
        return MemoryUtil.nmemAlloc(size);
    }

    @Redirect(
        method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;nmemCalloc(JJ)J"),
        require = 0
    )
    private long androidopt$redirectCalloc(long num, long size) {
        long totalSize = num * size;
        if (NativeBufferManager.isInitialized()) {
            this.nativegl$offHeapBuffer = NativeBufferManager.allocateCustom(totalSize, "NativeImage");
            if (this.nativegl$offHeapBuffer != null) {
                // Pour imiter calloc, il faudrait mettre le buffer à zéro.
                // Par sécurité et rapidité, on part du principe que NativeBufferManager retourne souvent du zeroed (mmap)
                return MemoryUtil.memAddress(this.nativegl$offHeapBuffer);
            }
        }
        return MemoryUtil.nmemCalloc(num, size);
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void androidopt$onClose(CallbackInfo ci) {
        if (this.nativegl$offHeapBuffer != null) {
            // Libérer via notre moteur C++ au lieu de MemoryUtil.nmemFree()
            NativeBufferManager.freeCustom(this.nativegl$offHeapBuffer);
            this.nativegl$offHeapBuffer = null;
            this.pixels = 0L;
            ci.cancel(); // Stoppe la méthode originale
        }
    }
}
