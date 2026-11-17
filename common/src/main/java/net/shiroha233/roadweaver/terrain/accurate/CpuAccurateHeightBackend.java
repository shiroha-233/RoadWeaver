/* 文件职责：通过原版 ChunkGenerator 的基准高度接口生成 CPU 精确高度图。 */
package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.shiroha233.roadweaver.core.metrics.AccurateSamplingStats;

/**
 * 精确采样 CPU 实现。
 *
 * <p>26.3 把噪声分块插值整体下沉进 {@code densityfunction} 包（{@code NoiseChunk} 已退化为
 * 采样上下文持有者），因此这里不再复刻原版插值流程，而是直接使用
 * {@link ChunkGenerator#getBaseHeight} 取得与地形生成一致的基准高度。</p>
 */
public final class CpuAccurateHeightBackend implements AccurateHeightBackend {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;

    private CpuAccurateHeightBackend(ServerLevel level,
                                     ChunkGenerator generator,
                                     RandomState randomState) {
        this.level = level;
        this.generator = generator;
        this.randomState = randomState;
    }

    public static CpuAccurateHeightBackend create(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new CpuAccurateHeightBackend(
                level, generator, level.getChunkSource().getGeneratorState().randomState());
    }

    @Override
    public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return result;
        }
        long startedAt = System.nanoTime();
        for (long key : chunkKeys) {
            result.put(key, sampleChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
        }
        AccurateSamplingStats.recordCpuBatch(result.size(), System.nanoTime() - startedAt);
        return result;
    }

    @Override
    public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Long, BlockPos> unique = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            if (position != null) {
                unique.putIfAbsent(AccurateHeightSample.key(position.getX(), position.getZ()), position);
            }
        }
        if (unique.isEmpty()) {
            return Map.of();
        }

        long startedAt = System.nanoTime();
        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>(unique.size());
        for (Map.Entry<Long, BlockPos> entry : unique.entrySet()) {
            BlockPos position = entry.getValue();
            result.put(entry.getKey(), sampleBaseHeights(position.getX(), position.getZ()));
        }
        AccurateSamplingStats.recordCpuBatch(result.size(), System.nanoTime() - startedAt);
        return result;
    }

    @Override
    public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                         AccurateSamplingProgress progress) {
        long startedAt = System.nanoTime();
        int sampleCount = request.sampleCount();
        int[] worldSurface = new int[sampleCount];
        int[] oceanFloor = new int[sampleCount];
        int[] motionBlocking = new int[sampleCount];
        for (int index = 0; index < sampleCount; index++) {
            AccurateHeightSample sample = sampleBaseHeights(request.blockX(index), request.blockZ(index));
            worldSurface[index] = sample.worldSurfaceWg();
            oceanFloor[index] = sample.oceanFloorWg();
            motionBlocking[index] = sample.motionBlockingNoLeaves();
        }

        long elapsedNanos = System.nanoTime() - startedAt;
        AccurateSamplingStats.recordCpuBatch(sampleCount, elapsedNanos);
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        sink.onBatch(new AccurateSamplingProgress.Batch(
                sampleCount, sampleCount, sampleCount, elapsedNanos, elapsedNanos, 0L));
        return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
    }

    private AccurateHeightChunk sampleChunk(int chunkX, int chunkZ) {
        int[] worldSurface = new int[AccurateHeightChunk.COLUMN_COUNT];
        int[] oceanFloor = new int[AccurateHeightChunk.COLUMN_COUNT];
        int[] motionBlocking = new int[AccurateHeightChunk.COLUMN_COUNT];
        int minBlockX = chunkX << 4;
        int minBlockZ = chunkZ << 4;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int index = localX + (localZ << 4);
                int x = minBlockX + localX;
                int z = minBlockZ + localZ;
                worldSurface[index] = baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG);
                oceanFloor[index] = baseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG);
                motionBlocking[index] = baseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
            }
        }
        return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
    }

    private AccurateHeightSample sampleBaseHeights(int x, int z) {
        return new AccurateHeightSample(
                baseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG),
                baseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG),
                baseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
    }

    private int baseHeight(int x, int z, Heightmap.Types type) {
        return generator.getBaseHeight(x, z, type, level, randomState);
    }

    @Override
    public String backendName() {
        return "CPU";
    }

    @Override
    public void close() {
    }
}
