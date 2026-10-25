/* 文件职责：验证地图 patch 分片满足单包限额且不丢数据。 */
package net.shiroha233.roadweaver.map;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPatchChunkerTest {

    @Test
    void smallPatchIsSentAsIs() {
        MapSnapshotPatch patch = new MapSnapshotPatch(
                List.of(new StructureInfo(new BlockPos(1, 0, 2), "minecraft:village_plains")),
                Map.of(new BlockPos(1, 0, 2), 0),
                List.of(), List.of(), List.of());
        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);
        assertEquals(1, chunks.size());
        assertSame(patch, chunks.get(0));
    }

    @Test
    void nullPatchProducesNoChunks() {
        assertTrue(MapPatchChunker.chunk(null).isEmpty());
    }

    @Test
    void oversizeStructuresAreSplitWithoutLoss() {
        ArrayList<StructureInfo> structures = new ArrayList<>();
        Map<BlockPos, Integer> sources = new LinkedHashMap<>();
        for (int i = 0; i < MapPatchChunker.MAX_STRUCTURES * 2 + 5; i++) {
            BlockPos pos = new BlockPos(i, 0, -i);
            structures.add(new StructureInfo(pos, "minecraft:village_plains"));
            sources.put(pos, i % 3);
        }
        MapSnapshotPatch patch = new MapSnapshotPatch(structures, sources, List.of(), List.of(), List.of());

        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);

        assertEquals(3, chunks.size());
        int index = 0;
        for (MapSnapshotPatch chunk : chunks) {
            assertTrue(chunk.structures().size() <= MapPatchChunker.MAX_STRUCTURES);
            for (StructureInfo info : chunk.structures()) {
                assertEquals(index, info.pos().getX());
                assertEquals(index % 3, chunk.structureSources().get(info.pos()));
                index++;
            }
            assertTrue(chunk.roads().isEmpty());
        }
        assertEquals(structures.size(), index);
    }

    @Test
    void roadPointsAreSplitByBudgetWithoutSplittingPolylines() {
        int pointsPerPolyline = MapPatchChunker.MAX_ROAD_POINTS / 2;
        ArrayList<MapSnapshotPatch.RoadPolylinePatch> roads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            roads.add(new MapSnapshotPatch.RoadPolylinePatch(100 + i, polyline(pointsPerPolyline, i)));
        }
        MapSnapshotPatch patch = new MapSnapshotPatch(List.of(), Map.of(), List.of(), roads, List.of());

        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);

        assertEquals(4, chunks.size());
        int keyIndex = 0;
        for (MapSnapshotPatch chunk : chunks) {
            int chunkPoints = 0;
            for (MapSnapshotPatch.RoadPolylinePatch road : chunk.roads()) {
                assertEquals(100 + keyIndex, road.key());
                assertEquals(pointsPerPolyline, road.points().size());
                chunkPoints += road.points().size();
                keyIndex++;
            }
            assertTrue(chunkPoints <= MapPatchChunker.MAX_ROAD_POINTS);
        }
        assertEquals(8, keyIndex);
    }

    @Test
    void oversizePolylineOccupiesItsOwnChunkAndSmallRoadFollows() {
        ArrayList<MapSnapshotPatch.RoadPolylinePatch> roads = new ArrayList<>();
        roads.add(new MapSnapshotPatch.RoadPolylinePatch(1L,
                polyline(MapPatchChunker.MAX_ROAD_POINTS + 1, 0)));
        roads.add(new MapSnapshotPatch.RoadPolylinePatch(2L, polyline(10, 1)));
        MapSnapshotPatch patch = new MapSnapshotPatch(List.of(), Map.of(), List.of(), roads, List.of());

        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);

        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).roads().size());
        assertEquals(MapPatchChunker.MAX_ROAD_POINTS + 1, chunks.get(0).roads().get(0).points().size());
        assertEquals(1, chunks.get(1).roads().size());
        assertEquals(2L, chunks.get(1).roads().get(0).key());
    }

    @Test
    void polylineBeyondDecoderLimitIsDropped() {
        ArrayList<MapSnapshotPatch.RoadPolylinePatch> roads = new ArrayList<>();
        roads.add(new MapSnapshotPatch.RoadPolylinePatch(1L,
                polyline(MapPatchChunker.MAX_POINTS_PER_POLYLINE + 1, 0)));
        MapSnapshotPatch patch = new MapSnapshotPatch(List.of(), Map.of(), List.of(), roads, List.of());

        List<MapSnapshotPatch> chunks = MapPatchChunker.chunk(patch);

        assertTrue(chunks.isEmpty());
    }

    private static List<BlockPos> polyline(int count, int seed) {
        ArrayList<BlockPos> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new BlockPos(seed * 100_000 + i, 0, -i));
        }
        return points;
    }
}
