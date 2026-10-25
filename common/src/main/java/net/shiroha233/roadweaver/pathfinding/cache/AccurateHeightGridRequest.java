package net.shiroha233.roadweaver.pathfinding.cache;

/**
 * 按固定步长排列的区域列请求。
 */
public record AccurateHeightGridRequest(int minX, int minZ, int width, int height, int step) {
    public AccurateHeightGridRequest {
        if (width <= 0 || height <= 0 || step <= 0) {
            throw new IllegalArgumentException("invalid accurate height grid");
        }
        Math.multiplyExact(width, height);
    }

    public int sampleCount() {
        return Math.multiplyExact(width, height);
    }

    public int blockX(int index) {
        checkIndex(index);
        return Math.addExact(minX, Math.multiplyExact(index % width, step));
    }

    public int blockZ(int index) {
        checkIndex(index);
        return Math.addExact(minZ, Math.multiplyExact(index / width, step));
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= sampleCount()) {
            throw new IndexOutOfBoundsException(index);
        }
    }
}
