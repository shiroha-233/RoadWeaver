package net.shiroha233.roadweaver.terrain.coarse;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.terrain.FastHeightSampler;
import net.shiroha233.roadweaver.terrain.api.CoarseHeightBatchRequest;
import net.shiroha233.roadweaver.terrain.api.CoarseHeightBatchSampler;

/**
 * CPU 粗高度批量采样器。
 */
public final class CpuCoarseHeightBatchSampler implements CoarseHeightBatchSampler {
    private final FastHeightSampler sampler;

    private CpuCoarseHeightBatchSampler(FastHeightSampler sampler) {
        this.sampler = sampler;
    }

    public static CpuCoarseHeightBatchSampler create(ServerLevel level) {
        return new CpuCoarseHeightBatchSampler(FastHeightSampler.create(level));
    }

    @Override
    public int[] sampleHeights(CoarseHeightBatchRequest request) {
        long startedAt = System.currentTimeMillis();
        int[] heights = new int[request.sampleCount()];
        for (int z = 0; z < request.sampleHeight(); z++) {
            if (Thread.currentThread().isInterrupted()) return null;
            int blockZ = request.blockZAt(z);
            for (int x = 0; x < request.sampleWidth(); x++) {
                int blockX = request.blockXAt(x);
                heights[z * request.sampleWidth() + x] = sampler.sampleHeight(blockX, blockZ);
            }
        }
        InitialGenerationProgressTracker.recordSampleBatch("CPU", request.sampleCount(),
                System.currentTimeMillis() - startedAt, "CPU", null);
        return heights;
    }

    @Override
    public void close() {
        sampler.clearCache();
    }
}
