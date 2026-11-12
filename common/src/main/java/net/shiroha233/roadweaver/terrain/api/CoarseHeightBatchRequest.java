package net.shiroha233.roadweaver.terrain.api;

/**
 * 粗高度批量采样请求。
 */
public record CoarseHeightBatchRequest(
        int minBlockX,
        int minBlockZ,
        int step,
        int sampleWidth,
        int sampleHeight
) {
    public CoarseHeightBatchRequest {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        if (sampleWidth <= 0 || sampleHeight <= 0) {
            throw new IllegalArgumentException("sample size must be positive");
        }
        Math.multiplyExact(sampleWidth, sampleHeight);
    }

    public int sampleCount() {
        return Math.multiplyExact(sampleWidth, sampleHeight);
    }

    public int blockXAt(int sampleX) {
        return minBlockX + sampleX * step;
    }

    public int blockZAt(int sampleZ) {
        return minBlockZ + sampleZ * step;
    }
}