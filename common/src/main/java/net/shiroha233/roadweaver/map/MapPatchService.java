package net.shiroha233.roadweaver.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.network.ServerMapPatchBridge;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图局部更新发布服务。
 */
public final class MapPatchService {
    private MapPatchService() {}

    public static void publishConnection(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return;
        List<StructureInfo> endpointInfos = collectEndpointInfos(level, connection);
        MapSnapshotPatch patch = new MapSnapshotPatch(
                endpointInfos,
                collectSources(level, endpointInfos),
                List.of(normalize(connection)),
                List.of(),
                List.of());
        broadcast(level, patch);
    }

    public static void publishStructures(ServerLevel level, List<StructureInfo> structures) {
        if (level == null || structures == null || structures.isEmpty()) return;
        ArrayList<StructureInfo> normalized = new ArrayList<>(structures.size());
        for (StructureInfo info : structures) {
            if (info == null || info.pos() == null || !StructureInfo.isKnownId(info.structureId())) continue;
            normalized.add(new StructureInfo(normalize(info.pos()), info.structureId()));
        }
        if (normalized.isEmpty()) return;
        broadcast(level, new MapSnapshotPatch(normalized, collectSources(level, normalized), List.of(), List.of(), List.of()));
    }

    public static void publishConnectionStatus(ServerLevel level, StructureConnection connection, ConnectionStatus status) {
        if (connection == null || status == null) return;
        publishConnection(level, new StructureConnection(connection.from(), connection.to(), status));
    }

    public static void publishRoadForConnectionAsync(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return;
        ThreadPoolManager.supplyAsync(ThreadPoolManager.TaskRole.MAP, () -> buildRoadPatch(level, connection))
                .thenAccept(patch -> broadcast(level, patch));
    }

    private static void broadcast(ServerLevel level, MapSnapshotPatch patch) {
        if (level == null || patch == null || patch.isEmpty()) return;
        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);
        var server = level.getServer();
        if (server != null) {
            server.execute(() -> {
                for (MapSnapshotPatch chunk : chunks) {
                    ServerMapPatchBridge.broadcast(level, level.dimension().location(), chunk);
                }
            });
        } else {
            for (MapSnapshotPatch chunk : chunks) {
                ServerMapPatchBridge.broadcast(level, level.dimension().location(), chunk);
            }
        }
    }

    private static MapSnapshotPatch buildRoadPatch(ServerLevel level, StructureConnection connection) {
        int minX = Math.min(connection.from().getX(), connection.to().getX());
        int maxX = Math.max(connection.from().getX(), connection.to().getX());
        int minZ = Math.min(connection.from().getZ(), connection.to().getZ());
        int maxZ = Math.max(connection.from().getZ(), connection.to().getZ());
        int pad = 256;
        List<RoadData> roads = RoadShardStorage.queryRect(level, minX - pad, minZ - pad, maxX + pad, maxZ + pad);
        long edgeKey = PlanningUtils.edgeKey(connection.from(), connection.to());
        ArrayList<MapSnapshotPatch.RoadPolylinePatch> roadPatches = new ArrayList<>();
        for (RoadData road : roads) {
            if (road == null || !road.hasOwnerPair()) continue;
            long roadEdgeKey = ownerEdgeKey(road.ownerA2dKey(), road.ownerB2dKey());
            if (roadEdgeKey != edgeKey) continue;
            List<BlockPos> polyline = toPolyline(road);
            if (polyline.size() >= 2) {
                roadPatches.add(new MapSnapshotPatch.RoadPolylinePatch(polylineKey(polyline), polyline));
            }
        }
        if (roadPatches.isEmpty()) return MapSnapshotPatch.empty();
        return new MapSnapshotPatch(List.of(), Map.of(), List.of(), roadPatches, List.of());
    }

    private static List<BlockPos> toPolyline(RoadData road) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        if (segments == null || segments.isEmpty()) return List.of();
        ArrayList<BlockPos> out = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement segment : segments) {
            if (segment != null && segment.middlePos() != null) {
                BlockPos pos = segment.middlePos();
                out.add(new BlockPos(pos.getX(), 0, pos.getZ()));
            }
        }
        return out;
    }

    private static List<StructureInfo> collectEndpointInfos(ServerLevel level, StructureConnection connection) {
        ArrayList<StructureInfo> out = new ArrayList<>(2);
        addEndpointInfo(level, connection.from(), out);
        if (!samePos(connection.from(), connection.to())) addEndpointInfo(level, connection.to(), out);
        return out;
    }

    private static void addEndpointInfo(ServerLevel level, BlockPos endpoint, List<StructureInfo> out) {
        if (endpoint == null) return;
        int x = endpoint.getX();
        int z = endpoint.getZ();
        for (StructureInfo info : StructureFileStorage.queryRect(level, x, z, x, z)) {
            if (info == null || info.pos() == null || !StructureInfo.isKnownId(info.structureId())) continue;
            out.add(new StructureInfo(new BlockPos(x, 0, z), info.structureId()));
            return;
        }
    }

    private static boolean samePos(BlockPos first, BlockPos second) {
        return first != null && second != null
                && first.getX() == second.getX()
                && first.getZ() == second.getZ();
    }

    private static StructureConnection normalize(StructureConnection connection) {
        return new StructureConnection(
                normalize(connection.from()),
                normalize(connection.to()),
                connection.status());
    }

    private static BlockPos normalize(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    private static long ownerEdgeKey(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return (hi << 1) ^ lo;
    }

    private static long polylineKey(List<BlockPos> road) {
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

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static Map<BlockPos, Integer> collectSources(ServerLevel level, List<StructureInfo> structures) {
        if (level == null || structures == null || structures.isEmpty()) return Map.of();
        StructureFileStorage.StructureSnapshot snapshot = StructureFileStorage.getStructureSnapshot(level);
        LinkedHashMap<BlockPos, Integer> sources = new LinkedHashMap<>();
        for (StructureInfo info : structures) {
            if (info == null || info.pos() == null) continue;
            BlockPos pos = normalize(info.pos());
            sources.put(pos, snapshot.sourceAt(pos));
        }
        return sources;
    }
}
