package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 构建规划区域级粗采样视图。
 */
public final class CoarseTerrainRegionSampler {
    private CoarseTerrainRegionSampler() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicReference<ThreadPoolExecutor> TILE_EXECUTOR = new AtomicReference<>();
    private static final AtomicLong TILE_THREAD_SEQ = new AtomicLong(1L);
    private static final AtomicLong REGION_LOG_SEQ = new AtomicLong();

    public static CoarseTerrainRegion sample(ServerLevel level,
                                             int minBlockX,
                                             int minBlockZ,
                                             int maxBlockX,
                                             int maxBlockZ,
                                             int step) {
        return sample(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, step,
                CoarseTerrainSamplingProgress.NONE);
    }

    public static CoarseTerrainRegion sample(ServerLevel level,
                                             int minBlockX,
                                             int minBlockZ,
                                             int maxBlockX,
                                             int maxBlockZ,
                                             int step,
                                             CoarseTerrainSamplingProgress progress) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        CoarseTerrainSamplingProgress progressSink = progress == null
                ? CoarseTerrainSamplingProgress.NONE
                : progress;
        int safeStep = Math.max(RoadConstants.ASTAR_STEP_MIN, step);
        CoarseRegionBounds bounds = CoarseRegionBounds.aligned(
                level.dimension().location(), minBlockX, minBlockZ, maxBlockX, maxBlockZ, safeStep);
        List<CoarseTerrainTileKey> keys = collectTileKeys(bounds);
        if (keys.size() > RoadConstants.COARSE_REGION_MAX_TILES) {
            throw new IllegalArgumentException("coarse region tile count too large: " + keys.size());
        }
        progressSink.onPlan(keys.size(), bounds.sampleCount());
        logRegionLoad(bounds, keys.size());

        Map<CoarseTerrainTileKey, CoarseTerrainTile> tiles = loadTiles(level, keys, progressSink);
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("coarse region has no terrain tiles");
        }
        return new CoarseTerrainRegion(bounds, level.getSeaLevel(), tiles);
    }

    private static Map<CoarseTerrainTileKey, CoarseTerrainTile> loadTiles(ServerLevel level,
                                                                          List<CoarseTerrainTileKey> keys,
                                                                          CoarseTerrainSamplingProgress progress) {
        int parallelism = resolveTileLoadParallelism(keys.size());
        if (parallelism <= 1) {
            return loadTilesSequentially(level, keys, progress);
        }
        return loadTilesInParallel(level, keys, parallelism, progress);
    }

    private static void logRegionLoad(CoarseRegionBounds bounds, int tileCount) {
        long seq = REGION_LOG_SEQ.incrementAndGet();
        if (seq <= 8 || tileCount >= 1024) {
            LOGGER.info("粗地形区域按 tile 加载: radiusApproxChunks={} tiles={} samples={} step={} maxTiles={}",
                    approximateRadiusChunks(bounds),
                    tileCount,
                    bounds.sampleCount(),
                    bounds.step(),
                    RoadConstants.COARSE_REGION_MAX_TILES);
        }
    }

    private static int approximateRadiusChunks(CoarseRegionBounds bounds) {
        int diameterBlocks = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ());
        return Math.max(1, Math.floorDiv(diameterBlocks, RoadConstants.CHUNK_SIZE_BLOCKS * 2));
    }

    private static List<CoarseTerrainTileKey> collectTileKeys(CoarseRegionBounds bounds) {
        ArrayList<CoarseTerrainTileKey> keys = new ArrayList<>();
        CoarseTerrainTileKey min = CoarseTerrainTileKey.forBlock(bounds.dimensionId(), bounds.minX(), bounds.minZ(), bounds.step());
        CoarseTerrainTileKey max = CoarseTerrainTileKey.forBlock(bounds.dimensionId(), bounds.maxX(), bounds.maxZ(), bounds.step());
        for (int tileZ = min.tileZ(); tileZ <= max.tileZ(); tileZ++) {
            for (int tileX = min.tileX(); tileX <= max.tileX(); tileX++) {
                keys.add(new CoarseTerrainTileKey(
                        bounds.dimensionId(),
                        tileX,
                        tileZ,
                        RoadConstants.COARSE_TERRAIN_TILE_SIZE_CHUNKS,
                        bounds.step(),
                        RoadConstants.COARSE_TERRAIN_TILE_SCHEMA_VERSION));
            }
        }
        return keys;
    }

    private static Map<CoarseTerrainTileKey, CoarseTerrainTile> loadTilesSequentially(ServerLevel level,
                                                                                     List<CoarseTerrainTileKey> keys,
                                                                                     CoarseTerrainSamplingProgress progress) {
        HashMap<CoarseTerrainTileKey, CoarseTerrainTile> out = new HashMap<>();
        int completed = 0;
        for (CoarseTerrainTileKey key : keys) {
            if (Thread.currentThread().isInterrupted()) return out;
            CoarseTerrainTile tile = CoarseTerrainTileCache.getOrLoad(level, key);
            if (tile != null) {
                out.put(key, tile);
            }
            progress.onTileCompleted(++completed, keys.size());
        }
        return out;
    }

    private static Map<CoarseTerrainTileKey, CoarseTerrainTile> loadTilesInParallel(ServerLevel level,
                                                                                   List<CoarseTerrainTileKey> keys,
                                                                                   int parallelism,
                                                                                   CoarseTerrainSamplingProgress progress) {
        HashMap<CoarseTerrainTileKey, CoarseTerrainTile> out = new HashMap<>();
        ExecutorCompletionService<TileLoadResult> completion = new ExecutorCompletionService<>(tileExecutor(parallelism));

        int submitted = 0;
        int completed = 0;
        int window = Math.min(parallelism, keys.size());
        while (submitted < window) {
            submitLoad(completion, level, keys.get(submitted++));
        }

        while (completed < keys.size()) {
            if (Thread.currentThread().isInterrupted()) return out;
            try {
                Future<TileLoadResult> future = completion.take();
                TileLoadResult result = future.get();
                if (result != null && result.tile() != null) {
                    out.put(result.key(), result.tile());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return out;
            } catch (ExecutionException ignored) {
            } finally {
                completed++;
                progress.onTileCompleted(completed, keys.size());
                if (submitted < keys.size()) {
                    submitLoad(completion, level, keys.get(submitted++));
                }
            }
        }
        return out;
    }

    private static void submitLoad(ExecutorCompletionService<TileLoadResult> completion,
                                   ServerLevel level,
                                   CoarseTerrainTileKey key) {
        completion.submit(() -> new TileLoadResult(key, CoarseTerrainTileCache.getOrLoad(level, key)));
    }

    private static int resolveTileLoadParallelism(int tileCount) {
        if (tileCount <= 1) return tileCount;
        int configured;
        try {
            configured = ConfigService.get().performance().sharedWorkerThreads();
        } catch (Throwable ignored) {
            configured = Runtime.getRuntime().availableProcessors();
        }
        int workers = Math.max(1, configured);
        int capped = Math.min(workers, RoadConstants.COARSE_REGION_PARALLEL_MAX_THREADS);
        return Math.max(1, Math.min(tileCount, capped));
    }

    private static ThreadPoolExecutor tileExecutor(int parallelism) {
        int threads = Math.max(1, Math.min(RoadConstants.COARSE_REGION_PARALLEL_MAX_THREADS, parallelism));
        ThreadPoolExecutor current = TILE_EXECUTOR.get();
        if (current != null && !current.isShutdown()) {
            return current;
        }
        synchronized (CoarseTerrainRegionSampler.class) {
            current = TILE_EXECUTOR.get();
            if (current != null && !current.isShutdown()) {
                return current;
            }
            ThreadPoolExecutor next = new ThreadPoolExecutor(
                    threads,
                    threads,
                    30L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    tileThreadFactory());
            next.allowCoreThreadTimeOut(true);
            TILE_EXECUTOR.set(next);
            return next;
        }
    }

    private static ThreadFactory tileThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "RW-CoarseTile-" + TILE_THREAD_SEQ.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record TileLoadResult(CoarseTerrainTileKey key, CoarseTerrainTile tile) {}
}
