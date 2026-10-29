package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 表面放置工具
 */
public final class SurfacePlacementUtil {
    private SurfacePlacementUtil() {}

    public static void placeOnSurface(WorldGenLevel world, BlockPos placePos, List<BlockState> material, int roadType, RandomSource random, ModConfig cfg) {
        double naturalBlockChance = 1.0;
        BlockPos surfacePos = placePos;
        BlockPos belowTop = surfacePos.below();
        BlockState blockStateAtPos = world.getBlockState(belowTop);
        boolean doPlace = (roadType == 0) || random.nextDouble() < naturalBlockChance;
        if (doPlace) {
            RoadBlockPlacer.placeRoadBlock(world, blockStateAtPos, surfacePos, material, random, cfg);
        } else {
            AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);
        }
    }

    public static void placeRoadBlock(WorldGenLevel world, BlockState blockBelow, BlockPos surfacePos, List<BlockState> materials, RandomSource random, ModConfig cfg) {
        RoadBlockPlacer.placeRoadBlock(world, blockBelow, surfacePos, materials, random, cfg);
    }

    public static void clearAboveColumn(WorldGenLevel world, BlockPos surfacePos, ModConfig cfg) {
        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);
    }
}
