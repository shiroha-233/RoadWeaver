package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.PathfinderFactory;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.List;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.snapToGrid;

final class RawPathSearch {
    private RawPathSearch() {}

    static List<BlockPos> find(ServerLevel level,
                               StructureConnection connection,
                               PathfindingCostConfig config,
                               TerrainSamplingCache cache,
                               PathTerrainField terrain) {
        int step = config.effectiveAStarStep();
        int startX = snapToGrid(connection.from().getX(), step);
        int startZ = snapToGrid(connection.from().getZ(), step);
        int endX = snapToGrid(connection.to().getX(), step);
        int endZ = snapToGrid(connection.to().getZ(), step);
        if (!terrain.contains(startX, startZ) || !terrain.contains(endX, endZ)) {
            return null;
        }
        BlockPos start = new BlockPos(startX, terrain.height(startX, startZ), startZ);
        BlockPos end = new BlockPos(endX, terrain.height(endX, endZ), endZ);
        Pathfinder pathfinder = PathfinderFactory.create(config.pathfindingAlgorithm());
        PathResult result = pathfinder.findRawPath(start, end, level, config.aStarMaxSteps(), cache, terrain, config);
        return result.success() && result.hasRawPath() ? result.rawPath() : null;
    }
}
