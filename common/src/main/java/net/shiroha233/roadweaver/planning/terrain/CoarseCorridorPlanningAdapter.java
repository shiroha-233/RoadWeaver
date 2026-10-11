/* 文件职责：使用粗地形高度场规划道路，避免在线阶段重复构造原版 NoiseChunk。 */
package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationCoarseSamplingProgress;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionSampler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;

/**
 * 粗地形道路规划器。
 *
 * <p>粗高度场与原版 preliminary surface 使用相同的噪声语义。路径的最终切填高度由
 * RoadChunkPlan 在区块生成前编译，不在规划线程重新执行一次完整区块噪声生成。</p>
 */
final class CoarseCorridorPlanningAdapter implements RoadTerrainPlanningPort {
    @Override
    public Result plan(Request request) {
        if (request.connections().isEmpty()) {
            return Result.empty(TerrainSamplingMode.COARSE_CORRIDOR);
        }

        TerrainSamplingCache cache = new TerrainSamplingCache();
        CoarseTerrainRegion terrain = null;
        try {
            Bounds bounds = request.bounds();
            int step = request.pathfinding().effectiveAStarStep();
            terrain = CoarseTerrainRegionSampler.sample(
                    request.level(), bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), step,
                    InitialGenerationCoarseSamplingProgress.INSTANCE);
            writeMapTiles(request, terrain);
            terrain.preparePathfinding();

            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.COARSE_PATHING, "computing_coarse_paths");
            InitialGenerationProgressTracker.setCoarsePathPlan(
                    request.connections().size(), "computing_coarse_paths");

            LinkedHashMap<StructureConnection, List<BlockPos>> coarsePaths = new LinkedHashMap<>();
            for (StructureConnection connection : request.connections()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("coarse planning interrupted");
                }
                try {
                    List<BlockPos> path = RawPathSearch.find(
                            request.level(), connection, request.pathfinding(), cache, terrain);
                    if (path != null && !path.isEmpty()) {
                        RawPathDebugService.captureSearch(request.level(), connection, TerrainSamplingMode.COARSE_CORRIDOR, path);
                        coarsePaths.put(connection, path);
                    }
                } finally {
                    InitialGenerationProgressTracker.recordCoarsePathDone();
                }
            }
            if (coarsePaths.isEmpty()) {
                return Result.empty(TerrainSamplingMode.COARSE_CORRIDOR);
            }

            LinkedHashMap<StructureConnection, List<BlockPos>> corridorPositions = new LinkedHashMap<>();
            long columnsTotal = 0L;
            for (Map.Entry<StructureConnection, List<BlockPos>> entry : coarsePaths.entrySet()) {
                List<BlockPos> positions = AccurateCorridorTerrain.positions(
                        entry.getValue(), step, Math.max(64, step * 2));
                corridorPositions.put(entry.getKey(), positions);
                columnsTotal += positions.size();
            }
            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.EXACT_SAMPLING, "sampling_accurate_corridors");
            InitialGenerationProgressTracker.setExactSamplingPlan(columnsTotal, "sampling_accurate_corridors");
            AccurateHeightSampler sampler = cache.getAccurateSampler(request.level());
            InitialGenerationProgressTracker.setBackend(sampler.backendName(), sampler.deviceName(), "");
            LinkedHashMap<StructureConnection, AccurateCorridorTerrain> corridors = new LinkedHashMap<>();
            for (Map.Entry<StructureConnection, List<BlockPos>> entry : corridorPositions.entrySet()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("corridor sampling interrupted");
                }
                AccurateCorridorTerrain corridor = AccurateCorridorTerrain.sample(
                        request.level(), cache, entry.getValue(), step,
                        batch -> InitialGenerationProgressTracker.recordExactSampleBatch(
                                batch.batchColumns(), batch.batchNanos() / 1_000_000L,
                                sampler.backendName(), sampler.deviceName()));
                corridors.put(entry.getKey(), corridor);
            }
            InitialGenerationProgressTracker.completeExactSampling();

            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.EXACT_PATHING, "computing_corridor_paths");
            InitialGenerationProgressTracker.setExactPathPlan(
                    coarsePaths.size(), "computing_corridor_paths");

            LinkedHashMap<StructureConnection, List<BlockPos>> paths = new LinkedHashMap<>();
            for (Map.Entry<StructureConnection, AccurateCorridorTerrain> entry : corridors.entrySet()) {
                StructureConnection connection = entry.getKey();
                List<BlockPos> coarsePath = coarsePaths.get(connection);
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("corridor planning interrupted");
                }
                try {
                    List<BlockPos> refined = RawPathSearch.find(request.level(), connection,
                            request.pathfinding(), cache, entry.getValue());
                    // Do not silently pave the coarse route when the accurate search fails.
                    if (refined != null && refined.size() >= 2) {
                        RawPathDebugService.captureResolved(request.level(), connection,
                                TerrainSamplingMode.COARSE_CORRIDOR, coarsePath, refined);
                        paths.put(connection, List.copyOf(refined));
                    }
                } finally {
                    InitialGenerationProgressTracker.recordExactPathDone();
                }
            }
            return new Result(TerrainSamplingMode.COARSE_CORRIDOR, paths);
        } finally {
            if (terrain != null) terrain.dispose();
            cache.clear();
        }
    }

    private static void writeMapTiles(Request request, CoarseTerrainRegion terrain) {
        try {
            CoarseTerrainPngWriter.writeTerrainTiles(request.level(), terrain);
        } catch (RuntimeException ignored) {
            // 地图输出是旁路能力，不能阻断道路规划。
        }
    }
}
