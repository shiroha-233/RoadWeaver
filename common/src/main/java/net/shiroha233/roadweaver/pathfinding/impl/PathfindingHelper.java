package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

/**
 * 寻路公共工具方法
 */
public final class PathfindingHelper {
    private PathfindingHelper() {}

    public static int heightSampler(PathTerrainField terrain, int x, int z) {
        return terrain.height(x, z);
    }

    public static Holder<Biome> biome(PathTerrainField terrain, int x, int z) {
        return terrain.biome(x, z);
    }

    public static boolean isWaterLike(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isWaterLike(level, x, z);
    }

    public static int oceanFloorSampler(PathTerrainField terrain, int x, int z) {
        return terrain.oceanFloor(x, z);
    }

    public static boolean isNearWaterLike(PathTerrainField terrain, int x, int z, int neighborDistance) {
        return terrain.isNearWater(x, z, neighborDistance);
    }

    public static boolean isColumnWater(PathTerrainField terrain, int x, int z) {
        return terrain.isColumnWater(x, z);
    }

    public static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    public static int calculateTerrainStability(PathTerrainField terrain, BlockPos pos, int y, int step) {
        int cost = 0;
        if (neighborDiffersOrMissing(terrain, pos.getX() + step, pos.getZ(), y)) cost++;
        if (neighborDiffersOrMissing(terrain, pos.getX() - step, pos.getZ(), y)) cost++;
        if (neighborDiffersOrMissing(terrain, pos.getX(), pos.getZ() + step, y)) cost++;
        if (neighborDiffersOrMissing(terrain, pos.getX(), pos.getZ() - step, y)) cost++;
        return cost;
    }

    private static boolean neighborDiffersOrMissing(PathTerrainField terrain, int x, int z, int y) {
        if (!terrain.contains(x, z)) {
            return true;
        }
        return Math.abs(heightSampler(terrain, x, z) - y) > 0;
    }
}
