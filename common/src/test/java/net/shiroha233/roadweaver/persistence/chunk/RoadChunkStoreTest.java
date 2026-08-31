/* 文件职责：验证道路按 ChunkPos 分片持久化及重启后的无磁盘查询快照。 */
package net.shiroha233.roadweaver.persistence.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.persistence.RoadReplacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadChunkStoreTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsNegativeAndPositiveChunkShardsAndReloadsSnapshot() {
        RoadData road = crossingRoad();
        long fingerprint = RoadFingerprint.compute(road);
        RoadChunkStore first = new RoadChunkStore(temporaryDirectory);

        first.addRoad(road);

        assertEquals(1, first.queryChunk(-1, 0).size());
        assertEquals(1, first.queryChunk(0, 0).size());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("chunks/-1_0.json")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("chunks/0_0.json")));

        RoadChunkStore reloaded;
        first.flush();
        reloaded = new RoadChunkStore(temporaryDirectory);
        assertNotNull(reloaded.loadByFingerprint(fingerprint));
        assertEquals(1, reloaded.queryRect(-4, 8, 4, 8).size());

        reloaded.deleteRoad(fingerprint);
        assertFalse(reloaded.hasAnyRoad());
        reloaded.flush();
        assertFalse(Files.exists(temporaryDirectory.resolve("chunks/-1_0.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("chunks/0_0.json")));
    }

    @Test
    void replacesMultipleRoadsWithOneBatchAndReloadsUpdatedSnapshot() {
        RoadData firstRoad = crossingRoad();
        RoadData secondRoad = parallelRoad(24, 303L, 404L);
        long firstFingerprint = RoadFingerprint.compute(firstRoad);
        long secondFingerprint = RoadFingerprint.compute(secondRoad);
        RoadChunkStore store = new RoadChunkStore(temporaryDirectory);
        store.addRoad(firstRoad);
        store.addRoad(secondRoad);

        RoadData firstReplacement = parallelRoad(16, 101L, 202L);
        RoadData secondReplacement = parallelRoad(32, 303L, 404L);
        store.replaceRoads(List.of(
                new RoadReplacement(firstFingerprint, firstReplacement),
                new RoadReplacement(secondFingerprint, secondReplacement)));

        assertEquals(firstReplacement, store.loadByFingerprint(RoadFingerprint.compute(firstReplacement)));
        assertEquals(secondReplacement, store.loadByFingerprint(RoadFingerprint.compute(secondReplacement)));
        store.flush();
        Path roadDirectory = temporaryDirectory.resolve("roads");
        assertTrue(Files.isRegularFile(roadDirectory.resolve(roadFileName(firstFingerprint, 3L))));
        assertTrue(Files.isRegularFile(roadDirectory.resolve(roadFileName(secondFingerprint, 3L))));
        assertFalse(Files.exists(roadDirectory.resolve(roadFileName(firstFingerprint, 1L))));
        assertFalse(Files.exists(roadDirectory.resolve(roadFileName(secondFingerprint, 2L))));

        RoadChunkStore reloaded = new RoadChunkStore(temporaryDirectory);
        assertEquals(firstReplacement, reloaded.loadByFingerprint(RoadFingerprint.compute(firstReplacement)));
        assertEquals(secondReplacement, reloaded.loadByFingerprint(RoadFingerprint.compute(secondReplacement)));
    }

    private static String roadFileName(long fingerprint, long generation) {
        return Long.toUnsignedString(fingerprint) + ".g" + Long.toUnsignedString(generation) + ".nbt";
    }

    private static RoadData crossingRoad() {
        return parallelRoad(8, 101L, 202L);
    }

    private static RoadData parallelRoad(int z, long ownerA, long ownerB) {
        List<RoadSegmentPlacement> segments = new ArrayList<>();
        List<Integer> targetY = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            BlockPos center = new BlockPos(x, 64, z);
            segments.add(new RoadSegmentPlacement(center, List.of(center)));
            targetY.add(64);
        }
        return new RoadData(3, 0, List.of(), List.of(), segments, List.of(), targetY, ownerA, ownerB);
    }
}
