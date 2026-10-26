/* 文件职责：通过原版 NoiseChunk 或 ChunkGenerator 生成 CPU 精确高度图。 */
package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 精确采样 CPU 基准实现，也是 OpenCL 的正确性回退源。
 */
public final class CpuAccurateHeightBackend implements AccurateHeightBackend {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final NoiseChunkHeightSampler noiseChunkSampler;

    private CpuAccurateHeightBackend(ServerLevel level,
                                     ChunkGenerator generator,
                                     RandomState randomState,
                                     NoiseChunkHeightSampler noiseChunkSampler) {
        this.level = level;
        this.generator = generator;
        this.randomState = randomState;
        this.noiseChunkSampler = noiseChunkSampler;
    }

    public static CpuAccurateHeightBackend create(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        NoiseChunkHeightSampler sampler = null;
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            sampler = NoiseChunkHeightSampler.create(
                    level,
                    noiseGenerator,
                    level.getChunkSource().getGeneratorState().randomState());
        }
        return new CpuAccurateHeightBackend(
                level, generator, level.getChunkSource().getGeneratorState().randomState(), sampler);
    }

    @Override
    public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return result;
        }
        long startedAt = System.nanoTime();
        for (long key : chunkKeys) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            result.put(key, sampleChunk(chunkX, chunkZ));
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
        LinkedHashMap<Long, List<Map.Entry<Long, BlockPos>>> byChunk = groupPositionsByChunk(unique);
        if (noiseChunkSampler != null) {
            for (Map.Entry<Long, List<Map.Entry<Long, BlockPos>>> chunkEntry : byChunk.entrySet()) {
                long chunkKey = chunkEntry.getKey();
                List<Map.Entry<Long, BlockPos>> chunkPositions = chunkEntry.getValue();
                int[] columns = new int[chunkPositions.size()];
                for (int index = 0; index < chunkPositions.size(); index++) {
                    BlockPos position = chunkPositions.get(index).getValue();
                    columns[index] = localIndex(position.getX(), position.getZ());
                }
                AccurateHeightChunk chunk = noiseChunkSampler.sampleChunkColumns(
                        ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), columns);
                for (Map.Entry<Long, BlockPos> entry : chunkPositions) {
                    BlockPos position = entry.getValue();
                    result.put(entry.getKey(), sampleFromChunk(chunk, position.getX(), position.getZ()));
                }
            }
        } else {
            for (Map.Entry<Long, BlockPos> entry : unique.entrySet()) {
                BlockPos position = entry.getValue();
                result.put(entry.getKey(), sampleBaseHeights(position.getX(), position.getZ()));
            }
        }
        AccurateSamplingStats.recordCpuBatch(byChunk.size(), System.nanoTime() - startedAt);
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
        LinkedHashMap<Long, IntList> indexesByChunk = groupGridIndexesByChunk(request);

        if (noiseChunkSampler != null) {
            for (Map.Entry<Long, IntList> entry : indexesByChunk.entrySet()) {
                long chunkKey = entry.getKey();
                IntList gridIndexes = entry.getValue();
                int[] columns = new int[gridIndexes.size()];
                for (int offset = 0; offset < gridIndexes.size(); offset++) {
                    int gridIndex = gridIndexes.get(offset);
                    columns[offset] = localIndex(request.blockX(gridIndex), request.blockZ(gridIndex));
                }
                AccurateHeightChunk chunk = noiseChunkSampler.sampleChunkColumns(
                        ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), columns);
                for (int offset = 0; offset < gridIndexes.size(); offset++) {
                    int gridIndex = gridIndexes.get(offset);
                    int blockX = request.blockX(gridIndex);
                    int blockZ = request.blockZ(gridIndex);
                    worldSurface[gridIndex] = chunk.worldSurfaceWg(blockX, blockZ);
                    oceanFloor[gridIndex] = chunk.oceanFloorWg(blockX, blockZ);
                    motionBlocking[gridIndex] = chunk.motionBlockingNoLeaves(blockX, blockZ);
                }
            }
        } else {
            for (int index = 0; index < sampleCount; index++) {
                AccurateHeightSample sample = sampleBaseHeights(request.blockX(index), request.blockZ(index));
                worldSurface[index] = sample.worldSurfaceWg();
                oceanFloor[index] = sample.oceanFloorWg();
                motionBlocking[index] = sample.motionBlockingNoLeaves();
            }
        }

        long elapsedNanos = System.nanoTime() - startedAt;
        AccurateSamplingStats.recordCpuBatch(indexesByChunk.size(), elapsedNanos);
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        sink.onBatch(new AccurateSamplingProgress.Batch(
                sampleCount, sampleCount, sampleCount, elapsedNanos, elapsedNanos, 0L));
        return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
    }

    private AccurateHeightChunk sampleChunk(int chunkX, int chunkZ) {
        if (noiseChunkSampler != null) {
            return noiseChunkSampler.sampleChunk(chunkX, chunkZ);
        }

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
                worldSurface[index] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
                oceanFloor[index] = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                motionBlocking[index] = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, randomState);
            }
        }
        return new AccurateHeightChunk(chunkX, chunkZ, worldSurface, oceanFloor, motionBlocking);
    }

    private AccurateHeightSample sampleBaseHeights(int x, int z) {
        return new AccurateHeightSample(
                generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState),
                generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState),
                generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, randomState));
    }

    private static AccurateHeightSample sampleFromChunk(AccurateHeightChunk chunk, int x, int z) {
        return new AccurateHeightSample(
                chunk.worldSurfaceWg(x, z),
                chunk.oceanFloorWg(x, z),
                chunk.motionBlockingNoLeaves(x, z));
    }

    private static int localIndex(int blockX, int blockZ) {
        return (blockX & 15) + ((blockZ & 15) << 4);
    }

    private static LinkedHashMap<Long, List<Map.Entry<Long, BlockPos>>> groupPositionsByChunk(
            LinkedHashMap<Long, BlockPos> positions) {
        LinkedHashMap<Long, List<Map.Entry<Long, BlockPos>>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Long, BlockPos> entry : positions.entrySet()) {
            BlockPos position = entry.getValue();
            long chunkKey = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
            grouped.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private static LinkedHashMap<Long, IntList> groupGridIndexesByChunk(AccurateHeightGridRequest request) {
        LinkedHashMap<Long, IntList> grouped = new LinkedHashMap<>();
        for (int index = 0; index < request.sampleCount(); index++) {
            long chunkKey = ChunkPos.asLong(request.blockX(index) >> 4, request.blockZ(index) >> 4);
            grouped.computeIfAbsent(chunkKey, ignored -> new IntList()).add(index);
        }
        return grouped;
    }

    private static final class IntList {
        private int[] values = new int[4];
        private int size;

        private void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        private int get(int index) {
            return values[index];
        }

        private int size() {
            return size;
        }
    }

    @Override
    public String backendName() {
        return "CPU";
    }

    @Override
    public void close() {
        if (noiseChunkSampler != null) {
            noiseChunkSampler.clear();
        }
    }
}
