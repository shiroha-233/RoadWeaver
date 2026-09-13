/* 文件职责：验证精确高度采样门面的内存缓存、持久化命中与局部补采行为。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprint;
import net.shiroha233.roadweaver.persistence.files.FileBackedAccurateSampleStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccurateHeightSamplerTest {
    @Test
    void sampleChunksReturnsImmutableResultsAndEvictsLeastRecentlyUsedChunk() {
        FakeChunkBackend backend = new FakeChunkBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<Long> keys = new ArrayList<>();
        for (int chunkX = 0; chunkX <= 256; chunkX++) {
            keys.add(ChunkPos.asLong(chunkX, 0));
        }

        Map<Long, AccurateHeightChunk> first = sampler.sampleChunks(keys);
        assertEquals(1, backend.invocations.get());
        assertEquals(257, first.size());
        assertThrows(UnsupportedOperationException.class,
                () -> first.put(ChunkPos.asLong(999, 0), chunk(999, 0)));

        sampler.sampleChunks(List.of(keys.get(0)));
        assertEquals(2, backend.invocations.get(), "the first chunk should have been evicted from the 256-entry LRU");
    }

    @Test
    void concurrentRequestsForSameChunkShareOneBackendBatch() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeChunkBackend backend = new FakeChunkBackend(entered, release);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        long key = ChunkPos.asLong(-7, 11);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<Long, AccurateHeightChunk>> first = executor.submit(() -> sampler.sampleChunks(List.of(key)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<Map<Long, AccurateHeightChunk>> second = executor.submit(() -> sampler.sampleChunks(List.of(key)));
            release.countDown();

            assertEquals(key, first.get(5, TimeUnit.SECONDS).get(key).chunkKey());
            assertEquals(key, second.get(5, TimeUnit.SECONDS).get(key).chunkKey());
            assertEquals(1, backend.invocations.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void samplePathHeightsAlwaysReplacesCoarsePathY() {
        FakeChunkBackend backend = new FakeChunkBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);

        List<BlockPos> sampled = sampler.samplePathHeights(List.of(
                new BlockPos(1, -40, 1),
                new BlockPos(17, -20, 1)), 0);

        assertEquals(List.of(
                new BlockPos(1, 96, 1),
                new BlockPos(17, 96, 1)), sampled);
        assertEquals(1, backend.invocations.get());
    }

    @Test
    void samplePositionsUsesSparseBackendAndCachesExactColumns() {
        SparseBackend backend = new SparseBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<BlockPos> positions = List.of(new BlockPos(1, 0, 1), new BlockPos(9, 0, 3));

        Map<Long, AccurateHeightSample> first = sampler.samplePositions(positions);
        Map<Long, AccurateHeightSample> second = sampler.samplePositions(positions);

        assertEquals(2, first.size());
        assertEquals(96, first.get(AccurateHeightSample.key(1, 1)).worldSurfaceWg());
        assertEquals(first, second);
        assertEquals(1, backend.sparseInvocations.get());
        assertEquals(0, backend.chunkInvocations.get());
    }

    @Test
    void samplePositionsWithProgressSharePersistentColumnCache() {
        SparseBackend backend = new SparseBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<BlockPos> positions = List.of(new BlockPos(1, 0, 1), new BlockPos(9, 0, 3));

        Map<Long, AccurateHeightSample> first = sampler.samplePositions(positions, AccurateSamplingProgress.NONE);
        Map<Long, AccurateHeightSample> second = sampler.samplePositions(positions, AccurateSamplingProgress.NONE);

        assertEquals(first, second);
        assertEquals(1, backend.sparseInvocations.get());
        assertThrows(UnsupportedOperationException.class,
                () -> first.put(AccurateHeightSample.key(3, 3), new AccurateHeightSample(1, 1, 1)));
    }

    @Test
    void allMissGridSamplingStillUsesBackendGridPort() {
        HybridGridBackend backend = new HybridGridBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(-16, 24, 3, 2, 8);
        AtomicInteger completed = new AtomicInteger();

        AccurateHeightGrid grid = sampler.sampleGrid(
                request, batch -> completed.set(Math.toIntExact(batch.completedColumns())));

        assertEquals(1, backend.gridInvocations.get());
        assertEquals(0, backend.sparseInvocations.get());
        assertEquals(request.sampleCount(), completed.get());
        assertEquals(-16, grid.worldSurface()[0]);
        assertEquals(32, grid.oceanFloor()[request.sampleCount() - 1]);
        assertEquals(32, grid.motionBlocking()[request.sampleCount() - 1]);
    }

    @Test
    void persistedColumnsAreReusedByFreshSamplerInstance(@TempDir Path tempDir) {
        WorldgenFingerprint fingerprint = new WorldgenFingerprint("accurate-height-sampler-cross-instance", 1, 1);
        List<BlockPos> positions = List.of(new BlockPos(1, 0, 1), new BlockPos(9, 0, 3));

        SparseBackend firstBackend = new SparseBackend(96);
        AccurateHeightSampler first = new AccurateHeightSampler(
                null,
                firstBackend,
                new FileBackedAccurateSampleStore(tempDir.resolve("accurate"), fingerprint));
        Map<Long, AccurateHeightSample> firstResult = first.samplePositions(positions);

        SparseBackend secondBackend = new SparseBackend(120);
        AccurateHeightSampler second = new AccurateHeightSampler(
                null,
                secondBackend,
                new FileBackedAccurateSampleStore(tempDir.resolve("accurate"), fingerprint));
        Map<Long, AccurateHeightSample> secondResult = second.samplePositions(positions);

        assertEquals(firstResult, secondResult);
        assertEquals(1, firstBackend.sparseInvocations.get());
        assertEquals(0, secondBackend.sparseInvocations.get());
    }

    @Test
    void partialHitGridSamplingUsesSparseBackendAndStore(@TempDir Path tempDir) {
        WorldgenFingerprint fingerprint = new WorldgenFingerprint("accurate-height-grid-partial-hit", 1, 1);
        Path root = tempDir.resolve("accurate");

        SparseBackend warmupBackend = new SparseBackend(90);
        AccurateHeightSampler warmup = new AccurateHeightSampler(
                null,
                warmupBackend,
                new FileBackedAccurateSampleStore(root, fingerprint));
        warmup.samplePositions(List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(8, 0, 0)));

        HybridGridBackend backend = new HybridGridBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(
                null,
                backend,
                new FileBackedAccurateSampleStore(root, fingerprint));
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(0, 0, 2, 2, 8);

        AccurateHeightGrid grid = sampler.sampleGrid(request, AccurateSamplingProgress.NONE);

        assertEquals(0, backend.gridInvocations.get());
        assertEquals(1, backend.sparseInvocations.get());
        assertEquals(90, grid.worldSurface()[0]);
        assertEquals(90, grid.worldSurface()[1]);
        assertEquals(208, grid.worldSurface()[2]);
        assertEquals(216, grid.motionBlocking()[3]);
    }

    @Test
    void acceleratedGridNeverUsesStandardBackendPath() {
        AcceleratedOnlyBackend backend = new AcceleratedOnlyBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(0, 0, 2, 2, 8);

        AccurateHeightGrid grid = sampler.sampleAcceleratedGrid(request, AccurateSamplingProgress.NONE);

        assertEquals(1, backend.acceleratedGridInvocations.get());
        assertEquals(0, backend.acceleratedPositionInvocations.get());
        assertEquals(300, grid.worldSurface()[0]);
    }

    @Test
    void acceleratedPartialGridOnlySubmitsMissingStoredColumns(@TempDir Path tempDir) {
        WorldgenFingerprint fingerprint = new WorldgenFingerprint("accelerated-grid-partial", 1, 1);
        Path root = tempDir.resolve("accurate");
        FileBackedAccurateSampleStore store = new FileBackedAccurateSampleStore(root, fingerprint);
        store.saveSamples(Map.of(
                AccurateHeightSample.key(0, 0),
                new AccurateHeightSample(90, 80, 91)));
        AcceleratedOnlyBackend backend = new AcceleratedOnlyBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend, store);

        AccurateHeightGrid grid = sampler.sampleAcceleratedGrid(
                new AccurateHeightGridRequest(0, 0, 2, 1, 8),
                AccurateSamplingProgress.NONE);

        assertEquals(0, backend.acceleratedGridInvocations.get());
        assertEquals(1, backend.acceleratedPositionInvocations.get());
        assertEquals(90, grid.worldSurface()[0]);
        assertEquals(308, grid.worldSurface()[1]);
    }

    @Test
    void overlappingConcurrentGridsOnlySubmitNewColumnsAfterTileWait() throws Exception {
        CountDownLatch gridEntered = new CountDownLatch(1);
        CountDownLatch releaseGrid = new CountDownLatch(1);
        OverlappingGridBackend backend = new OverlappingGridBackend(gridEntered, releaseGrid);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AccurateHeightGrid> first = executor.submit(() -> sampler.sampleGrid(
                    new AccurateHeightGridRequest(0, 0, 2, 1, 8)));
            assertTrue(gridEntered.await(5, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<AccurateHeightGrid> second = executor.submit(() -> {
                secondStarted.countDown();
                return sampler.sampleGrid(new AccurateHeightGridRequest(8, 0, 2, 1, 8));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(50L);
            releaseGrid.countDown();

            first.get(5, TimeUnit.SECONDS);
            AccurateHeightGrid secondGrid = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, backend.gridInvocations.get());
            assertEquals(1, backend.sparseInvocations.get());
            assertEquals(1, backend.sparseColumns.get());
            assertEquals(308, secondGrid.worldSurface()[0]);
            assertEquals(316, secondGrid.worldSurface()[1]);
        } finally {
            releaseGrid.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void storedGridRestoreNeverSamplesMissingColumns(@TempDir Path tempDir) {
        WorldgenFingerprint fingerprint = new WorldgenFingerprint("stored-grid-only", 1, 1);
        FileBackedAccurateSampleStore store = new FileBackedAccurateSampleStore(
                tempDir.resolve("accurate"), fingerprint);
        store.saveSamples(Map.of(
                AccurateHeightSample.key(0, 0),
                new AccurateHeightSample(70, 60, 71)));
        AcceleratedOnlyBackend backend = new AcceleratedOnlyBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend, store);
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(0, 0, 2, 1, 8);

        assertTrue(sampler.loadStoredGrid(request).isEmpty());
        store.saveSamples(Map.of(
                AccurateHeightSample.key(8, 0),
                new AccurateHeightSample(80, 65, 81)));

        AccurateHeightGrid restored = sampler.loadStoredGrid(request).orElseThrow();
        assertEquals(70, restored.worldSurface()[0]);
        assertEquals(80, restored.worldSurface()[1]);
        assertEquals(0, backend.acceleratedGridInvocations.get());
        assertEquals(0, backend.acceleratedPositionInvocations.get());
    }

    private static AccurateHeightChunk chunk(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ, chunkX + chunkZ);
    }

    private static AccurateHeightChunk chunk(int chunkX, int chunkZ, int height) {
        int[] values = new int[AccurateHeightChunk.COLUMN_COUNT];
        Arrays.fill(values, height);
        return new AccurateHeightChunk(chunkX, chunkZ, values.clone(), values.clone(), values.clone());
    }

    private static final class FakeChunkBackend implements AccurateHeightBackend {
        private final AtomicInteger invocations = new AtomicInteger();
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final Integer fixedHeight;

        private FakeChunkBackend() {
            this(null, null, null);
        }

        private FakeChunkBackend(int fixedHeight) {
            this(null, null, fixedHeight);
        }

        private FakeChunkBackend(CountDownLatch entered, CountDownLatch release) {
            this(entered, release, null);
        }

        private FakeChunkBackend(CountDownLatch entered, CountDownLatch release, Integer fixedHeight) {
            this.entered = entered;
            this.release = release;
            this.fixedHeight = fixedHeight;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            invocations.incrementAndGet();
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
            for (long key : chunkKeys) {
                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                result.put(key, fixedHeight == null
                        ? chunk(chunkX, chunkZ)
                        : chunk(chunkX, chunkZ, fixedHeight));
            }
            return result;
        }

        @Override
        public String backendName() {
            return "TEST";
        }
    }

    private static final class SparseBackend implements AccurateHeightBackend {
        private final int height;
        private final AtomicInteger chunkInvocations = new AtomicInteger();
        private final AtomicInteger sparseInvocations = new AtomicInteger();

        private SparseBackend(int height) {
            this.height = height;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            chunkInvocations.incrementAndGet();
            return Map.of();
        }

        @Override
        public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
            sparseInvocations.incrementAndGet();
            LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
            for (BlockPos position : positions) {
                samples.put(AccurateHeightSample.key(position.getX(), position.getZ()),
                        new AccurateHeightSample(height, height, height));
            }
            return samples;
        }

        @Override
        public String backendName() {
            return "SPARSE_TEST";
        }
    }

    private static final class HybridGridBackend implements AccurateHeightBackend {
        private final AtomicInteger gridInvocations = new AtomicInteger();
        private final AtomicInteger sparseInvocations = new AtomicInteger();

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            throw new AssertionError("chunk sampling must not be used by these grid tests");
        }

        @Override
        public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions,
                                                               AccurateSamplingProgress progress) {
            sparseInvocations.incrementAndGet();
            LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
            for (BlockPos position : positions) {
                int worldSurface = 200 + position.getX() + position.getZ();
                int oceanFloor = 100 + position.getZ();
                int motionBlocking = worldSurface;
                samples.put(
                        AccurateHeightSample.key(position.getX(), position.getZ()),
                        new AccurateHeightSample(worldSurface, oceanFloor, motionBlocking));
            }
            progress.onBatch(new AccurateSamplingProgress.Batch(
                    positions.size(), positions.size(), positions.size(), 1L, 1L, 0L));
            return samples;
        }

        @Override
        public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                             AccurateSamplingProgress progress) {
            gridInvocations.incrementAndGet();
            int[] worldSurface = new int[request.sampleCount()];
            int[] oceanFloor = new int[request.sampleCount()];
            int[] motionBlocking = new int[request.sampleCount()];
            for (int index = 0; index < request.sampleCount(); index++) {
                worldSurface[index] = request.blockX(index);
                oceanFloor[index] = request.blockZ(index);
                motionBlocking[index] = request.blockX(index) + request.blockZ(index);
            }
            progress.onBatch(new AccurateSamplingProgress.Batch(
                    request.sampleCount(), request.sampleCount(), request.sampleCount(), 1L, 1L, 0L));
            return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
        }

        @Override
        public String backendName() {
            return "GRID_TEST";
        }
    }

    private static final class AcceleratedOnlyBackend implements AccurateHeightBackend {
        private final AtomicInteger acceleratedGridInvocations = new AtomicInteger();
        private final AtomicInteger acceleratedPositionInvocations = new AtomicInteger();

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            throw new AssertionError("standard chunk sampling is forbidden");
        }

        @Override
        public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions,
                                                               AccurateSamplingProgress progress) {
            throw new AssertionError("standard position sampling is forbidden");
        }

        @Override
        public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                             AccurateSamplingProgress progress) {
            throw new AssertionError("standard grid sampling is forbidden");
        }

        @Override
        public boolean supportsAcceleratedSampling() {
            return true;
        }

        @Override
        public Map<Long, AccurateHeightSample> sampleAcceleratedPositions(
                Collection<BlockPos> positions,
                AccurateSamplingProgress progress) {
            acceleratedPositionInvocations.incrementAndGet();
            LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
            for (BlockPos position : positions) {
                int height = 300 + position.getX() + position.getZ();
                samples.put(AccurateHeightSample.key(position.getX(), position.getZ()),
                        new AccurateHeightSample(height, height - 10, height));
            }
            return samples;
        }

        @Override
        public AccurateHeightGrid sampleAcceleratedGrid(AccurateHeightGridRequest request,
                                                        AccurateSamplingProgress progress) {
            acceleratedGridInvocations.incrementAndGet();
            int[] worldSurface = new int[request.sampleCount()];
            int[] oceanFloor = new int[request.sampleCount()];
            int[] motionBlocking = new int[request.sampleCount()];
            for (int index = 0; index < request.sampleCount(); index++) {
                int height = 300 + request.blockX(index) + request.blockZ(index);
                worldSurface[index] = height;
                oceanFloor[index] = height - 10;
                motionBlocking[index] = height;
            }
            return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
        }

        @Override
        public String backendName() {
            return "ACCELERATED_TEST";
        }
    }

    private static final class OverlappingGridBackend implements AccurateHeightBackend {
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicInteger gridInvocations = new AtomicInteger();
        private final AtomicInteger sparseInvocations = new AtomicInteger();
        private final AtomicInteger sparseColumns = new AtomicInteger();

        private OverlappingGridBackend(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            throw new AssertionError("chunk sampling is not expected");
        }

        @Override
        public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions,
                                                               AccurateSamplingProgress progress) {
            sparseInvocations.incrementAndGet();
            sparseColumns.addAndGet(positions.size());
            LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>();
            for (BlockPos position : positions) {
                int height = 300 + position.getX() + position.getZ();
                result.put(AccurateHeightSample.key(position.getX(), position.getZ()),
                        new AccurateHeightSample(height, height - 10, height));
            }
            return result;
        }

        @Override
        public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                             AccurateSamplingProgress progress) {
            gridInvocations.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(interrupted);
            }
            int[] worldSurface = new int[request.sampleCount()];
            int[] oceanFloor = new int[request.sampleCount()];
            int[] motionBlocking = new int[request.sampleCount()];
            for (int index = 0; index < request.sampleCount(); index++) {
                int height = 300 + request.blockX(index) + request.blockZ(index);
                worldSurface[index] = height;
                oceanFloor[index] = height - 10;
                motionBlocking[index] = height;
            }
            return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
        }

        @Override
        public String backendName() {
            return "OVERLAP_TEST";
        }
    }
}
