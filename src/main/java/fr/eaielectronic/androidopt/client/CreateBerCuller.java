package fr.eaielectronic.androidopt.client;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.OptConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.Set;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class CreateBerCuller {

    
    public static final int BER_CULL_DISTANCE = 16; // default, overridden by config
    public static double BER_CULL_DIST_SQ = (double) BER_CULL_DISTANCE * BER_CULL_DISTANCE;

    
    public static final Set<BlockPos> CULLED_POSITIONS = new HashSet<>();

    private static final int UPDATE_INTERVAL = 10; // toutes les 10 frames
    private static int frameCount = 0;


    @SubscribeEvent
    public static void onRenderLevelPre(RenderLevelStageEvent event) {
        if (!OptConfig.isActive()) return;
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return; // Skip if Create not present
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        frameCount++;
        if (frameCount % UPDATE_INTERVAL != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        CULLED_POSITIONS.clear();

        // Read configurable distance
        int cullDist = fr.eaielectronic.androidopt.ConfigGuard.getInt(OptConfig.BER_CULL_DISTANCE, 16);
        
        // Reduire dynamiquement si le jeu rame
        if (FrameBudgetManager.currentLevel == FrameBudgetManager.BudgetLevel.CRITICAL) {
            cullDist = Math.max(4, cullDist / 2);
        } else if (FrameBudgetManager.currentLevel == FrameBudgetManager.BudgetLevel.LOW) {
            cullDist = Math.max(8, (int)(cullDist * 0.75));
        }
        
        BER_CULL_DIST_SQ = (double) cullDist * cullDist;

        Level level = mc.level;
        int playerCX = mc.player.blockPosition().getX() >> 4;
        int playerCZ = mc.player.blockPosition().getZ() >> 4;
        int scanRadius = (mc.options.renderDistance().get() / 2) + 1;

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                LevelChunk chunk = level.getChunkSource()
                    .getChunk(playerCX + dx, playerCZ + dz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = mc.player.blockPosition().distSqr(pos);
                    if (distSq > BER_CULL_DIST_SQ) {
                        CULLED_POSITIONS.add(pos);
                    }
                }
            }
        }

        if (!CULLED_POSITIONS.isEmpty()) {
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] CreateBerCuller : {} BERs culled ce frame", CULLED_POSITIONS.size());
        }
    }
}
