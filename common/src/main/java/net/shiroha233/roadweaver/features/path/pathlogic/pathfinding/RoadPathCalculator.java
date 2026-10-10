/* 文件职责：组织普通道路的精确量化寻路与最终路径段生成。 */
package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccuratePathHeightResolver;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.impl.PathPostProcessor;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.terrain.RoadTerrainPlanningPipeline;
import net.shiroha233.roadweaver.planning.terrain.RoadTerrainPlanningPort;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.List;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.snapToGrid;

/**
 * 道路路径计算器
 */
public final class RoadPathCalculator {
    private static final RoadTerrainPlanningPipeline TERRAIN_PLANNING = new RoadTerrainPlanningPipeline();

    private RoadPathCalculator() {}

    public static List<RoadSegmentPlacement> calculateAStarRoadPath(BlockPos startIn,
                                                                    BlockPos endIn,
                                                                    int width,
                                                                    ServerLevel level,
                                                                    int maxSteps,
                                                                    TerrainSamplingCache cache,
                                                                    RoadGenerationConfig cfg) {
        PathCalculationResult result = calculateAStarRoadPathDetailed(startIn, endIn, width, level, maxSteps, cache, cfg);
        try {
            return result.segments();
        } finally {
            if (result.terrain() != null) {
                result.terrain().dispose();
            }
        }
    }

    public static PathCalculationResult calculateAStarRoadPathDetailed(BlockPos startIn,
                                                                       BlockPos endIn,
                                                                       int width,
                                                                       ServerLevel level,
                                                                       int maxSteps,
                                                                       TerrainSamplingCache cache,
                                                                       RoadGenerationConfig cfg) {
        if (startIn == null || endIn == null || level == null || cache == null || cfg == null) {
            return PathCalculationResult.failure();
        }
        PathfindingCostConfig pathCfg = cfg.pathfinding().snapshot();
        pathCfg.setAStarMaxSteps(maxSteps);
        pathCfg.sanitize();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        int margin = searchMargin(start, end);
        StructureConnection connection = new StructureConnection(start, end, ConnectionStatus.PLANNED);
        RoadTerrainPlanningPort.Result planned;
        try {
            planned = TERRAIN_PLANNING.plan(new RoadTerrainPlanningPort.Request(
                    level,
                    new RoadTerrainPlanningPort.Bounds(
                            Math.min(start.getX(), end.getX()) - margin,
                            Math.min(start.getZ(), end.getZ()) - margin,
                            Math.max(start.getX(), end.getX()) + margin,
                            Math.max(start.getZ(), end.getZ()) + margin),
                    List.of(connection),
                    pathCfg));
        } catch (RuntimeException failure) {
            return PathCalculationResult.failure();
        }
        List<BlockPos> rawPath = planned.paths().get(connection);
        if (rawPath == null || rawPath.isEmpty()) {
            return PathCalculationResult.failure();
        }
        return finalizePath(rawPath, width, level, cache, null, connection, planned.mode());
    }

    /**
     * 直接后处理规划阶段已经完成的精确路径，不再采样或重复寻路。
     */
    public static PathCalculationResult calculateFromPlannedPath(List<BlockPos> plannedPath,
                                                                   int width,
                                                                   ServerLevel level,
                                                                   TerrainSamplingCache cache) {
        return calculateFromPlannedPath(plannedPath, width, level, cache, null, null);
    }

    public static PathCalculationResult calculateFromPlannedPath(List<BlockPos> plannedPath,
                                                                   int width,
                                                                   ServerLevel level,
                                                                   TerrainSamplingCache cache,
                                                                   StructureConnection connection,
                                                                   TerrainSamplingMode mode) {
        if (plannedPath == null || plannedPath.isEmpty()) {
            return PathCalculationResult.failure();
        }
        return finalizePath(plannedPath, width, level, cache, null, connection, mode);
    }

    private static PathCalculationResult finalizePath(List<BlockPos> rawPath,
                                                      int width,
                                                       ServerLevel level,
                                                       TerrainSamplingCache cache,
                                                       PathTerrainField terrain,
                                                       StructureConnection connection,
                                                       TerrainSamplingMode mode) {
        if (rawPath == null || rawPath.size() < 2) {
            return PathCalculationResult.failure();
        }
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        List<BlockPos> finalPath = mode == TerrainSamplingMode.COARSE_CORRIDOR || mode == TerrainSamplingMode.FULL_REGION
                ? rawPath : AccuratePathHeightResolver.resolve(rawPath, terrain, accurate);
        if (connection != null && mode != null && mode != TerrainSamplingMode.COARSE_CORRIDOR) {
            RawPathDebugService.captureResolved(level, connection, mode, rawPath, finalPath);
        }
        List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                finalPath,
                width,
                level,
                cache,
                terrain,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH,
                net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.CurveMode.BEZIER_CASTELJAU,
                accurate);
        if (segments == null) {
            return PathCalculationResult.failure();
        }
        return new PathCalculationResult(segments, terrain);
    }

    static int searchMargin(BlockPos start, BlockPos end) {
        int distance = Math.abs(start.getX() - end.getX()) + Math.abs(start.getZ() - end.getZ());
        return Math.min(RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS, Math.max(512, distance));
    }

    public record PathCalculationResult(List<RoadSegmentPlacement> segments, PathTerrainField terrain) {
        public static PathCalculationResult failure() {
            return new PathCalculationResult(null, null);
        }
    }
}
