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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 持久路径仓库的进程内前台。
 * 路径点列表可达数十万级，按维度设置 LRU 容量上限防止内存无界增长。
 */
public final class PlannedPathCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int PATH_WORLDGEN_SCHEMA = 1;
    private static final int MAX_ENTRIES_PER_LEVEL = 256;
    private static final Map<ServerLevel, LinkedHashMap<PlannedPathKey, Entry>> BY_LEVEL =
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
        LinkedHashMap<PlannedPathKey, Entry> memory = memory(level);
        PlannedPathStore store = store(level);
        for (Map.Entry<StructureConnection, List<BlockPos>> item : paths.entrySet()) {
            StructureConnection connection = item.getKey();
            List<BlockPos> path = item.getValue();
            if (connection == null || path == null || path.isEmpty()) continue;
            PlannedPathKey key = PlannedPathKey.of(connection);
            List<BlockPos> immutablePath = List.copyOf(path);
            memory.put(key, new Entry(fingerprint, immutablePath));
            if (persistent) {
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
        LinkedHashMap<PlannedPathKey, Entry> memory = memory(level);
        Entry cached = memory.get(key);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return Optional.of(cached.path());
        }
        if (!persistent) return Optional.empty();
        try {
            Optional<List<BlockPos>> loaded = store(level).load(key, fingerprint);
            loaded.ifPresent(path -> memory.put(key, new Entry(fingerprint, path)));
            return loaded;
        } catch (IOException failure) {
            LOGGER.warn("读取待生成路径失败 dimension={} key={}", level.dimension().location(), key, failure);
            return Optional.empty();
        }
    }

    public static void discard(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        PlannedPathKey key = PlannedPathKey.of(connection);
        LinkedHashMap<PlannedPathKey, Entry> memory = BY_LEVEL.get(level);
        if (memory != null) memory.remove(key);
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
    }

    public static void clearAll() {
        synchronized (BY_LEVEL) {
            BY_LEVEL.clear();
        }
    }

    private static LinkedHashMap<PlannedPathKey, Entry> memory(ServerLevel level) {
        synchronized (BY_LEVEL) {
            return BY_LEVEL.computeIfAbsent(level, ignored -> new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PlannedPathKey, Entry> eldest) {
                    return size() > MAX_ENTRIES_PER_LEVEL;
                }
            });
        }
    }

    private static PlannedPathStore store(ServerLevel level) {
        return new FilePlannedPathStore(FileStoragePathResolver.categoryRoot(level, "planned_paths"));
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
}
