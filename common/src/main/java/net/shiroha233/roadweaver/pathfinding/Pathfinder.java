package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

/**
 * 普通道路寻路器接口，支持策略模式切换算法
 */
public interface Pathfinder {
    PathResult findPath(BlockPos start, BlockPos end, int width,
                        ServerLevel level, int maxSteps,
                        TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig config);

    default PathResult findRawPath(BlockPos start, BlockPos end,
                                   ServerLevel level, int maxSteps,
                                   TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig config) {
        return findPath(start, end, 1, level, maxSteps, cache, terrain, config);
    }
}
