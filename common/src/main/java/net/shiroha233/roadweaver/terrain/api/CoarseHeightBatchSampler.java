package net.shiroha233.roadweaver.terrain.api;

/**
 * 粗高度批量采样器。
 */
public interface CoarseHeightBatchSampler extends AutoCloseable {
    int[] sampleHeights(CoarseHeightBatchRequest request);

    default boolean isAccelerated() {
        return false;
    }

    @Override
    default void close() {}
}