package net.shiroha233.roadweaver.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.MapViewportController;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.map.search.MapStructureSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图快照编解码器
 */
public final class MapSnapshotCodec {
    /** 状态序数查表：{@code values()} 每次调用都会克隆整个枚举数组。 */
    private static final ConnectionStatus[] STATUSES = ConnectionStatus.values();

    private MapSnapshotCodec() {}

    public static void write(FriendlyByteBuf buf, MapSnapshot s) {
        List<BlockPos> structures = s.structures();
        List<StructureConnection> conns = s.connections();
        
        buf.writeVarInt(structures.size());
        for (BlockPos p : structures) {
            buf.writeBlockPos(p);
        }
        
        for (BlockPos p : structures) {
            String name = s.structureName(p);
            boolean has = name != null;
            buf.writeBoolean(has);
            if (has) {
                buf.writeUtf(name);
            }
            buf.writeVarInt(s.structureSource(p) + 1);
        }
        
        buf.writeVarInt(conns.size());
        for (StructureConnection c : conns) {
            buf.writeBlockPos(c.from());
            buf.writeBlockPos(c.to());
            buf.writeVarInt(c.status().ordinal());
        }
        
        List<List<BlockPos>> roads = s.roadPolylines();
        buf.writeVarInt(roads.size());
        for (List<BlockPos> pl : roads) {
            buf.writeVarInt(pl.size());
            for (BlockPos p : pl) {
                buf.writeBlockPos(p);
            }
        }
    }

    public static MapSnapshot read(FriendlyByteBuf buf) {
        int sc = readCount(buf, 2048, "structures");
        List<BlockPos> structures = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            structures.add(buf.readBlockPos());
        }
        
        List<StructureInfo> infos = new ArrayList<>(sc);
        Map<BlockPos, Integer> sourceBuilder = new HashMap<>();
        for (int i = 0; i < sc; i++) {
            boolean has = buf.readBoolean();
            if (has) {
                String id = buf.readUtf(256);
                infos.add(new StructureInfo(structures.get(i), id));
            }
            sourceBuilder.put(structures.get(i), buf.readVarInt() - 1);
        }
        
        int cc = readCount(buf, 4096, "connections");
        List<StructureConnection> conns = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            int ord = buf.readVarInt();
            ConnectionStatus st = ord >= 0 && ord < STATUSES.length ? STATUSES[ord] : ConnectionStatus.FAILED;
            conns.add(new StructureConnection(a, b, st));
        }
        
        int rp = readCount(buf, 4096, "road polylines");
        List<List<BlockPos>> roads = new ArrayList<>(rp);
        int remainingRoadPoints = 262144;
        for (int i = 0; i < rp; i++) {
            int pc = readCount(buf, remainingRoadPoints, "road points");
            remainingRoadPoints -= pc;
            List<BlockPos> poly = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) {
                poly.add(buf.readBlockPos());
            }
            roads.add(poly);
        }

        return new MapSnapshot(structures, conns, infos, roads, sourceBuilder);
    }

    public static void writePatch(FriendlyByteBuf buf, MapSnapshotPatch patch) {
        if (patch == null) patch = MapSnapshotPatch.empty();

        buf.writeVarInt(patch.structures().size());
        for (StructureInfo info : patch.structures()) {
            buf.writeBlockPos(info.pos());
            String id = info.structureId();
            buf.writeBoolean(id != null);
            if (id != null) buf.writeUtf(id);
            buf.writeVarInt(patch.structureSources().getOrDefault(info.pos(), MapStructureSource.UNKNOWN.id()) + 1);
        }

        buf.writeVarInt(patch.connections().size());
        for (StructureConnection connection : patch.connections()) {
            buf.writeBlockPos(connection.from());
            buf.writeBlockPos(connection.to());
            buf.writeVarInt(connection.status().ordinal());
        }

        buf.writeVarInt(patch.roads().size());
        for (MapSnapshotPatch.RoadPolylinePatch road : patch.roads()) {
            buf.writeLong(road.key());
            buf.writeVarInt(road.points().size());
            for (BlockPos pos : road.points()) {
                buf.writeBlockPos(pos);
            }
        }

        buf.writeVarInt(patch.loadedRects().size());
        for (MapSnapshotPatch.LoadedRect loadedRect : patch.loadedRects()) {
            buf.writeUtf(loadedRect.phase().name());
            MapViewportController.RequestRect rect = loadedRect.rect();
            buf.writeVarInt(rect.minX());
            buf.writeVarInt(rect.minZ());
            buf.writeVarInt(rect.maxX());
            buf.writeVarInt(rect.maxZ());
        }
    }

    public static MapSnapshotPatch readPatch(FriendlyByteBuf buf) {
        int sc = readCount(buf, 2048, "patch structures");
        List<StructureInfo> structures = new ArrayList<>(sc);
        Map<BlockPos, Integer> structureSources = new HashMap<>();
        for (int i = 0; i < sc; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean hasId = buf.readBoolean();
            String id = hasId ? buf.readUtf(256) : "unknown";
            structures.add(new StructureInfo(pos, id));
            structureSources.put(pos, buf.readVarInt() - 1);
        }

        int cc = readCount(buf, 4096, "patch connections");
        List<StructureConnection> connections = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            BlockPos from = buf.readBlockPos();
            BlockPos to = buf.readBlockPos();
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= STATUSES.length) throw new DecoderException("invalid connection status: " + ordinal);
            ConnectionStatus status = STATUSES[ordinal];
            connections.add(new StructureConnection(from, to, status));
        }

        int rc = readCount(buf, 4096, "patch roads");
        List<MapSnapshotPatch.RoadPolylinePatch> roads = new ArrayList<>(rc);
        int remainingRoadPoints = 262144;
        for (int i = 0; i < rc; i++) {
            long key = buf.readLong();
            int pc = readCount(buf, remainingRoadPoints, "patch road points");
            remainingRoadPoints -= pc;
            ArrayList<BlockPos> points = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) {
                points.add(buf.readBlockPos());
            }
            roads.add(new MapSnapshotPatch.RoadPolylinePatch(key, points));
        }

        int lc = readCount(buf, 1024, "loaded rects");
        List<MapSnapshotPatch.LoadedRect> loadedRects = new ArrayList<>(lc);
        for (int i = 0; i < lc; i++) {
            MapLoadPhase phase = MapLoadPhase.valueOf(buf.readUtf());
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            loadedRects.add(new MapSnapshotPatch.LoadedRect(
                    phase,
                    new MapViewportController.RequestRect(minX, minZ, maxX, maxZ)));
        }

        return new MapSnapshotPatch(structures, structureSources, connections, roads, loadedRects);
    }

    private static int readCount(FriendlyByteBuf buf, int max, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException(label + " count out of range: " + count);
        }
        return count;
    }
}
