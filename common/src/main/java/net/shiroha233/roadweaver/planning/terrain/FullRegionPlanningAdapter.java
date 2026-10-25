package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.map.tile.storage.AccurateTerrainMapFingerprintGuard;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AcceleratedSamplingUnavailableException;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateRegionLimitExceededException;

import java.util.LinkedHashMap;
import java.util.List;

final class FullRegionPlanningAdapter implements RoadTerrainPlanningPort {
    @Override
    public Result plan(Request request) {
        if (request.connections().isEmpty()) {
            return Result.empty(TerrainSamplingMode.FULL_REGION);
        }
        TerrainSamplingCache cache = new TerrainSamplingCache();
        AccurateTerrainRegion region = null;
        try {
            AccurateTerrainMapFingerprintGuard.ensure(request.level());
            AccurateHeightSampler sampler = cache.getAccurateSampler(request.level());
            if (!sampler.supportsAcceleratedSampling()) {
                throw new FullRegionUnavailableException("FP64 OpenCL GPU is unavailable");
            }
            Bounds bounds = request.bounds();
            region = AccurateTerrainRegionSampler.sampleAccelerated(
                    request.level(), cache,
                    bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(),
                    request.pathfinding().effectiveAStarStep());

            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.EXACT_PATHING, "computing_accurate_paths");
            InitialGenerationProgressTracker.setExactPathPlan(
                    request.connections().size(), "computing_accurate_paths");
            LinkedHashMap<StructureConnection, List<BlockPos>> paths = new LinkedHashMap<>();
            for (StructureConnection connection : request.connections()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("full-region planning interrupted");
                }
                try {
                    List<BlockPos> path = RawPathSearch.find(
                            request.level(), connection, request.pathfinding(), cache, region);
                    if (path != null && !path.isEmpty()) {
                        paths.put(connection, path);
                    }
                } finally {
                    InitialGenerationProgressTracker.recordExactPathDone();
                }
            }
            try {
                AccurateTerrainPngWriter.writeTerrainTiles(request.level(), region);
            } catch (RuntimeException ignored) {
                // 地图是派生产物，写入失败不应使采样策略降级。
            }
            return new Result(TerrainSamplingMode.FULL_REGION, paths);
        } catch (java.util.concurrent.CancellationException interrupted) {
            throw interrupted;
        } catch (FullRegionUnavailableException unavailable) {
            throw unavailable;
        } catch (AcceleratedSamplingUnavailableException | AccurateRegionLimitExceededException failure) {
            throw new FullRegionUnavailableException(
                    "full-region accurate sampling failed: " + failure.getMessage(), failure);
        } catch (OutOfMemoryError insufficientMemory) {
            throw new FullRegionUnavailableException("insufficient memory for full-region accurate sampling", insufficientMemory);
        } finally {
            if (region != null && !region.isDisposed()) {
                region.dispose();
            }
            cache.clear();
        }
    }
}
