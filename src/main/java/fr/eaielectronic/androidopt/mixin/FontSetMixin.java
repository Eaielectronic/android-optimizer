package fr.eaielectronic.androidopt.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import fr.eaielectronic.androidopt.OptConfig;
import fr.eaielectronic.androidopt.memory.FontPageManager;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public class FontSetMixin {

    @Inject(method = "getGlyphInfo", at = @At("HEAD"))
    private void androidopt$onGetGlyphInfo(int codepoint, boolean filterFishy, CallbackInfoReturnable<GlyphInfo> cir) {
        if (OptConfig.isActive() && OptConfig.LAZY_FONTS.get()) {
            FontPageManager.INSTANCE.loadPage(codepoint);
        }
    }
}
