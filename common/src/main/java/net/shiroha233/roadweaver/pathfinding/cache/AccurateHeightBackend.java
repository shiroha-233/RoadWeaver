package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可批量生成精确区块高度图的后端。
 */
public interface AccurateHeightBackend extends AutoCloseable {
    Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys);

    default Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Long, BlockPos> unique = new LinkedHashMap<>();
        LinkedHashMap<Long, Long> chunkByPosition = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            if (position == null) {
                continue;
            }
            long positionKey = AccurateHeightSample.key(position.getX(), position.getZ());
            unique.putIfAbsent(positionKey, position);
            chunkByPosition.putIfAbsent(positionKey, ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<Long, AccurateHeightChunk> chunks = sampleChunks(chunkByPosition.values());
        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>(unique.size());
        for (Map.Entry<Long, BlockPos> entry : unique.entrySet()) {
            BlockPos position = entry.getValue();
            AccurateHeightChunk chunk = chunks.get(chunkByPosition.get(entry.getKey()));
            if (chunk != null) {
                result.put(entry.getKey(), new AccurateHeightSample(
                        chunk.worldSurfaceWg(position.getX(), position.getZ()),
                        chunk.oceanFloorWg(position.getX(), position.getZ()),
                        chunk.motionBlockingNoLeaves(position.getX(), position.getZ())));
            }
        }
        return result;
    }

    default Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions,
                                                             AccurateSamplingProgress progress) {
        long startedAt = System.nanoTime();
        Map<Long, AccurateHeightSample> result = samplePositions(positions);
        long elapsedNanos = System.nanoTime() - startedAt;
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        sink.onBatch(new AccurateSamplingProgress.Batch(
                result.size(), result.size(), result.size(),
                elapsedNanos, elapsedNanos, 0L));
        return result;
    }

    default AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                            AccurateSamplingProgress progress) {
        ArrayList<BlockPos> positions = new ArrayList<>(request.sampleCount());
        for (int index = 0; index < request.sampleCount(); index++) {
            positions.add(new BlockPos(request.blockX(index), 0, request.blockZ(index)));
        }
        Map<Long, AccurateHeightSample> samples = samplePositions(positions, progress);
        int[] worldSurface = new int[request.sampleCount()];
        int[] oceanFloor = new int[request.sampleCount()];
        int[] motionBlocking = new int[request.sampleCount()];
        for (int index = 0; index < request.sampleCount(); index++) {
            AccurateHeightSample sample = samples.get(AccurateHeightSample.key(
                    request.blockX(index), request.blockZ(index)));
            if (sample == null) {
                throw new IllegalStateException("accurate backend omitted grid column " + index);
            }
            worldSurface[index] = sample.worldSurfaceWg();
            oceanFloor[index] = sample.oceanFloorWg();
            motionBlocking[index] = sample.motionBlockingNoLeaves();
        }
        return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
    }

    default boolean supportsAcceleratedSampling() {
        return false;
    }

    default Map<Long, AccurateHeightSample> sampleAcceleratedPositions(Collection<BlockPos> positions,
                                                                        AccurateSamplingProgress progress) {
        throw new AcceleratedSamplingUnavailableException("accelerated accurate sampling is unavailable");
    }

    default AccurateHeightGrid sampleAcceleratedGrid(AccurateHeightGridRequest request,
                                                      AccurateSamplingProgress progress) {
        throw new AcceleratedSamplingUnavailableException("accelerated accurate sampling is unavailable");
    }

    String backendName();

    default String deviceName() {
        return backendName();
    }

    @Override
    default void close() {}
}
