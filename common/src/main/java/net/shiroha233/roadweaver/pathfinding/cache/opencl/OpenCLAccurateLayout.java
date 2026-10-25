package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * Java 侧批次规划与 OpenCL kernel 共享的 lattice 布局说明。
 */
public final class OpenCLAccurateLayout {
    private OpenCLAccurateLayout() {}

    public static int latticePointCount(int cellCountXZ, int cellCountY) {
        return Math.multiplyExact(
                Math.multiplyExact(cellCountXZ + 1, cellCountXZ + 1),
                cellCountY + 1);
    }

    public static int latticePointIndex(int cellCountXZ,
                                        int cellCountY,
                                        int cellX,
                                        int cellY,
                                        int cellZ) {
        int latticeXZ = cellCountXZ + 1;
        int latticeY = cellCountY + 1;
        if (cellX < 0 || cellX >= latticeXZ
                || cellZ < 0 || cellZ >= latticeXZ
                || cellY < 0 || cellY >= latticeY) {
            throw new IndexOutOfBoundsException("lattice cell [" + cellX + "," + cellY + "," + cellZ + "]");
        }
        return (cellX * latticeXZ + cellZ) * latticeY + cellY;
    }

    public static double interpolateCell(double[] lattice,
                                         int cellCountXZ,
                                         int cellCountY,
                                         int cellX,
                                         int cellY,
                                         int cellZ,
                                         double deltaX,
                                         double deltaY,
                                         double deltaZ) {
        double v000 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX, cellY, cellZ)];
        double v100 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX + 1, cellY, cellZ)];
        double v010 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX, cellY + 1, cellZ)];
        double v110 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX + 1, cellY + 1, cellZ)];
        double v001 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX, cellY, cellZ + 1)];
        double v101 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX + 1, cellY, cellZ + 1)];
        double v011 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX, cellY + 1, cellZ + 1)];
        double v111 = lattice[latticePointIndex(cellCountXZ, cellCountY, cellX + 1, cellY + 1, cellZ + 1)];
        return lerp(deltaZ,
                lerp(deltaY, lerp(deltaX, v000, v100), lerp(deltaX, v010, v110)),
                lerp(deltaY, lerp(deltaX, v001, v101), lerp(deltaX, v011, v111)));
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
