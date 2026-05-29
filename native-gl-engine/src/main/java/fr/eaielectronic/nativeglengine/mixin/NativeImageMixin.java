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

    @Inject(
        method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V",
        at = @At("RETURN")
    )
    private void androidopt$onInit(NativeImage.Format format, int width, int height, boolean useCalloc, CallbackInfo ci) {
        if (NativeBufferManager.isInitialized()) {
            long size = (long)width * (long)height * (long)format.components();
            this.nativegl$offHeapBuffer = NativeBufferManager.allocateCustom(size, "NativeImage");
            
            if (this.nativegl$offHeapBuffer != null) {
                // LWJGL a déjà alloué la mémoire dans 'this.pixels'.
                // On la libère immédiatement et on remplace par notre adresse OffHeap.
                if (this.pixels != 0L) {
                    MemoryUtil.nmemFree(this.pixels);
                }
                this.pixels = MemoryUtil.memAddress(this.nativegl$offHeapBuffer);
            }
        }
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
