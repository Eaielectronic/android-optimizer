package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import fr.eaielectronic.androidopt.client.CreateBerCuller;
import fr.eaielectronic.androidopt.client.FrameBudgetManager;
import fr.eaielectronic.androidopt.client.StaticBERBatcher;
import fr.eaielectronic.androidopt.client.TickSkipper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(BlockEntityRenderDispatcher.class)
public class BERDistanceMixin {

    private static final String[] DECORATIVE_SUFFIXES = {
        "SeatBlockEntity", "PlacardBlockEntity", "ToolboxBlockEntity",
        "TrainHatBlockEntity", "ItemVaultBlockEntity", "DisplayLinkBlockEntity",
    };

    @Inject(
        method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity> void androidopt$cullBER(
            E blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {

        if (!ConfigGuard.isReady()) return;

        var pos = blockEntity.getBlockPos();

        if (ConfigGuard.getBool(OptConfig.CREATE_BER_CULLER, true) && CreateBerCuller.CULLED_POSITIONS.contains(pos)) {
            ci.cancel();
            return;
        }

        if (ConfigGuard.getBool(OptConfig.TICK_SKIPPER, true) && TickSkipper.THROTTLED_POSITIONS.contains(pos)) {
            ci.cancel();
            return;
        }

        if (StaticBERBatcher.STATIC_BER_POSITIONS.contains(pos)) {
            ci.cancel();
            return;
        }

        if (ConfigGuard.getBool(OptConfig.CREATE_BER_CULLER, true)
                && FrameBudgetManager.currentLevel != FrameBudgetManager.BudgetLevel.NORMAL) {
            String name = blockEntity.getClass().getSimpleName();
            for (String suffix : DECORATIVE_SUFFIXES) {
                if (name.equals(suffix)) {
                    ci.cancel();
                    return;
                }
            }
        }

    }
}
