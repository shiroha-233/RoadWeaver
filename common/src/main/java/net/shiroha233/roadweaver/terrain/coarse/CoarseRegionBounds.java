package net.shiroha233.roadweaver.terrain.coarse;

import net.minecraft.resources.Identifier;

/**
 * 区域级粗采样边界。
 */
public record CoarseRegionBounds(Identifier dimensionId,
                                 int minX,
                                 int minZ,
                                 int maxX,
                                 int maxZ,
                                 int step,
                                 int width,
                                 int height) {
    public CoarseRegionBounds {
        if (dimensionId == null) {
            throw new IllegalArgumentException("dimensionId must not be null");
        }
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

    public static CoarseRegionBounds aligned(Identifier dimensionId,
                                             int minBlockX,
                                             int minBlockZ,
                                             int maxBlockX,
                                             int maxBlockZ,
                                             int step) {
        int safeStep = Math.max(1, step);
        int x0 = Math.floorDiv(Math.min(minBlockX, maxBlockX), safeStep) * safeStep;
        int z0 = Math.floorDiv(Math.min(minBlockZ, maxBlockZ), safeStep) * safeStep;
        int x1 = ceilToGrid(Math.max(minBlockX, maxBlockX), safeStep);
        int z1 = ceilToGrid(Math.max(minBlockZ, maxBlockZ), safeStep);
        int w = Math.floorDiv(x1 - x0, safeStep) + 1;
        int h = Math.floorDiv(z1 - z0, safeStep) + 1;
        return new CoarseRegionBounds(dimensionId, x0, z0, x1, z1, safeStep, w, h);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public long sampleCount() {
        return (long) width * (long) height;
    }

    public int indexOfNearest(int x, int z) {
        int ix = clamp(Math.floorDiv(x - minX + step / 2, step), 0, width - 1);
        int iz = clamp(Math.floorDiv(z - minZ + step / 2, step), 0, height - 1);
        return iz * width + ix;
    }

    int blockXAt(int ix) {
        return minX + ix * step;
    }

    int blockZAt(int iz) {
        return minZ + iz * step;
    }

    private static int ceilToGrid(int value, int step) {
        return Math.floorDiv(value + step - 1, step) * step;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}