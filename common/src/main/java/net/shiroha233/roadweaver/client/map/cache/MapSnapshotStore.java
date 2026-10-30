package net.shiroha233.roadweaver.client.map.cache;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.map.data.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.MapViewportController;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.structure.MapStructureSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.map.data.RequestRect;

/**
 * 客户端地图数据仓库。
 *
 * 负责把多次视口请求返回的数据沉淀为一个可复用快照，并记录每个加载阶段已经覆盖的区域。
 */
public final class MapSnapshotStore {
    private final LinkedHashMap<Long, BlockPos> structuresByPos = new LinkedHashMap<>();
    private final LinkedHashMap<Long, String> structureNamesByPos = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Integer> structureSourcesByPos = new LinkedHashMap<>();
    private final LinkedHashMap<ConnectionKey, StructureConnection> connectionsByKey = new LinkedHashMap<>();
    private final LinkedHashMap<Long, List<BlockPos>> roadsByKey = new LinkedHashMap<>();
    private final EnumMap<MapLoadPhase, List<RequestRect>> loadedRects = new EnumMap<>(MapLoadPhase.class);

    public MapSnapshotStore() {
        for (MapLoadPhase phase : MapLoadPhase.values()) {
            loadedRects.put(phase, new ArrayList<>());
        }
    }

    public static MapSnapshotStore fromSnapshot(MapSnapshot snapshot) {
        MapSnapshotStore store = new MapSnapshotStore();
        store.mergeData(snapshot);
        return store;
    }

    public synchronized void merge(MapLoadPhase phase,
                                   RequestRect loadedRect,
                                   MapSnapshot snapshot) {
        mergeData(snapshot);
        markLoaded(phase, loadedRect);
    }

    public synchronized void apply(MapSnapshotPatch patch) {
        if (patch == null) return;
        mergeStructureInfos(patch.structures(), patch.structureSources());
        replaceConnections(patch.connections());
        mergeRoadPatches(patch.roads());
        for (MapSnapshotPatch.LoadedRect loadedRect : patch.loadedRects()) {
            if (loadedRect != null) {
                markLoaded(loadedRect.phase(), loadedRect.rect());
            }
        }
    }

    public synchronized void mergeData(MapSnapshot snapshot) {
        if (snapshot == null) return;
        mergeStructures(snapshot);
        mergeConnections(snapshot.connections());
        mergeRoads(snapshot.roadPolylines());
    }

    public synchronized MapSnapshot snapshot() {
        ArrayList<BlockPos> structures = new ArrayList<>(structuresByPos.values());
        ArrayList<StructureConnection> connections = new ArrayList<>(connectionsByKey.values());
        ArrayList<StructureInfo> infos = new ArrayList<>(structureNamesByPos.size());
        LinkedHashMap<BlockPos, Integer> sources = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : structureNamesByPos.entrySet()) {
            BlockPos pos = structuresByPos.get(entry.getKey());
            if (pos != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                infos.add(new StructureInfo(pos, entry.getValue()));
            }
        }
        for (Map.Entry<Long, BlockPos> entry : structuresByPos.entrySet()) {
            Integer source = structureSourcesByPos.get(entry.getKey());
            if (source != null) sources.put(entry.getValue(), source);
        }
        ArrayList<List<BlockPos>> roads = new ArrayList<>(roadsByKey.values());
        return new MapSnapshot(structures, connections, infos, roads, sources);
    }

    public synchronized List<RequestRect> loadedRects(MapLoadPhase phase) {
        List<RequestRect> rects = loadedRects.get(phase);
        if (rects == null || rects.isEmpty()) return List.of();
        return new ArrayList<>(rects);
    }

    public synchronized void invalidate(MapLoadPhase phase, RequestRect dirtyRect) {
        if (phase == null || dirtyRect == null) return;
        List<RequestRect> rects = loadedRects.get(phase);
        if (rects == null || rects.isEmpty()) return;
        ArrayList<RequestRect> next = new ArrayList<>();
        for (RequestRect rect : rects) {
            next.addAll(MapViewportController.subtract(rect, dirtyRect));
        }
        loadedRects.put(phase, next);
    }

    public synchronized void clearRect(MapLoadPhase phase, RequestRect rect) {
        if (phase == null || rect == null) return;
        invalidate(phase, rect);
        switch (phase) {
            case STRUCTURES -> {
                var iterator = structuresByPos.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Long, BlockPos> entry = iterator.next();
                    if (inside(entry.getValue(), rect)) {
                        iterator.remove();
                        structureNamesByPos.remove(entry.getKey());
                        structureSourcesByPos.remove(entry.getKey());
                    }
                }
            }
            case CONNECTIONS -> connectionsByKey.entrySet().removeIf(entry -> intersects(entry.getValue(), rect));
            case ROADS -> roadsByKey.entrySet().removeIf(entry -> intersects(entry.getValue(), rect));
        }
    }

    public synchronized void invalidateAllLoadedRects() {
        for (MapLoadPhase phase : MapLoadPhase.values()) {
            loadedRects.get(phase).clear();
        }
    }

    private void markLoaded(MapLoadPhase phase, RequestRect loadedRect) {
        if (phase == null || loadedRect == null) return;
        List<RequestRect> rects = loadedRects.computeIfAbsent(phase, ignored -> new ArrayList<>());
        rects.add(loadedRect);
    }

    private void mergeStructures(MapSnapshot snapshot) {
        for (BlockPos pos : snapshot.structures()) {
            if (pos == null) continue;
            BlockPos normalized = normalize(pos);
            long key = posKey(normalized);
            structuresByPos.putIfAbsent(key, normalized);
            mergeStructureSource(key, snapshot.structureSource(pos));
            String name = snapshot.structureName(pos);
            mergeStructureName(key, name);
        }
    }

    private void mergeStructureInfos(List<StructureInfo> infos, Map<BlockPos, Integer> sources) {
        if (infos == null || infos.isEmpty()) return;
        for (StructureInfo info : infos) {
            if (info == null || info.pos() == null) continue;
            BlockPos normalized = normalize(info.pos());
            long key = posKey(normalized);
            structuresByPos.putIfAbsent(key, normalized);
            int source = sources == null
                    ? MapStructureSource.UNKNOWN.id()
                    : sources.getOrDefault(normalized, MapStructureSource.UNKNOWN.id());
            mergeStructureSource(key, source);
            String name = info.structureId();
            mergeStructureName(key, name);
        }
    }

    private void mergeStructureName(long key, String name) {
        if (name == null || name.isBlank()) return;
        String previous = structureNamesByPos.get(key);
        if (previous == null || previous.isBlank()
                || (!StructureInfo.isKnownId(previous) && StructureInfo.isKnownId(name))) {
            structureNamesByPos.put(key, name);
        }
    }

    private void mergeStructureSource(long key, int source) {
        Integer previous = structureSourcesByPos.get(key);
        if (previous == null || source != MapStructureSource.UNKNOWN.id()) {
            structureSourcesByPos.put(key, source);
        }
    }

    private void mergeConnections(List<StructureConnection> connections) {
        if (connections == null || connections.isEmpty()) return;
        for (StructureConnection connection : connections) {
            if (connection == null || connection.from() == null || connection.to() == null) continue;
            StructureConnection normalized = normalize(connection);
            ensureStructurePresent(normalized.from());
            ensureStructurePresent(normalized.to());
            ConnectionKey key = ConnectionKey.of(normalized);
            StructureConnection previous = connectionsByKey.get(key);
            if (previous == null || statusPriority(normalized.status()) >= statusPriority(previous.status())) {
                connectionsByKey.put(key, normalized);
            }
        }
    }

    private void replaceConnections(List<StructureConnection> connections) {
        if (connections == null || connections.isEmpty()) return;
        for (StructureConnection connection : connections) {
            if (connection == null || connection.from() == null || connection.to() == null) continue;
            StructureConnection normalized = normalize(connection);
            ensureStructurePresent(normalized.from());
            ensureStructurePresent(normalized.to());
            connectionsByKey.put(ConnectionKey.of(normalized), normalized);
        }
    }

    private void ensureStructurePresent(BlockPos pos) {
        if (pos == null) return;
        BlockPos normalized = normalize(pos);
        structuresByPos.putIfAbsent(posKey(normalized), normalized);
        structureSourcesByPos.putIfAbsent(posKey(normalized), MapStructureSource.UNKNOWN.id());
    }

    private void mergeRoads(List<List<BlockPos>> roads) {
        if (roads == null || roads.isEmpty()) return;
        for (List<BlockPos> road : roads) {
            if (road == null || road.size() < 2) continue;
            ArrayList<BlockPos> normalized = new ArrayList<>(road.size());
            for (BlockPos pos : road) {
                if (pos != null) normalized.add(normalize(pos));
            }
            if (normalized.size() < 2) continue;
            roadsByKey.put(roadKey(normalized), List.copyOf(normalized));
        }
    }

    private void mergeRoadPatches(List<MapSnapshotPatch.RoadPolylinePatch> roads) {
        if (roads == null || roads.isEmpty()) return;
        for (MapSnapshotPatch.RoadPolylinePatch road : roads) {
            if (road == null || road.points().size() < 2) continue;
            ArrayList<BlockPos> normalized = new ArrayList<>(road.points().size());
            for (BlockPos pos : road.points()) {
                if (pos != null) normalized.add(normalize(pos));
            }
            if (normalized.size() < 2) continue;
            roadsByKey.put(road.key(), List.copyOf(normalized));
        }
    }

    private static BlockPos normalize(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    private static StructureConnection normalize(StructureConnection connection) {
        return new StructureConnection(normalize(connection.from()), normalize(connection.to()), connection.status());
    }

    private static long posKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static long roadKey(List<BlockPos> road) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPos pos : road) {
            hash ^= pos.getX();
            hash *= 0x100000001b3L;
            hash ^= pos.getZ();
            hash *= 0x100000001b3L;
        }
        hash ^= road.size();
        hash *= 0x100000001b3L;
        return hash;
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

    private static boolean inside(BlockPos pos, RequestRect rect) {
        return pos != null
                && pos.getX() >= rect.minX() && pos.getX() <= rect.maxX()
                && pos.getZ() >= rect.minZ() && pos.getZ() <= rect.maxZ();
    }

    private static boolean intersects(StructureConnection connection, RequestRect rect) {
        if (connection == null || connection.from() == null || connection.to() == null) return false;
        int minX = Math.min(connection.from().getX(), connection.to().getX());
        int maxX = Math.max(connection.from().getX(), connection.to().getX());
        int minZ = Math.min(connection.from().getZ(), connection.to().getZ());
        int maxZ = Math.max(connection.from().getZ(), connection.to().getZ());
        return maxX >= rect.minX() && minX <= rect.maxX()
                && maxZ >= rect.minZ() && minZ <= rect.maxZ();
    }

    private static boolean intersects(List<BlockPos> road, RequestRect rect) {
        if (road == null || road.isEmpty()) return false;
        for (BlockPos pos : road) {
            if (inside(pos, rect)) return true;
        }
        for (int i = 1; i < road.size(); i++) {
            BlockPos previous = road.get(i - 1);
            BlockPos current = road.get(i);
            if (previous == null || current == null) continue;
            int minX = Math.min(previous.getX(), current.getX());
            int maxX = Math.max(previous.getX(), current.getX());
            int minZ = Math.min(previous.getZ(), current.getZ());
            int maxZ = Math.max(previous.getZ(), current.getZ());
            if (maxX >= rect.minX() && minX <= rect.maxX()
                    && maxZ >= rect.minZ() && minZ <= rect.maxZ()) return true;
        }
        return false;
    }

    private record ConnectionKey(long a, long b) {
        private static ConnectionKey of(StructureConnection connection) {
            long from = posKey(connection.from());
            long to = posKey(connection.to());
            return from <= to ? new ConnectionKey(from, to) : new ConnectionKey(to, from);
        }
    }
}
