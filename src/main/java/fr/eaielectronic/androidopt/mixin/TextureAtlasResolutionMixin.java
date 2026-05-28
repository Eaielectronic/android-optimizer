package fr.eaielectronic.androidopt.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SpriteContents.class)
public class TextureAtlasResolutionMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static NativeImage androidopt$reduceResolution(NativeImage original) {
        if (!fr.eaielectronic.androidopt.ConfigGuard.isReady() || !fr.eaielectronic.androidopt.OptConfig.TEXTURE_DOWNSCALE_ENABLED.get()) return original;
        if (!fr.eaielectronic.androidopt.AndroidDetector.IS_ANDROID) return original;
        if (original.getWidth() <= 16 || original.getHeight() <= 16) return original;
        
        int newW = original.getWidth() / 2;
        int newH = original.getHeight() / 2;
        NativeImage resized = new NativeImage(original.format(), newW, newH, false);
        
        for (int x = 0; x < newW; x++) {
            for (int y = 0; y < newH; y++) {
                resized.setPixelRGBA(x, y, original.getPixelRGBA(x * 2, y * 2));
            }
        }
        return resized;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static FrameSize androidopt$reduceFrameSize(FrameSize original) {
        if (!fr.eaielectronic.androidopt.ConfigGuard.isReady() || !fr.eaielectronic.androidopt.OptConfig.TEXTURE_DOWNSCALE_ENABLED.get()) return original;
        if (!fr.eaielectronic.androidopt.AndroidDetector.IS_ANDROID || original == null) return original;
        if (original.width() <= 16 || original.height() <= 16) return original;
        return new FrameSize(original.width() / 2, original.height() / 2);
    }
}
