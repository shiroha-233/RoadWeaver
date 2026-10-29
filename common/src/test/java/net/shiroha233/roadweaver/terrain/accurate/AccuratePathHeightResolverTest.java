/* 文件职责：验证最终道路高度只复用精采地形场，粗地形覆盖不能绕过精采后端。 */
package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.terrain.AccuratePathHeightResolver;
import net.shiroha233.roadweaver.terrain.PathTerrainField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccuratePathHeightResolverTest {
    @Test
    void coarseTerrainCoverageCannotOverrideAccurateBackendHeight() {
        FixedBackend backend = new FixedBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        PathTerrainField coarse = new FixedTerrainField(-20, false);

        List<BlockPos> resolved = AccuratePathHeightResolver.resolve(
                List.of(new BlockPos(1, -40, 1)), coarse, sampler);

        assertEquals(List.of(new BlockPos(1, 96, 1)), resolved);
        assertEquals(1, backend.invocations.get());
    }

    @Test
    void accurateTerrainSampleIsReusedWithoutAnotherBackendBatch() {
        FixedBackend backend = new FixedBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        PathTerrainField accurate = new FixedTerrainField(88, true);

        List<BlockPos> resolved = AccuratePathHeightResolver.resolve(
                List.of(new BlockPos(1, -40, 1)), accurate, sampler);

        assertEquals(List.of(new BlockPos(1, 88, 1)), resolved);
        assertEquals(0, backend.invocations.get());
    }

    private static final class FixedBackend implements AccurateHeightBackend {
        private final int height;
        private final AtomicInteger invocations = new AtomicInteger();

        private FixedBackend(int height) {
            this.height = height;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            invocations.incrementAndGet();
            LinkedHashMap<Long, AccurateHeightChunk> chunks = new LinkedHashMap<>();
            for (long key : chunkKeys) {
                int[] values = new int[AccurateHeightChunk.COLUMN_COUNT];
                Arrays.fill(values, height);
                chunks.put(key, new AccurateHeightChunk(
                        ChunkPos.getX(key),
                        ChunkPos.getZ(key),
                        values.clone(),
                        values.clone(),
                        values.clone()));
            }
            return chunks;
        }

        @Override
        public String backendName() {
            return "TEST";
        }
    }

    private record FixedTerrainField(int height, boolean accurate) implements PathTerrainField {
        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public int height(int x, int z) {
            return height;
        }

        @Override
        public int oceanFloor(int x, int z) {
            return height;
        }

        @Override
        public boolean isColumnWater(int x, int z) {
            return false;
        }

        @Override
        public boolean isNearWater(int x, int z, int neighborDistance) {
            return false;
        }

        @Override
        public Holder<Biome> biome(int x, int z) {
            return null;
        }

        @Override
        public boolean contains(int x, int z) {
            return true;
        }

        @Override
        public int step() {
            return 8;
        }

        @Override
        public boolean hasAccurateSample(int x, int z) {
            return accurate;
        }
    }
}
