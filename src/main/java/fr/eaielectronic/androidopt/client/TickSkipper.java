package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.Set;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class TickSkipper {

    private static final int    SCAN_INTERVAL = 10;   // toutes les 0.5s
    private static final int    SCAN_RADIUS   = 6;    // chunks
    private static final double THROTTLE_SQ   = 24.0 * 24.0; // 24 blocs

    
    public static final Set<BlockPos> THROTTLED_POSITIONS = new HashSet<>();

    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive() || !OptConfig.TICK_SKIPPER.get()) {
            THROTTLED_POSITIONS.clear();
            return;
        }

        if (++ticks % SCAN_INTERVAL != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            THROTTLED_POSITIONS.clear();
            return;
        }

        Player player = mc.player;
        Level  level  = mc.level;

        int playerCX = player.blockPosition().getX() >> 4;
        int playerCZ = player.blockPosition().getZ() >> 4;

        THROTTLED_POSITIONS.clear();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource()
                    .getChunk(playerCX + dx, playerCZ + dz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (player.blockPosition().distSqr(pos) > THROTTLE_SQ) {
                        THROTTLED_POSITIONS.add(pos.immutable());
                    }
                }
            }
        }

        if (!THROTTLED_POSITIONS.isEmpty()) {
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] TickSkipper : {} BEs lointains marqués (rendu réduit)",
                THROTTLED_POSITIONS.size());
        }
    }
}
