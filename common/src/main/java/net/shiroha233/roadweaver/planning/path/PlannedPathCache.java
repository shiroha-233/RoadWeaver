/* 文件职责：提供待生成路径的内存前台缓存，并协调文件持久层。 */
package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprintService;
import net.shiroha233.roadweaver.persistence.files.FileStoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 持久路径仓库的进程内前台。
 *
 * <p>单条路径可达数十万点，故上限按路径点总数而非条目数控制。
 *
 * <p>access-order 的 {@link LinkedHashMap} 在 {@code get} 时会重排内部双向链表，
 * 并发访问可导致链表成环、{@code removeEldestEntry} 遍历死循环，
 * 因此所有读写必须持 {@link #BY_LEVEL} 监视器；文件 IO 一律在锁外进行。
 */
public final class PlannedPathCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int PATH_WORLDGEN_SCHEMA = 2;
    private static final long MAX_PATH_POINTS_PER_LEVEL = 400_000L;
    private static final int LRU_INITIAL_CAPACITY = 128;

    private static final Map<ServerLevel, LinkedHashMap<PlannedPathKey, Entry>> BY_LEVEL =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ServerLevel, PlannedPathStore> STORES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private PlannedPathCache() {}

    public static void register(ServerLevel level,
                                TerrainSamplingMode effectiveMode,
                                PathfindingCostConfig config,
                                Map<StructureConnection, List<BlockPos>> paths) {
        if (level == null || effectiveMode == null || config == null || paths == null || paths.isEmpty()) return;
        String computedFingerprint = fingerprintOrNull(level, effectiveMode, config);
        boolean persistent = computedFingerprint != null;
        String fingerprint = persistent ? computedFingerprint : "";
        PlannedPathStore store = persistent ? store(level) : null;

        for (Map.Entry<StructureConnection, List<BlockPos>> item : paths.entrySet()) {
            StructureConnection connection = item.getKey();
            List<BlockPos> path = item.getValue();
            if (connection == null || path == null || path.isEmpty()) continue;
            PlannedPathKey key = PlannedPathKey.of(connection);
            List<BlockPos> immutablePath = List.copyOf(path);
            memoryPut(level, key, new Entry(fingerprint, immutablePath));
            if (store != null) {
                // 锁外写盘：避免长时间持有前台缓存监视器
                try {
                    store.save(key, fingerprint, immutablePath);
                } catch (IOException failure) {
                    LOGGER.warn("持久化待生成路径失败 dimension={} key={}", level.dimension().location(), key, failure);
                }
            }
        }
    }

    public static Optional<List<BlockPos>> find(ServerLevel level,
                                                StructureConnection connection,
                                                TerrainSamplingMode effectiveMode,
                                                PathfindingCostConfig config) {
        if (level == null || connection == null || effectiveMode == null || config == null) return Optional.empty();
        PlannedPathKey key = PlannedPathKey.of(connection);
        String computedFingerprint = fingerprintOrNull(level, effectiveMode, config);
        boolean persistent = computedFingerprint != null;
        String fingerprint = persistent ? computedFingerprint : "";

        Entry cached = memoryGet(level, key);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return Optional.of(cached.path());
        }
        if (!persistent) return Optional.empty();
        try {
            Optional<List<BlockPos>> loaded = store(level).load(key, fingerprint);
            loaded.ifPresent(path -> memoryPut(level, key, new Entry(fingerprint, path)));
            return loaded;
        } catch (IOException failure) {
            LOGGER.warn("读取待生成路径失败 dimension={} key={}", level.dimension().location(), key, failure);
            return Optional.empty();
        }
    }

    public static void discard(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        PlannedPathKey key = PlannedPathKey.of(connection);
        memoryRemove(level, key);
        try {
            store(level).delete(key);
        } catch (IOException failure) {
            LOGGER.warn("删除已生成路径失败 dimension={} key={}", level.dimension().location(), key, failure);
        }
    }

    public static void clear(ServerLevel level) {
        if (level == null) return;
        synchronized (BY_LEVEL) {
            BY_LEVEL.remove(level);
        }
        synchronized (STORES) {
            STORES.remove(level);
        }
    }

    public static void clearAll() {
        synchronized (BY_LEVEL) {
            BY_LEVEL.clear();
        }
        synchronized (STORES) {
            STORES.clear();
        }
    }

    // ==================== 线程安全的前台缓存访问 ====================

    private static void memoryPut(ServerLevel level, PlannedPathKey key, Entry entry) {
        synchronized (BY_LEVEL) {
            levelMemory(level).put(key, entry);
        }
    }

    private static Entry memoryGet(ServerLevel level, PlannedPathKey key) {
        synchronized (BY_LEVEL) {
            return levelMemory(level).get(key);
        }
    }

    private static void memoryRemove(ServerLevel level, PlannedPathKey key) {
        synchronized (BY_LEVEL) {
            // 用 get 而非 levelMemory：discard 常用于从未缓存过的维度，
            // computeIfAbsent 会凭空留下一个空桶，只能靠 clear(level) 回收
            LinkedHashMap<PlannedPathKey, Entry> memory = BY_LEVEL.get(level);
            if (memory != null) memory.remove(key);
        }
    }

    /** 必须在 {@link #BY_LEVEL} 监视器内调用。 */
    private static LinkedHashMap<PlannedPathKey, Entry> levelMemory(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new PointBudgetLru());
    }

    private static PlannedPathStore store(ServerLevel level) {
        synchronized (STORES) {
            return STORES.computeIfAbsent(level,
                    ignored -> new FilePlannedPathStore(FileStoragePathResolver.categoryRoot(level, "planned_paths")));
        }
    }

    private static String fingerprintOrNull(ServerLevel level,
                                            TerrainSamplingMode effectiveMode,
                                            PathfindingCostConfig config) {
        try {
            return PlannedPathFingerprintService.create(
                    WorldgenFingerprintService.forLevel(level, PATH_WORLDGEN_SCHEMA),
                    effectiveMode,
                    config);
        } catch (RuntimeException failure) {
            LOGGER.warn("计算待生成路径指纹失败 dimension={}，仅使用内存路径",
                    level.dimension().location(), failure);
            return null;
        }
    }

    private record Entry(String fingerprint, List<BlockPos> path) {}

    /**
     * 以路径点总数为预算的 LRU，让内存占用与实际数据量成正比，
     * 而非随条目数与单条长度相乘。
     */
    private static final class PointBudgetLru extends LinkedHashMap<PlannedPathKey, Entry> {
        private long points;

        PointBudgetLru() {
            super(LRU_INITIAL_CAPACITY, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<PlannedPathKey, Entry> eldest) {
            if (points <= MAX_PATH_POINTS_PER_LEVEL) return false;
            // 逐出最旧条目直到回到预算内
            Iterator<Map.Entry<PlannedPathKey, Entry>> it = entrySet().iterator();
            while (points > MAX_PATH_POINTS_PER_LEVEL && it.hasNext()) {
                Map.Entry<PlannedPathKey, Entry> oldest = it.next();
                points -= cost(oldest.getValue());
                it.remove();
            }
            return false;
        }

        @Override
        public Entry put(PlannedPathKey key, Entry value) {
            // 必须先更新 points 再交给 super.put：后者插入完成后会同步调用
            // removeEldestEntry，那一刻需要看到已包含本次新增的预算。
            // 注意 super.get 会触发 access-order 的链表重排；此处无害——
            // 该条目紧接着就要被覆写并移到队尾，与我们想要的结果一致。
            Entry previous = super.get(key);
            if (previous != null) {
                points -= cost(previous);
            }
            points += cost(value);
            return super.put(key, value);
        }

        @Override
        public Entry remove(Object key) {
            Entry removed = super.remove(key);
            if (removed != null) {
                points -= cost(removed);
            }
            return removed;
        }

        @Override
        public void clear() {
            super.clear();
            points = 0L;
        }

        private static long cost(Entry entry) {
            if (entry == null || entry.path() == null) return 0L;
            return entry.path().size();
        }
    }
}
