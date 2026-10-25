package net.shiroha233.roadweaver.pathfinding.terrain.region;

/**
 * 区域级精确量化地形场的不可变网格边界。
 */
public record AccurateRegionBounds(int minX,
                                   int minZ,
                                   int maxX,
                                   int maxZ,
                                   int step,
                                   int width,
                                   int height) {
    public AccurateRegionBounds {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid region bounds");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid region size");
        }
    }

    public static AccurateRegionBounds aligned(int minBlockX,
                                               int minBlockZ,
                                               int maxBlockX,
                                               int maxBlockZ,
                                               int step) {
        int safeStep = Math.max(1, step);
        int x0 = alignDown(Math.min(minBlockX, maxBlockX), safeStep);
        int z0 = alignDown(Math.min(minBlockZ, maxBlockZ), safeStep);
        int x1 = alignUp(Math.max(minBlockX, maxBlockX), safeStep);
        int z1 = alignUp(Math.max(minBlockZ, maxBlockZ), safeStep);
        int width = Math.addExact(Math.floorDiv(x1 - x0, safeStep), 1);
        int height = Math.addExact(Math.floorDiv(z1 - z0, safeStep), 1);
        return new AccurateRegionBounds(x0, z0, x1, z1, safeStep, width, height);
    }

    public long sampleCount() {
        return Math.multiplyExact((long) width, height);
    }

    public boolean containsSample(int x, int z) {
        return contains(x, z)
                && Math.floorMod(x - minX, step) == 0
                && Math.floorMod(z - minZ, step) == 0;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int indexOf(int x, int z) {
        if (!containsSample(x, z)) {
            throw new IllegalArgumentException("Terrain field has no sample at " + x + "," + z);
        }
        int gridX = Math.floorDiv(x - minX, step);
        int gridZ = Math.floorDiv(z - minZ, step);
        return gridZ * width + gridX;
    }

    public int indexOfNearest(int x, int z) {
        if (!contains(x, z)) {
            throw new IllegalArgumentException("Terrain field does not cover " + x + "," + z);
        }
        int gridX = clamp(Math.floorDiv(x - minX + step / 2, step), 0, width - 1);
        int gridZ = clamp(Math.floorDiv(z - minZ + step / 2, step), 0, height - 1);
        return gridZ * width + gridX;
    }

    public int blockXAt(int gridX) {
        if (gridX < 0 || gridX >= width) {
            throw new IllegalArgumentException("grid x out of bounds: " + gridX);
        }
        return minX + gridX * step;
    }

    public int blockZAt(int gridZ) {
        if (gridZ < 0 || gridZ >= height) {
            throw new IllegalArgumentException("grid z out of bounds: " + gridZ);
        }
        return minZ + gridZ * step;
    }

    private static int alignDown(int value, int step) {
        return Math.floorDiv(value, step) * step;
    }

    private static int alignUp(int value, int step) {
        return -Math.floorDiv(-value, step) * step;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
