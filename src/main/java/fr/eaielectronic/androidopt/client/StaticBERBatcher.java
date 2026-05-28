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
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.Set;


@EventBusSubscriber(modid = AndroidOptMod.MODID, value = Dist.CLIENT)
public class StaticBERBatcher {

    public static final double STATIC_CULL_DIST    = 12.0;
    public static final double STATIC_CULL_DIST_SQ = STATIC_CULL_DIST * STATIC_CULL_DIST;

    
    public static final Set<BlockPos> STATIC_BER_POSITIONS = new HashSet<>();

    // BEs toujours animes — ne jamais skipper
    // Nous utiliserons instanceof directement dans la methode isAlwaysAnimated

    private static final int SCAN_INTERVAL = 60; // 3s
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!OptConfig.isActive()) {
            STATIC_BER_POSITIONS.clear();
            return;
        }
        // Actif seulement en mode REDUCED ou pire
        if (FrameBudgetManager.currentLevel == FrameBudgetManager.BudgetLevel.NORMAL) {
            STATIC_BER_POSITIONS.clear();
            return;
        }

        if (++ticks % SCAN_INTERVAL != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STATIC_BER_POSITIONS.clear();
            return;
        }

        STATIC_BER_POSITIONS.clear();

        Level level  = mc.level;
        int playerCX = mc.player.blockPosition().getX() >> 4;
        int playerCZ = mc.player.blockPosition().getZ() >> 4;
        int radius   = Math.min(mc.options.renderDistance().get(), 6);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource()
                    .getChunk(playerCX + dx, playerCZ + dz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = mc.player.blockPosition().distSqr(pos);

                    // Seulement les BEs lointains
                    if (distSq <= STATIC_CULL_DIST_SQ) continue;

                    // Skip les BEs toujours animés
                    if (isAlwaysAnimated(be)) continue;

                    // Skip les BEs Create (gérés par BERDistanceMixin)
                    String className = be.getClass().getName();
                    if (className.contains("create") || className.contains("Create")) continue;

                    STATIC_BER_POSITIONS.add(pos.immutable());
                }
            }
        }

        if (!STATIC_BER_POSITIONS.isEmpty()) {
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] StaticBERBatcher : {} BERs statiques skippés", STATIC_BER_POSITIONS.size());
        }
    }

    private static boolean isAlwaysAnimated(BlockEntity be) {
        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) return true;
        if (be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) return true;
        if (be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) return true;
        if (be instanceof net.minecraft.world.level.block.entity.BellBlockEntity) return true;
        if (be instanceof net.minecraft.world.level.block.entity.ConduitBlockEntity) return true;
        if (be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity) return true;
        
        // Piston, End Gateway, and Create machines by string matching to avoid hard dependencies
        String name = be.getClass().getName();
        if (name.contains("Piston") || name.contains("Gateway") || name.contains("Portal")) return true;
        if (name.contains("create") || name.contains("Create")) return true;
        
        return false;
    }
}
