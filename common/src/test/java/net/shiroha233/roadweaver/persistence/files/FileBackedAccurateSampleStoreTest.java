/* 文件职责：验证精确样本文件存储的稀疏回写、合并恢复、损坏隔离与命名空间清理。 */
package net.shiroha233.roadweaver.persistence.files;

import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightChunk;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightSample;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBackedAccurateSampleStoreTest {
    @Test
    void sparseSamplesRoundTripAcrossTiles(@TempDir Path tempDir) {
        FileBackedAccurateSampleStore store = new FileBackedAccurateSampleStore(
                tempDir.resolve("accurate"),
                new WorldgenFingerprint("sparse-roundtrip", 1, 1));
        Map<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
        samples.put(AccurateHeightSample.key(-1, 2), new AccurateHeightSample(80, 64, 90));
        samples.put(AccurateHeightSample.key(260, 513), new AccurateHeightSample(120, 118, 124));

        store.saveSamples(samples);

        assertEquals(samples, store.loadSamples(samples.keySet()));
        assertTrue(Files.exists(store.tilePath(-1, 0)));
        assertTrue(Files.exists(store.tilePath(1, 2)));
    }

    @Test
    void mergedSparseWritesRecoverCompleteChunk(@TempDir Path tempDir) {
        FileBackedAccurateSampleStore store = new FileBackedAccurateSampleStore(
                tempDir.resolve("accurate"),
                new WorldgenFingerprint("sparse-merge", 1, 1));
        AccurateHeightChunk expected = chunk(2, -3);
        long chunkKey = expected.chunkKey();

        store.saveSamples(samplesFromChunk(expected, 0, 128));
        assertTrue(store.loadChunks(List.of(chunkKey)).isEmpty());

        store.saveSamples(samplesFromChunk(expected, 128, AccurateHeightChunk.COLUMN_COUNT));
        AccurateHeightChunk recovered = store.loadChunks(List.of(chunkKey)).get(chunkKey);

        assertNotNull(recovered);
        for (int index = 0; index < AccurateHeightChunk.COLUMN_COUNT; index++) {
            assertEquals(expected.worldSurfaceWgAt(index), recovered.worldSurfaceWgAt(index));
            assertEquals(expected.oceanFloorWgAt(index), recovered.oceanFloorWgAt(index));
            assertEquals(expected.motionBlockingNoLeavesAt(index), recovered.motionBlockingNoLeavesAt(index));
        }
    }

    @Test
    void corruptTileIsQuarantinedAndIgnored(@TempDir Path tempDir) throws IOException {
        WorldgenFingerprint fingerprint = new WorldgenFingerprint("corrupt-tile", 1, 1);
        Path root = tempDir.resolve("accurate");
        FileBackedAccurateSampleStore initial = new FileBackedAccurateSampleStore(root, fingerprint);
        long sampleKey = AccurateHeightSample.key(1, 1);
        initial.saveSamples(Map.of(sampleKey, new AccurateHeightSample(70, 60, 71)));
        Path tilePath = initial.tilePath(0, 0);
        initial.close();

        Files.write(tilePath, new byte[] {1, 2, 3, 4});

        FileBackedAccurateSampleStore reloaded = new FileBackedAccurateSampleStore(root, fingerprint);
        assertTrue(reloaded.loadSamples(List.of(sampleKey)).isEmpty());
        assertFalse(Files.exists(tilePath));
        assertTrue(hasCorruptSibling(tilePath));
    }

    @Test
    void creatingNewFingerprintNamespaceClearsStaleNamespaces(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("accurate");
        Files.createDirectories(root.resolve("stale-a").resolve("nested"));
        Files.createDirectories(root.resolve("stale-b"));
        Files.writeString(root.resolve("stale-a").resolve("nested").resolve("marker.txt"), "stale");

        FileBackedAccurateSampleStore store = new FileBackedAccurateSampleStore(
                root,
                new WorldgenFingerprint("active-fingerprint", 1, 1));

        assertTrue(Files.isDirectory(store.namespaceRoot()));
        assertFalse(Files.exists(root.resolve("stale-a")));
        assertFalse(Files.exists(root.resolve("stale-b")));
    }

    private static boolean hasCorruptSibling(Path tilePath) throws IOException {
        Path parent = tilePath.getParent();
        String prefix = tilePath.getFileName() + ".corrupt.";
        try (var stream = Files.list(parent)) {
            return stream.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }

    private static Map<Long, AccurateHeightSample> samplesFromChunk(AccurateHeightChunk chunk,
                                                                    int startInclusive,
                                                                    int endExclusive) {
        LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
        int baseX = chunk.chunkX() << 4;
        int baseZ = chunk.chunkZ() << 4;
        for (int index = startInclusive; index < endExclusive; index++) {
            int blockX = baseX + (index & 15);
            int blockZ = baseZ + (index >> 4);
            samples.put(
                    AccurateHeightSample.key(blockX, blockZ),
                    new AccurateHeightSample(
                            chunk.worldSurfaceWgAt(index),
                            chunk.oceanFloorWgAt(index),
                            chunk.motionBlockingNoLeavesAt(index)));
        }
        return samples;
    }

    private static AccurateHeightChunk chunk(int chunkX, int chunkZ) {
        int[] worldSurface = new int[AccurateHeightChunk.COLUMN_COUNT];
        int[] oceanFloor = new int[AccurateHeightChunk.COLUMN_COUNT];
        int[] motionBlocking = new int[AccurateHeightChunk.COLUMN_COUNT];
        for (int index = 0; index < AccurateHeightChunk.COLUMN_COUNT; index++) {
            worldSurface[index] = 100 + index;
            oceanFloor[index] = -20 + index;
            motionBlocking[index] = 200 + index;
        }
        return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
    }
}
