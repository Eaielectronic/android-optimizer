package fr.eaielectronic.androidopt.mixin;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SodiumOptionsGUI.class)
public class SodiumOptionsGUIMixin {

    @Shadow
    @Final
    private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void androidOpt$injectPage(net.minecraft.client.gui.screens.Screen prevScreen, CallbackInfo ci) {
        // Ajoute la page a la fin
        this.pages.add(fr.eaielectronic.androidopt.integration.sodium.AndroidOptSodiumNativePage.createPage());
        fr.eaielectronic.androidopt.AndroidOptMod.LOGGER.info("[AndroidOpt] Intégration Native Sodium (via Mixin) activée !");
    }
}
