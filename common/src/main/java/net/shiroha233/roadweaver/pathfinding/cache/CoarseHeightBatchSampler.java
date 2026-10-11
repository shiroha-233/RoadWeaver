package net.shiroha233.roadweaver.pathfinding.cache;

/**
 * 粗高度批量采样器。
 */
public interface CoarseHeightBatchSampler extends AutoCloseable {
    int[] sampleHeights(CoarseHeightBatchRequest request);

    default boolean isAccelerated() {
        return false;
    }

    default String deviceName() {
        return "CPU";
    }

    @Override
    default void close() {}
}
