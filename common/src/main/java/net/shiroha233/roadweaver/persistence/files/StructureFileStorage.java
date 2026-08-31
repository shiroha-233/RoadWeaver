/* 文件职责：结构状态内存权威副本、单写者延迟落盘与扫描租约管理。 */
package net.shiroha233.roadweaver.persistence.files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 结构状态存储：内存为权威副本，写入仅标脏，由单写者线程周期 checkpoint。
 * 主线程读写均为内存操作，磁盘 IO 不再出现在任何热路径上。
 */
public final class StructureFileStorage {
    private StructureFileStorage() {}

    public static final int SOURCE_PREDICTED = 0;
    public static final int SOURCE_MANUAL = 1;
    public static final int SCAN_TILE_SIZE_CHUNKS = 128;

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY = "structures";
    private static final String STATE_FILE = "state.json";
    private static final long FLUSH_INTERVAL_MS = 2000;

    private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final class StateData {
        StructureLocationData structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        List<StructureConnection> connections = new ArrayList<>();
        Set<Long> plannedTileKeys = new HashSet<>();
        Map<Long, Long> plannedTileCenters = new HashMap<>();
        Map<Long, Integer> structureSources = new HashMap<>();
        Map<String, String> meta = new HashMap<>();
        Map<Long, Long> scanTiles = new HashMap<>();
    }

    private static final class Entry {
        final Path path;
        final Object lock = new Object();
        StateData data;
        long version;
        long flushed;
        StructureSnapshot snapshot;
        long snapshotVersion = -1;

        Entry(Path path) { this.path = path; }
    }

    private static final ScheduledExecutorService FLUSHER = initFlusher();

    private static ScheduledExecutorService initFlusher() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "roadweaver-structure-state-flusher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                flushDirtyEntries();
            } catch (Throwable t) {
                LOGGER.warn("结构状态周期落盘失败", t);
            }
        }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return executor;
    }

    public record StructureSnapshot(StructureLocationData locations, Map<Long, Integer> sources) {
        public StructureSnapshot {
            locations = locations == null
                    ? new StructureLocationData(new ArrayList<>(), new ArrayList<>())
                    : new StructureLocationData(locations.structureLocations(), locations.structureInfos());
            sources = sources == null ? Map.of() : Map.copyOf(sources);
        }

        public int sourceAt(BlockPos pos) {
            return pos == null ? -1 : sources.getOrDefault(posKey(pos), SOURCE_PREDICTED);
        }
    }

    // ==================== 读取 API（内存操作） ====================

    public static StructureLocationData getStructureLocations(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StructureLocationData data = loaded(entry).structureLocations;
            return new StructureLocationData(data.structureLocations(), data.structureInfos());
        }
    }

    public static List<StructureConnection> getStructureConnections(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            return new ArrayList<>(loaded(entry).connections);
        }
    }

    public static Set<Long> getPlannedTileKeys(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            return new HashSet<>(loaded(entry).plannedTileKeys);
        }
    }

    public static Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            return new HashMap<>(loaded(entry).plannedTileCenters);
        }
    }

    public static StructureSnapshot getStructureSnapshot(ServerLevel level) {
        if (level == null) return new StructureSnapshot(null, Map.of());
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            if (entry.snapshot == null || entry.snapshotVersion != entry.version) {
                entry.snapshot = new StructureSnapshot(state.structureLocations, state.structureSources);
                entry.snapshotVersion = entry.version;
            }
            return entry.snapshot;
        }
    }

    public static int sourceAt(ServerLevel level, BlockPos pos) {
        return getStructureSnapshot(level).sourceAt(pos);
    }

    public static List<StructureInfo> queryRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, int... sources) {
        if (level == null) return List.of();
        Set<Integer> filter = normalizeSources(sources);
        List<StructureInfo> out = new ArrayList<>();
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            for (StructureInfo info : state.structureLocations.structureInfos()) {
                if (info == null || info.pos() == null) continue;
                int x = info.pos().getX();
                int z = info.pos().getZ();
                if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) continue;
                int source = state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED);
                if (filter.contains(source)) out.add(info);
            }
        }
        return out;
    }

    public static boolean hasAnyStructure(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            return !loaded(entry).structureLocations.structureInfos().isEmpty();
        }
    }

    public static boolean hasAnyManualStructure(ServerLevel level) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            for (StructureInfo info : state.structureLocations.structureInfos()) {
                if (info != null && info.pos() != null && state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                    return true;
                }
            }
            for (BlockPos pos : state.structureLocations.structureLocations()) {
                if (pos != null && state.structureSources.getOrDefault(posKey(pos), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Set<Long> manualStructureKeys(ServerLevel level) {
        Entry entry = entry(level);
        HashSet<Long> out = new HashSet<>();
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            for (StructureInfo info : state.structureLocations.structureInfos()) {
                if (info != null && info.pos() != null && state.structureSources.getOrDefault(posKey(info.pos()), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                    out.add(posKey(info.pos()));
                }
            }
            for (BlockPos pos : state.structureLocations.structureLocations()) {
                if (pos != null && state.structureSources.getOrDefault(posKey(pos), SOURCE_PREDICTED) == SOURCE_MANUAL) {
                    out.add(posKey(pos));
                }
            }
        }
        return out;
    }

    // ==================== 写入 API（改内存 + 标脏） ====================

    public static void setStructureLocations(ServerLevel level, StructureLocationData data) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            Map<Long, Integer> previousSources = new HashMap<>(state.structureSources);
            state.structureLocations = data != null ? normalizeStructureLocations(data) : new StructureLocationData(new ArrayList<>(), new ArrayList<>());
            state.structureSources = rebuildSources(state.structureLocations, previousSources, SOURCE_MANUAL);
            markDirty(entry);
        }
    }

    public static void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            state.connections = connections != null ? new ArrayList<>(connections) : new ArrayList<>();
            markDirty(entry);
        }
    }

    public static void mergeStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        if (level == null || connections == null || connections.isEmpty()) return;
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            mergeConnections(state.connections, connections);
            markDirty(entry);
        }
    }

    public static void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            loaded(entry).plannedTileKeys = keys != null ? new HashSet<>(keys) : new HashSet<>();
            markDirty(entry);
        }
    }

    public static void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            loaded(entry).plannedTileCenters = centers != null ? new HashMap<>(centers) : new HashMap<>();
            markDirty(entry);
        }
    }

    public static void addStructures(ServerLevel level, List<StructureInfo> infos, int source) {
        if (level == null || infos == null || infos.isEmpty()) return;
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            for (StructureInfo info : infos) {
                if (info == null || info.pos() == null) continue;
                putStructure(state, info, source);
            }
            markDirty(entry);
        }
    }

    public static void ensurePolicy(ServerLevel level, String policyHash) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            String hash = policyHash == null ? "" : policyHash;
            String current = state.meta.getOrDefault("policy_hash", "");
            if (!current.equals(hash)) {
                keepOnlyManualStructures(state);
                state.scanTiles.clear();
                state.meta.put("policy_hash", hash);
                markDirty(entry);
            }
        }
    }

    public static boolean claimScanTile(ServerLevel level, int tileX, int tileZ) {
        return claimScanKey(level, chunkKey(tileX, tileZ));
    }

    public static void markScanTileDone(ServerLevel level, int tileX, int tileZ) {
        markScanKeyDone(level, chunkKey(tileX, tileZ));
    }

    public static void releaseScanTile(ServerLevel level, int tileX, int tileZ) {
        releaseScanKey(level, chunkKey(tileX, tileZ));
    }

    public static boolean claimScanWindow(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return claimScanKey(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    public static void markScanWindowDone(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        markScanKeyDone(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    public static void releaseScanWindow(ServerLevel level, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        releaseScanKey(level, scanWindowKey(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    // ==================== 生命周期 API ====================

    /** 同步落盘指定维度的脏状态。 */
    public static void flush(ServerLevel level) {
        if (level == null) return;
        Entry entry = ENTRIES.get(FileStoragePathResolver.dimensionKey(level.dimension().location()));
        if (entry != null) flushEntry(entry);
    }

    /** 同步落盘全部维度脏状态。 */
    public static void flushAll() {
        for (Entry entry : ENTRIES.values()) {
            try {
                flushEntry(entry);
            } catch (Throwable t) {
                LOGGER.warn("结构状态同步落盘失败: {}", entry.path, t);
            }
        }
    }

    /** 同步落盘并清空全部缓存条目（服务器停止时调用）。 */
    public static void shutdown() {
        flushAll();
        ENTRIES.clear();
    }

    private static void flushDirtyEntries() {
        for (Entry entry : ENTRIES.values()) {
            flushEntry(entry);
        }
    }

    private static void flushEntry(Entry entry) {
        Snapshot pending = snapshotIfDirty(entry);
        if (pending == null) return;
        writeSnapshot(pending, entry);
    }

    private record Snapshot(StateData data, long version) {}

    private static Snapshot snapshotIfDirty(Entry entry) {
        synchronized (entry.lock) {
            if (entry.data == null || entry.version == entry.flushed) return null;
            return new Snapshot(shallowCopy(entry.data), entry.version);
        }
    }

    private static void writeSnapshot(Snapshot snapshot, Entry entry) {
        try {
            FileStorageIO.writeStringAtomic(entry.path, GSON.toJson(snapshot.data()));
        } catch (IOException e) {
            LOGGER.warn("结构状态落盘失败，稍后重试: {}", entry.path, e);
            return;
        }
        synchronized (entry.lock) {
            if (entry.flushed < snapshot.version()) entry.flushed = snapshot.version();
        }
    }

    private static StateData shallowCopy(StateData data) {
        StateData copy = new StateData();
        StructureLocationData locations = data.structureLocations;
        copy.structureLocations = new StructureLocationData(
                new ArrayList<>(locations.structureLocations()),
                new ArrayList<>(locations.structureInfos()));
        copy.connections = new ArrayList<>(data.connections);
        copy.plannedTileKeys = new HashSet<>(data.plannedTileKeys);
        copy.plannedTileCenters = new HashMap<>(data.plannedTileCenters);
        copy.structureSources = new HashMap<>(data.structureSources);
        copy.meta = new HashMap<>(data.meta);
        copy.scanTiles = new HashMap<>(data.scanTiles);
        return copy;
    }

    private static void markDirty(Entry entry) {
        entry.version++;
        entry.snapshot = null;
    }

    // ==================== 条目与加载 ====================

    private static Entry entry(ServerLevel level) {
        String key = FileStoragePathResolver.dimensionKey(level.dimension().location());
        Path path = FileStoragePathResolver.categoryRoot(level, CATEGORY).resolve(STATE_FILE);
        return ENTRIES.compute(key, (k, existing) -> {
            if (existing != null && existing.path.equals(path)) return existing;
            if (existing != null) flushEntry(existing);
            return new Entry(path);
        });
    }

    private static StateData loaded(Entry entry) {
        if (entry.data != null) return entry.data;
        StateData fileState = readFileState(entry.path);
        entry.version = 0;
        entry.flushed = 0;
        entry.snapshot = null;
        entry.data = normalize(fileState != null ? fileState : new StateData());
        return entry.data;
    }

    private static StateData readFileState(Path path) {
        try {
            if (!Files.exists(path)) return null;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, StateData.class);
            }
        } catch (Exception e) {
            FileStorageIO.quarantineCorrupt(path, LOGGER, "结构状态文件损坏，已隔离");
            return null;
        }
    }

    // ==================== 私有辅助 ====================

    private static boolean claimScanKey(ServerLevel level, long key) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            StateData state = loaded(entry);
            long now = System.currentTimeMillis() / 1000L;
            Long scannedAt = state.scanTiles.get(key);
            if (scannedAt != null && scannedAt > 0) return false;
            if (scannedAt != null) {
                long start = -scannedAt;
                if (now - start < 10 * 60) return false;
            }
            state.scanTiles.put(key, -now);
            markDirty(entry);
            return true;
        }
    }

    private static void markScanKeyDone(ServerLevel level, long key) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            loaded(entry).scanTiles.put(key, System.currentTimeMillis() / 1000L);
            markDirty(entry);
        }
    }

    private static void releaseScanKey(ServerLevel level, long key) {
        Entry entry = entry(level);
        synchronized (entry.lock) {
            loaded(entry).scanTiles.remove(key);
            markDirty(entry);
        }
    }

    private static boolean samePos(BlockPos pos, int x, int z) {
        return pos != null && pos.getX() == x && pos.getZ() == z;
    }

    private static long posKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static int normalizeSource(int source) {
        return source == SOURCE_MANUAL ? SOURCE_MANUAL : SOURCE_PREDICTED;
    }

    private static long scanWindowKey(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        long hash = 0xcbf29ce484222325L;
        hash = mixScanHash(hash, minChunkX);
        hash = mixScanHash(hash, minChunkZ);
        hash = mixScanHash(hash, maxChunkX);
        hash = mixScanHash(hash, maxChunkZ);
        return hash;
    }

    private static long mixScanHash(long hash, int value) {
        hash ^= value;
        hash *= 0x100000001b3L;
        return hash;
    }

    private static Set<Integer> normalizeSources(int[] sources) {
        if (sources == null || sources.length == 0) {
            return Set.of(SOURCE_PREDICTED, SOURCE_MANUAL);
        }
        Set<Integer> out = new HashSet<>();
        for (int source : sources) out.add(normalizeSource(source));
        return out;
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static StateData normalize(StateData state) {
        if (state.structureLocations == null) {
            state.structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        } else {
            state.structureLocations = normalizeStructureLocations(state.structureLocations);
        }
        if (state.connections == null) state.connections = new ArrayList<>();
        if (state.plannedTileKeys == null) state.plannedTileKeys = new HashSet<>();
        if (state.plannedTileCenters == null) state.plannedTileCenters = new HashMap<>();
        if (state.structureSources == null) state.structureSources = new HashMap<>();
        if (state.meta == null) state.meta = new HashMap<>();
        if (state.scanTiles == null) state.scanTiles = new HashMap<>();
        if (state.structureSources.isEmpty()) {
            state.structureSources = rebuildSources(state.structureLocations, Map.of(), SOURCE_PREDICTED);
        }
        return state;
    }

    private static StructureLocationData normalizeStructureLocations(StructureLocationData data) {
        StructureLocationData normalized = new StructureLocationData(data.structureLocations(), data.structureInfos());
        for (BlockPos pos : normalized.structureLocations()) {
            if (pos == null) continue;
            boolean hasInfo = false;
            for (StructureInfo info : normalized.structureInfos()) {
                if (info != null && samePos(info.pos(), pos.getX(), pos.getZ())) {
                    hasInfo = true;
                    break;
                }
            }
            if (!hasInfo) {
                normalized.structureInfos().add(new StructureInfo(pos, "unknown"));
            }
        }
        return normalized;
    }

    private static Map<Long, Integer> rebuildSources(StructureLocationData data, Map<Long, Integer> previous, int defaultSource) {
        HashMap<Long, Integer> out = new HashMap<>();
        if (data != null) {
            for (StructureInfo info : data.structureInfos()) {
                if (info == null || info.pos() == null) continue;
                long key = posKey(info.pos());
                out.put(key, previous.getOrDefault(key, normalizeSource(defaultSource)));
            }
            for (BlockPos pos : data.structureLocations()) {
                if (pos == null) continue;
                long key = posKey(pos);
                out.putIfAbsent(key, previous.getOrDefault(key, normalizeSource(defaultSource)));
            }
        }
        return out;
    }

    private static void putStructure(StateData state, StructureInfo info, int source) {
        int x = info.pos().getX();
        int z = info.pos().getZ();
        long key = posKey(info.pos());
        Integer existingSource = state.structureSources.get(key);
        StructureInfo existingInfo = findStructureInfo(state.structureLocations.structureInfos(), x, z);
        boolean incomingKnown = StructureInfo.isKnownId(info.structureId());
        boolean existingKnown = existingInfo != null && StructureInfo.isKnownId(existingInfo.structureId());
        if (source == SOURCE_PREDICTED && existingSource != null && existingSource == SOURCE_MANUAL) {
            if (!incomingKnown || existingKnown) return;
            source = SOURCE_MANUAL;
        } else if (!incomingKnown && existingKnown) {
            if (source == SOURCE_MANUAL) state.structureSources.put(key, SOURCE_MANUAL);
            return;
        }
        state.structureLocations.structureInfos().removeIf(existing -> existing != null && samePos(existing.pos(), x, z));
        state.structureLocations.structureLocations().removeIf(pos -> samePos(pos, x, z));
        state.structureLocations.structureInfos().add(info);
        state.structureLocations.structureLocations().add(info.pos());
        state.structureSources.put(key, normalizeSource(source));
    }

    private static StructureInfo findStructureInfo(List<StructureInfo> infos, int x, int z) {
        for (StructureInfo info : infos) {
            if (info != null && samePos(info.pos(), x, z)) return info;
        }
        return null;
    }

    private static void keepOnlyManualStructures(StateData state) {
        ArrayList<StructureInfo> manualInfos = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (StructureInfo info : state.structureLocations.structureInfos()) {
            if (info == null || info.pos() == null) continue;
            long key = posKey(info.pos());
            if (state.structureSources.getOrDefault(key, SOURCE_PREDICTED) == SOURCE_MANUAL && seen.add(key)) {
                manualInfos.add(info);
            }
        }
        for (BlockPos pos : state.structureLocations.structureLocations()) {
            if (pos == null) continue;
            long key = posKey(pos);
            if (state.structureSources.getOrDefault(key, SOURCE_PREDICTED) == SOURCE_MANUAL && seen.add(key)) {
                manualInfos.add(new StructureInfo(pos, "unknown"));
            }
        }

        state.structureLocations = new StructureLocationData(new ArrayList<>(), new ArrayList<>());
        state.structureSources.clear();
        for (StructureInfo info : manualInfos) {
            putStructure(state, info, SOURCE_MANUAL);
        }
    }

    private static void mergeConnections(List<StructureConnection> target, List<StructureConnection> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        LinkedHashMap<PlannedPathKey, StructureConnection> merged = new LinkedHashMap<>();
        for (StructureConnection connection : target) {
            if (validConnection(connection)) merged.put(PlannedPathKey.of(connection), connection);
        }
        for (StructureConnection connection : incoming) {
            if (!validConnection(connection)) continue;
            PlannedPathKey key = PlannedPathKey.of(connection);
            StructureConnection previous = merged.get(key);
            if (previous == null || statusPriority(connection.status()) >= statusPriority(previous.status())) {
                merged.put(key, connection);
            }
        }
        target.clear();
        target.addAll(merged.values());
    }

    private static boolean validConnection(StructureConnection connection) {
        return connection != null && connection.from() != null && connection.to() != null;
    }

    private static int statusPriority(ConnectionStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case COMPLETED -> 4;
            case GENERATING -> 3;
            case PLANNED -> 2;
            case FAILED -> 1;
        };
    }
}
