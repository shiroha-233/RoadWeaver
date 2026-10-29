package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

/**
 * 路面上方清障器
 */
public final class AboveColumnClearer {
    private AboveColumnClearer() {}

    public static void clearAboveColumn(WorldGenLevel world, BlockPos surfacePos, ModConfig cfg) {
        boolean tunnel = cfg != null && cfg.tunnelEnabled();
        int defaultClear = (cfg != null ? cfg.roadClearHeight() : 3);
        int maxH = tunnel
                ? Math.max(2, Math.min(16, cfg.tunnelClearHeight()))
                : Math.max(1, Math.min(16, defaultClear));

        for (int i = 0; i < maxH; i++) {
            BlockPos up = surfacePos.above(i);
            BlockState st = world.getBlockState(up);
            if (st.isAir()) continue;

            world.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        BlockState state = world.getBlockState(surfacePos.above(maxH));
        if(state.getBlock() instanceof BushBlock || state.getBlock() instanceof SnowLayerBlock)
            world.removeBlock(surfacePos.above(maxH),false);
    }
}
