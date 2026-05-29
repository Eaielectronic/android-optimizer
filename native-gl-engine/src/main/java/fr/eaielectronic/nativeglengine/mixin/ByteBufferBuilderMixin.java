package fr.eaielectronic.nativeglengine.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import fr.eaielectronic.nativeglengine.NativeBufferManager;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(ByteBufferBuilder.class)
public class ByteBufferBuilderMixin {

    @Shadow long pointer;
    @Shadow private int capacity;

    @Unique
    private ByteBuffer nativegl$offHeapBuffer = null;

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    private void androidopt$onInit(int capacity, CallbackInfo ci) {
        if (NativeBufferManager.isInitialized()) {
            this.nativegl$offHeapBuffer = NativeBufferManager.allocateCustom(capacity, "ByteBufferBuilder");
            if (this.nativegl$offHeapBuffer != null) {
                if (this.pointer != 0L) {
                    MemoryUtil.getAllocator(false).free(this.pointer);
                }
                this.pointer = MemoryUtil.memAddress(this.nativegl$offHeapBuffer);
            }
        }
    }

    @Inject(method = "resize", at = @At("HEAD"), cancellable = true)
    private void androidopt$onResize(int newSize, CallbackInfo ci) {
        if (NativeBufferManager.isInitialized() && this.nativegl$offHeapBuffer != null) {
            ByteBuffer newBuf = NativeBufferManager.allocateCustom(newSize, "ByteBufferBuilder_Resize");
            if (newBuf != null) {
                long newPtr = MemoryUtil.memAddress(newBuf);
                MemoryUtil.memCopy(this.pointer, newPtr, this.capacity);
                NativeBufferManager.freeCustom(this.nativegl$offHeapBuffer);
                this.nativegl$offHeapBuffer = newBuf;
                this.pointer = newPtr;
                this.capacity = newSize;
                
                // Note: La méthode resize originale met à jour this.capacity, 
                // mais on a 'cancellable = true', donc on doit le faire nous-même.
                ci.cancel();
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void androidopt$onClose(CallbackInfo ci) {
        if (this.nativegl$offHeapBuffer != null) {
            NativeBufferManager.freeCustom(this.nativegl$offHeapBuffer);
            this.nativegl$offHeapBuffer = null;
            this.pointer = 0L;
            // On ne peut pas facilement reset this.generation (private), mais ce n'est pas grave
            // car la méthode cancel() empêchera l'appel à allocator.free().
            ci.cancel();
        }
    }
}
