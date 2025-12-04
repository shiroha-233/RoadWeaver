package net.shiroha233.roadweaver.features.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

/**
 * 路面上方清障器。
 * 清除路面上方所有障碍物（树木、植被、建筑等），确保道路畅通。
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

            // 清除路面上方所有障碍物（包括树木、植被等）
            world.setBlock(up, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
