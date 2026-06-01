package fr.eaielectronic.androidopt.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import fr.eaielectronic.androidopt.memory.AudioPoolBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SoundBuffer.class)
public class SoundBufferMixin {

    @Redirect(
        method = "getAlBuffer",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alGenBuffers:([I)V"),
        remap = false
    )
    private void androidopt$redirectGenBuffers(int[] arr) {
        arr[0] = AudioPoolBridge.getBuffer();
    }

    @Redirect(
        method = "discardAlBuffer",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alDeleteBuffers:([I)V"),
        remap = false
    )
    private void androidopt$redirectDeleteBuffers(int[] arr) {
        AudioPoolBridge.releaseBuffer(arr[0]);
    }
}
