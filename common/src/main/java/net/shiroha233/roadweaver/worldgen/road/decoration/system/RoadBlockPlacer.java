package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 道路方块放置器
 */
public final class RoadBlockPlacer {
    private RoadBlockPlacer() {}

    public static void placeRoadBlock(WorldGenLevel world,
            BlockState blockBelow,
            BlockPos surfacePos,
            List<BlockState> materials,
            RandomSource random,
            ModConfig cfg) {
        if (!PlacementRules.placeAllowedCheck(blockBelow.getBlock()))
            return;
        BlockState chosen = materials.get(random.nextInt(materials.size()));

        BlockPos below1 = surfacePos.below();
        world.setBlock(below1, chosen, 3);

        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);

        BlockPos belowPos1 = surfacePos.below(2);
        BlockState belowState1 = world.getBlockState(belowPos1);
        if (belowState1.is(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), 3);
        }
    }
}
