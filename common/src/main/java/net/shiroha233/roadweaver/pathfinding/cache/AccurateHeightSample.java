package net.shiroha233.roadweaver.pathfinding.cache;

/**
 * 单个世界列的精确高度采样结果。
 */
public record AccurateHeightSample(int worldSurfaceWg,
                                   int oceanFloorWg,
                                   int motionBlockingNoLeaves) {
    public static long key(int blockX, int blockZ) {
        return ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
    }
}
