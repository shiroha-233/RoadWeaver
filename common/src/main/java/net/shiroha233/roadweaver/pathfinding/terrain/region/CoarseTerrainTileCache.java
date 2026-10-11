package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.persistence.files.CoarseTerrainTileFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 粗采样地形瓦片内存缓存和持久化加载入口。
 */
public final class CoarseTerrainTileCache {
    private CoarseTerrainTileCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MEMORY_CAPACITY = RoadConstants.COARSE_TERRAIN_TILE_CACHE_MAX_ENTRIES;
    private static final Object MEMORY_LOCK = new Object();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile ConcurrentHashMap<CacheKey, CompletableFuture<CoarseTerrainTile>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static Map<CacheKey, CoarseTerrainTile> MEMORY = createMemoryMap();

    /** 同一维度坐标在不同存档中不能共享瓦片或进行单飞合并。 */
    private record CacheKey(ServerLevel level, CoarseTerrainTileKey tile) {
        @Override
        public boolean equals(Object other) {
            return other instanceof CacheKey key && level == key.level && tile.equals(key.tile);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(level) + tile.hashCode();
        }
    }

    public static CoarseTerrainTile getOrLoad(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) return null;

        CacheKey cacheKey = new CacheKey(level, key);
        long generation = GENERATION.get();
        CoarseTerrainTile cached = getMemory(cacheKey);
        if (cached != null) {
            InitialGenerationProgressTracker.recordTileMemoryHit();
            return cached;
        }

        CompletableFuture<CoarseTerrainTile> future = new CompletableFuture<>();
        ConcurrentHashMap<CacheKey, CompletableFuture<CoarseTerrainTile>> inFlight = IN_FLIGHT;
        CompletableFuture<CoarseTerrainTile> existing = inFlight.putIfAbsent(cacheKey, future);
        if (existing != null) {
            try {
                return existing.join();
            } catch (Throwable ignored) {
                return null;
            }
        }

        try {
            CoarseTerrainTile loaded = loadOrSample(level, cacheKey, generation);
            future.complete(loaded);
            return loaded;
        } catch (Throwable t) {
            future.completeExceptionally(t);
            LOGGER.warn("加载粗采样地形瓦片失败 tile=[{},{}]", key.tileX(), key.tileZ(), t);
            return null;
        } finally {
            inFlight.remove(cacheKey, future);
        }
    }

    /**
     * 释放所有缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    public static void clearAll() {
        synchronized (MEMORY_LOCK) {
            GENERATION.incrementAndGet();
            MEMORY = createMemoryMap();
            IN_FLIGHT = new ConcurrentHashMap<>();
        }
    }

    private static LinkedHashMap<CacheKey, CoarseTerrainTile> createMemoryMap() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, CoarseTerrainTile> eldest) {
                return size() > MEMORY_CAPACITY;
            }
        };
    }

    private static CoarseTerrainTile loadOrSample(ServerLevel level, CacheKey cacheKey, long generation) {
        CoarseTerrainTileKey key = cacheKey.tile();
        CoarseTerrainTile cached = getMemory(cacheKey);
        if (cached != null) return cached;

        CoarseTerrainTile stored = CoarseTerrainTileFileStorage.loadTile(level, key);
        if (stored != null) {
            InitialGenerationProgressTracker.recordTileDiskHit();
            putMemory(cacheKey, stored, generation);
            return stored;
        }

        CoarseTerrainTile sampled = CoarseTerrainTileSampler.sample(level, key);
        if (sampled != null) {
            InitialGenerationProgressTracker.recordTileSampled();
            CoarseTerrainTileFileStorage.saveTile(level, sampled);
            putMemory(cacheKey, sampled, generation);
        }
        return sampled;
    }

    private static CoarseTerrainTile getMemory(CacheKey key) {
        synchronized (MEMORY_LOCK) {
            return MEMORY.get(key);
        }
    }

    private static void putMemory(CacheKey key, CoarseTerrainTile tile, long generation) {
        synchronized (MEMORY_LOCK) {
            if (GENERATION.get() == generation) MEMORY.put(key, tile);
        }
    }
}
