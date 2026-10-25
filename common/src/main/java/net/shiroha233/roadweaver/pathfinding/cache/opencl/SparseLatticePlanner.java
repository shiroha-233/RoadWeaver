package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * 保留完整 lattice 存储布局，仅压缩 GPU 需要执行的角点集合。
 */
final class SparseLatticePlanner {
    private SparseLatticePlanner() {}

    static Plan plan(int chunkCount,
                     int[] columnReferences,
                     int cellWidth,
                     int cellCountXZ,
                     int cellCountY) {
        if (chunkCount <= 0 || cellWidth <= 0 || cellCountXZ <= 0 || cellCountY <= 0) {
            throw new IllegalArgumentException("invalid sparse lattice dimensions");
        }
        if (columnReferences == null || columnReferences.length == 0
                || columnReferences.length % 3 != 0) {
            throw new IllegalArgumentException("invalid column references");
        }

        int latticeXZ = cellCountXZ + 1;
        int latticeY = cellCountY + 1;
        int horizontalPointsPerChunk = Math.multiplyExact(latticeXZ, latticeXZ);
        int latticePointsPerChunk = Math.multiplyExact(horizontalPointsPerChunk, latticeY);
        boolean[] requiredHorizontal = new boolean[Math.multiplyExact(chunkCount, horizontalPointsPerChunk)];
        int requiredHorizontalCount = 0;

        for (int offset = 0; offset < columnReferences.length; offset += 3) {
            int chunkIndex = columnReferences[offset];
            int localX = columnReferences[offset + 1];
            int localZ = columnReferences[offset + 2];
            if (chunkIndex < 0 || chunkIndex >= chunkCount
                    || localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                throw new IllegalArgumentException("column reference is outside its chunk");
            }
            int cellX = localX / cellWidth;
            int cellZ = localZ / cellWidth;
            if (cellX >= cellCountXZ || cellZ >= cellCountXZ) {
                throw new IllegalArgumentException("column reference is outside the noise lattice");
            }
            requiredHorizontalCount += mark(requiredHorizontal, chunkIndex, horizontalPointsPerChunk,
                    latticeXZ, cellX, cellZ);
            requiredHorizontalCount += mark(requiredHorizontal, chunkIndex, horizontalPointsPerChunk,
                    latticeXZ, cellX + 1, cellZ);
            requiredHorizontalCount += mark(requiredHorizontal, chunkIndex, horizontalPointsPerChunk,
                    latticeXZ, cellX, cellZ + 1);
            requiredHorizontalCount += mark(requiredHorizontal, chunkIndex, horizontalPointsPerChunk,
                    latticeXZ, cellX + 1, cellZ + 1);
        }

        int fullHorizontalCount = Math.multiplyExact(chunkCount, horizontalPointsPerChunk);
        if (requiredHorizontalCount == fullHorizontalCount) {
            return new Plan(new int[]{0}, false,
                    Math.multiplyExact(chunkCount, latticePointsPerChunk), latticePointsPerChunk);
        }

        int[] references = new int[Math.multiplyExact(requiredHorizontalCount, latticeY)];
        int resultIndex = 0;
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int horizontalBase = chunkIndex * horizontalPointsPerChunk;
            int latticeBase = chunkIndex * latticePointsPerChunk;
            for (int horizontal = 0; horizontal < horizontalPointsPerChunk; horizontal++) {
                if (!requiredHorizontal[horizontalBase + horizontal]) {
                    continue;
                }
                int pointBase = horizontal * latticeY;
                for (int cellY = 0; cellY < latticeY; cellY++) {
                    references[resultIndex++] = latticeBase + pointBase + cellY;
                }
            }
        }
        if (resultIndex != references.length) {
            throw new IllegalStateException("sparse lattice reference count mismatch");
        }
        return new Plan(references, true, references.length, latticePointsPerChunk);
    }

    private static int mark(boolean[] required,
                            int chunkIndex,
                            int pointsPerChunk,
                            int latticeXZ,
                            int cellX,
                            int cellZ) {
        int index = chunkIndex * pointsPerChunk + cellX * latticeXZ + cellZ;
        if (required[index]) {
            return 0;
        }
        required[index] = true;
        return 1;
    }

    record Plan(int[] references,
                boolean sparse,
                int workItemCount,
                int latticePointsPerChunk) {}
}
