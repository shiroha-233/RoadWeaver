package net.shiroha233.roadweaver.map.tile.sampling;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.map.tile.storage.AccurateTerrainMapFingerprintGuard;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingProgress;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateRegionBounds;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseRegionBounds;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainSamplingProgress;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileKey;
import net.shiroha233.roadweaver.pathfinding.terrain.region.TerrainPngWriteProgress;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessions;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.search.StructureIndexService;
import net.shiroha233.roadweaver.util.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主动地图采样用例。每个地图界面实例持有一个服务，任务状态通过不可变快照发布。
 */
public final class MapTerrainSamplingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int TERRAIN_SAMPLING_PERCENT = 70;
    private static final int STRUCTURE_QUERY_PERCENT = 10;
    private static final int PNG_WRITING_PERCENT = 100 - TERRAIN_SAMPLING_PERCENT - STRUCTURE_QUERY_PERCENT;

    private final AtomicReference<MapSamplingSnapshot> snapshot =
            new AtomicReference<>(MapSamplingSnapshot.idle());
    private final MapStructureSamplingPort structureSampling;

    public MapTerrainSamplingService() {
        this(MapTerrainSamplingService::queryStructures);
    }

    MapTerrainSamplingService(MapStructureSamplingPort structureSampling) {
        this.structureSampling = Objects.requireNonNull(structureSampling, "structureSampling");
    }

    public MapSamplingSnapshot snapshot() {
        return snapshot.get();
    }

    public boolean isAvailable(ServerLevel level) {
        if (level == null) return false;
        return resolveSamplingMode(TerrainSamplingSessions.forLevel(level).snapshot()) != null;
    }

    public synchronized StartResult start(ServerLevel level,
                                          MapSamplingBounds requestedBounds) {
        if (snapshot.get().active()) {
            return StartResult.ALREADY_RUNNING;
        }
        if (level == null || requestedBounds == null) {
            return StartResult.WORLD_UNAVAILABLE;
        }

        TerrainSamplingMode mode = resolveSamplingMode(TerrainSamplingSessions.forLevel(level).snapshot());
        if (mode == null) {
            return StartResult.MODE_UNAVAILABLE;
        }

        SamplingPlan plan;
        try {
            plan = createSamplingPlan(level, requestedBounds, mode);
        } catch (RuntimeException invalidBounds) {
            return StartResult.RANGE_TOO_LARGE;
        }
        if (plan == null) {
            return StartResult.RANGE_TOO_LARGE;
        }

        MapSamplingBounds visibleBounds = plan.visibleBounds();
        publish(MapSamplingSnapshot.Stage.SAMPLING_TERRAIN, visibleBounds, 0);
        long epoch = ThreadPoolManager.currentEpoch();
        try {
            ComputeService.runMapAsync(() -> runSampling(level, plan, epoch));
        } catch (RuntimeException submissionFailure) {
            publish(MapSamplingSnapshot.Stage.FAILED, visibleBounds, 0);
            LOGGER.warn("提交主动地图采样任务失败 dimension={} mode={}",
                    level.dimension().location(), mode, submissionFailure);
            return StartResult.SUBMISSION_FAILED;
        }
        return StartResult.STARTED;
    }

    private SamplingPlan createSamplingPlan(ServerLevel level,
                                            MapSamplingBounds requestedBounds,
                                            TerrainSamplingMode mode) {
        int step = Math.max(
                RoadConstants.ASTAR_STEP_MIN,
                ConfigService.get().pathfindingCost().effectiveAStarStep());
        return switch (mode) {
            case FULL_REGION -> createAccuratePlan(requestedBounds, step, mode);
            case LEGACY_DIRECT, COARSE_CORRIDOR -> createCoarsePlan(
                    level, requestedBounds, step, mode);
        };
    }

    private static CoarseSamplingPlan createCoarsePlan(ServerLevel level,
                                                        MapSamplingBounds requestedBounds,
                                                        int step,
                                                        TerrainSamplingMode mode) {
        CoarseRegionBounds bounds = CoarseRegionBounds.aligned(
                level.dimension().location(),
                requestedBounds.minX(), requestedBounds.minZ(),
                requestedBounds.maxX(), requestedBounds.maxZ(),
                step);
        if (!isWithinCoarseManualLimits(bounds)) {
            return null;
        }
        return new CoarseSamplingPlan(mode, visibleBounds(bounds), bounds);
    }

    private static AccurateSamplingPlan createAccuratePlan(MapSamplingBounds requestedBounds,
                                                            int step,
                                                            TerrainSamplingMode mode) {
        AccurateRegionBounds bounds = AccurateRegionBounds.aligned(
                requestedBounds.minX(), requestedBounds.minZ(),
                requestedBounds.maxX(), requestedBounds.maxZ(),
                step);
        if (!isWithinAccurateManualLimits(bounds)) {
            return null;
        }
        return new AccurateSamplingPlan(mode, visibleBounds(bounds), bounds);
    }

    private void runSampling(ServerLevel level, SamplingPlan plan, long epoch) {
        MapSamplingBounds visibleBounds = plan.visibleBounds();
        try {
            ensureActiveEpoch(epoch);
            if (plan instanceof CoarseSamplingPlan coarsePlan) {
                sampleCoarse(level, coarsePlan, epoch);
            } else if (plan instanceof AccurateSamplingPlan accuratePlan) {
                sampleAccurate(level, accuratePlan, epoch);
            } else {
                throw new IllegalStateException("Unsupported map sampling plan: " + plan.getClass().getName());
            }
            ensureActiveEpoch(epoch);
            queryStructures(level, visibleBounds, epoch);
            ensureActiveEpoch(epoch);
            publish(MapSamplingSnapshot.Stage.COMPLETED, visibleBounds, 100);
        } catch (CancellationException cancelled) {
            publish(MapSamplingSnapshot.Stage.FAILED, visibleBounds, snapshot.get().percent());
        } catch (RuntimeException failure) {
            publish(MapSamplingSnapshot.Stage.FAILED, visibleBounds, snapshot.get().percent());
            LOGGER.warn("主动地图采样失败 dimension={} mode={} bounds=[{},{} -> {},{}]",
                    level.dimension().location(),
                    plan.mode(),
                    visibleBounds.minX(), visibleBounds.minZ(),
                    visibleBounds.maxX(), visibleBounds.maxZ(),
                    failure);
        }
    }

    private void sampleCoarse(ServerLevel level,
                              CoarseSamplingPlan plan,
                              long epoch) {
        CoarseTerrainRegion region = null;
        try {
            CoarseRegionBounds bounds = plan.bounds();
            region = CoarseTerrainRegionSampler.sample(
                    level,
                    bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), bounds.step(),
                    coarseSamplingProgress(plan.visibleBounds()));
            ensureActiveEpoch(epoch);
            beginPngWrite(plan.visibleBounds());
            CoarseTerrainPngWriter.writeTerrainTiles(level, region, pngProgress(plan.visibleBounds()));
        } finally {
            if (region != null) {
                region.dispose();
            }
        }
    }

    private void sampleAccurate(ServerLevel level,
                                AccurateSamplingPlan plan,
                                long epoch) {
        TerrainSamplingCache cache = new TerrainSamplingCache();
        AccurateTerrainRegion region = null;
        try {
            AccurateTerrainMapFingerprintGuard.ensure(level);
            AccurateRegionBounds bounds = plan.bounds();
            region = AccurateTerrainRegionSampler.sampleForMap(
                    level,
                    cache,
                    bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), bounds.step(),
                    true,
                    accurateSamplingProgress(plan.visibleBounds()));
            ensureActiveEpoch(epoch);
            beginPngWrite(plan.visibleBounds());
            AccurateTerrainPngWriter.writeTerrainTiles(level, region, pngProgress(plan.visibleBounds()));
        } finally {
            if (region != null) {
                region.dispose();
            }
            cache.clear();
        }
    }

    private CoarseTerrainSamplingProgress coarseSamplingProgress(MapSamplingBounds bounds) {
        return new CoarseTerrainSamplingProgress() {
            @Override
            public void onPlan(int totalTiles, long totalSamples) {
                publish(MapSamplingSnapshot.Stage.SAMPLING_TERRAIN, bounds, 0);
            }

            @Override
            public void onTileCompleted(int completedTiles, int totalTiles) {
                publish(MapSamplingSnapshot.Stage.SAMPLING_TERRAIN,
                        bounds,
                        weightedPercent(completedTiles, totalTiles, 0, TERRAIN_SAMPLING_PERCENT));
            }
        };
    }

    private AccurateSamplingProgress accurateSamplingProgress(MapSamplingBounds bounds) {
        return batch -> publish(MapSamplingSnapshot.Stage.SAMPLING_TERRAIN,
                bounds,
                weightedPercent(batch.completedColumns(), batch.totalColumns(), 0, TERRAIN_SAMPLING_PERCENT));
    }

    private TerrainPngWriteProgress pngProgress(MapSamplingBounds bounds) {
        return (completed, total) -> publish(MapSamplingSnapshot.Stage.WRITING_PNG,
                bounds,
                weightedPercent(completed, total,
                        TERRAIN_SAMPLING_PERCENT,
                        PNG_WRITING_PERCENT));
    }

    private void beginPngWrite(MapSamplingBounds bounds) {
        publish(MapSamplingSnapshot.Stage.WRITING_PNG, bounds, TERRAIN_SAMPLING_PERCENT);
    }

    private void queryStructures(ServerLevel level,
                                 MapSamplingBounds bounds,
                                 long epoch) {
        ensureActiveEpoch(epoch);
        publish(MapSamplingSnapshot.Stage.QUERYING_STRUCTURES,
                bounds,
                TERRAIN_SAMPLING_PERCENT + PNG_WRITING_PERCENT);
        structureSampling.query(level, bounds);
    }

    private static void queryStructures(ServerLevel level, MapSamplingBounds bounds) {
        if (level == null || bounds == null) return;
        StructureIndexService.predictAndVerifyInRect(
                level,
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    private void publish(MapSamplingSnapshot.Stage stage,
                         MapSamplingBounds bounds,
                         int percent) {
        snapshot.set(new MapSamplingSnapshot(stage, bounds, percent));
    }

    static TerrainSamplingMode resolveSamplingMode(TerrainSamplingSessionSnapshot session) {
        return session == null ? null : session.effectiveMode();
    }

    static boolean isWithinCoarseManualLimits(CoarseRegionBounds bounds) {
        return bounds != null
                && bounds.sampleCount() <= RoadConstants.COARSE_REGION_MAX_SAMPLES
                && coarseTileCount(bounds) <= RoadConstants.MANUAL_MAP_SAMPLING_MAX_COARSE_TILES;
    }

    static boolean isWithinAccurateManualLimits(AccurateRegionBounds bounds) {
        return bounds != null && bounds.sampleCount() <= RoadConstants.ACCURATE_REGION_MAX_SAMPLES;
    }

    static long coarseTileCount(CoarseRegionBounds bounds) {
        CoarseTerrainTileKey min = CoarseTerrainTileKey.forBlock(
                bounds.dimensionId(), bounds.minX(), bounds.minZ(), bounds.step());
        CoarseTerrainTileKey max = CoarseTerrainTileKey.forBlock(
                bounds.dimensionId(), bounds.maxX(), bounds.maxZ(), bounds.step());
        long width = (long) max.tileX() - min.tileX() + 1L;
        long height = (long) max.tileZ() - min.tileZ() + 1L;
        try {
            return Math.multiplyExact(width, height);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static int weightedPercent(long completed, long total, int start, int span) {
        if (total <= 0L) return Math.max(0, Math.min(100, start));
        double ratio = Math.max(0.0D, Math.min(1.0D, completed / (double) total));
        return Math.max(0, Math.min(100, start + (int) Math.round(span * ratio)));
    }

    private static MapSamplingBounds visibleBounds(CoarseRegionBounds bounds) {
        return new MapSamplingBounds(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    private static MapSamplingBounds visibleBounds(AccurateRegionBounds bounds) {
        return new MapSamplingBounds(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    private static void ensureActiveEpoch(long epoch) {
        if (Thread.currentThread().isInterrupted() || !ThreadPoolManager.isEpoch(epoch)) {
            throw new CancellationException("map sampling task is no longer active");
        }
    }

    private sealed interface SamplingPlan permits CoarseSamplingPlan, AccurateSamplingPlan {
        TerrainSamplingMode mode();

        MapSamplingBounds visibleBounds();
    }

    private record CoarseSamplingPlan(TerrainSamplingMode mode,
                                      MapSamplingBounds visibleBounds,
                                      CoarseRegionBounds bounds) implements SamplingPlan {}

    private record AccurateSamplingPlan(TerrainSamplingMode mode,
                                        MapSamplingBounds visibleBounds,
                                        AccurateRegionBounds bounds) implements SamplingPlan {}

    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        WORLD_UNAVAILABLE,
        MODE_UNAVAILABLE,
        RANGE_TOO_LARGE,
        SUBMISSION_FAILED
    }
}
