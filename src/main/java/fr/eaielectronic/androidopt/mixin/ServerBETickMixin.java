package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.ConfigGuard;
import fr.eaielectronic.androidopt.OptConfig;
import fr.eaielectronic.androidopt.client.ServerModeDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;


@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public class ServerBETickMixin {

    private static final double THROTTLE_DIST_SQ = 24.0 * 24.0;
    private static final int    THROTTLE_RATIO   = 4;
    private static final AtomicInteger globalTick = new AtomicInteger(0);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void androidopt$throttleDistantBETick(CallbackInfo ci) {
        if (!ConfigGuard.isReady() || !ConfigGuard.getBool(OptConfig.TICK_SKIPPER, true)) return;
        if (!ServerModeDetector.isSolo()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Lit la position via l'interface TickingBlockEntity
        TickingBlockEntity self = (TickingBlockEntity) (Object) this;
        BlockPos pos = self.getPos();
        if (pos == null) return;

        if (mc.player.blockPosition().distSqr(pos) <= THROTTLE_DIST_SQ) return;

        // Verifie les exceptions via le nom du type
        String type = self.getType();
        if (type != null) {
            String typeLower = type.toLowerCase();
            if (typeLower.contains("furnace") || 
                typeLower.contains("smoker") || 
                typeLower.contains("hopper") || 
                typeLower.contains("brewing") || 
                typeLower.contains("spawner")) {
                return;
            }
        }

        // Skip 3 ticks sur 4
        if (globalTick.incrementAndGet() % THROTTLE_RATIO != 0) {
            ci.cancel();
        }
    }
}
