package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer",
       remap = false)
public class CreateGogglesOverlayCuller {

    private static final double MAX_DIST_SQ = 8.0 * 8.0;

    @Inject(
        method = "renderOverlay",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void androidopt$cullDistantGoggleOverlay(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {

        if (!ConfigGuard.isReady() || !ConfigGuard.getBool(OptConfig.CREATE_BER_CULLER, true)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null) return;

        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            if (mc.player.blockPosition().distSqr(bhr.getBlockPos()) > MAX_DIST_SQ) {
                ci.cancel();
            }
        }
    }
}
