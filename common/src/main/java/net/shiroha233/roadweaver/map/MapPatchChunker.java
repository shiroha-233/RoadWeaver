/* 文件职责：把超出单包限额的地图 patch 拆分为客户端可解码的多个分片。 */
package net.shiroha233.roadweaver.map;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图 patch 分片器。
 *
 * <p>分片限额必须落在 {@link net.shiroha233.roadweaver.network.MapSnapshotCodec}#readPatch
 * 的解码限额内，并给原版 1MB 客户端自定义包体积留出余量；超限的包会被客户端解码器直接
 * 拒绝并断开连接。分片按序发送，客户端按序增量合并，最终结果与单包一致。</p>
 */
public final class MapPatchChunker {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    static final int MAX_STRUCTURES = 1024;
    static final int MAX_CONNECTIONS = 2048;
    static final int MAX_ROADS = 2048;
    static final int MAX_ROAD_POINTS = 32768;
    static final int MAX_LOADED_RECTS = 1024;
    /** 单条折线的传输上限，与 readPatch 的总点数预算一致。 */
    static final int MAX_POINTS_PER_POLYLINE = 262144;

    private MapPatchChunker() {}

    public static List<MapSnapshotPatch> chunk(MapSnapshotPatch patch) {
        if (patch == null) return List.of();
        if (fitsInOnePacket(patch)) return List.of(patch);

        List<StructureInfo> structures = patch.structures();
        List<StructureConnection> connections = patch.connections();
        List<MapSnapshotPatch.RoadPolylinePatch> roads = patch.roads();
        List<MapSnapshotPatch.LoadedRect> loadedRects = patch.loadedRects();

        ArrayList<MapSnapshotPatch> chunks = new ArrayList<>();
        int structureIndex = 0;
        int connectionIndex = 0;
        int roadIndex = 0;
        int rectIndex = 0;
        while (structureIndex < structures.size()
                || connectionIndex < connections.size()
                || roadIndex < roads.size()
                || rectIndex < loadedRects.size()) {
            int structureEnd = Math.min(structures.size(), structureIndex + MAX_STRUCTURES);
            int connectionEnd = Math.min(connections.size(), connectionIndex + MAX_CONNECTIONS);
            int rectEnd = Math.min(loadedRects.size(), rectIndex + MAX_LOADED_RECTS);

            ArrayList<MapSnapshotPatch.RoadPolylinePatch> roadChunk = new ArrayList<>();
            int points = 0;
            while (roadIndex < roads.size()
                    && roadChunk.size() < MAX_ROADS
                    && (points == 0 || points + roads.get(roadIndex).points().size() <= MAX_ROAD_POINTS)) {
                List<BlockPos> polyline = roads.get(roadIndex).points();
                if (polyline.size() > MAX_POINTS_PER_POLYLINE) {
                    LOGGER.warn("地图 patch 丢弃超长道路折线: {} 个点", polyline.size());
                    roadIndex++;
                    continue;
                }
                roadChunk.add(roads.get(roadIndex));
                points += polyline.size();
                roadIndex++;
            }

            MapSnapshotPatch chunk = new MapSnapshotPatch(
                    structures.subList(structureIndex, structureEnd),
                    selectSources(patch.structureSources(), structures.subList(structureIndex, structureEnd)),
                    connections.subList(connectionIndex, connectionEnd),
                    roadChunk,
                    loadedRects.subList(rectIndex, rectEnd));
            if (!chunk.isEmpty()) chunks.add(chunk);
            structureIndex = structureEnd;
            connectionIndex = connectionEnd;
            rectIndex = rectEnd;
        }
        return chunks;
    }

    private static boolean fitsInOnePacket(MapSnapshotPatch patch) {
        return patch.structures().size() <= MAX_STRUCTURES
                && patch.connections().size() <= MAX_CONNECTIONS
                && patch.roads().size() <= MAX_ROADS
                && patch.loadedRects().size() <= MAX_LOADED_RECTS
                && totalRoadPoints(patch.roads()) <= MAX_ROAD_POINTS;
    }

    private static int totalRoadPoints(List<MapSnapshotPatch.RoadPolylinePatch> roads) {
        int total = 0;
        for (MapSnapshotPatch.RoadPolylinePatch road : roads) {
            total += road.points().size();
        }
        return total;
    }

    private static Map<BlockPos, Integer> selectSources(Map<BlockPos, Integer> sources,
                                                        List<StructureInfo> structures) {
        if (structures.isEmpty() || sources.isEmpty()) return Map.of();
        HashMap<BlockPos, Integer> selected = new HashMap<>();
        for (StructureInfo info : structures) {
            Integer source = sources.get(info.pos());
            if (source != null) selected.put(info.pos(), source);
        }
        return selected;
    }
}
