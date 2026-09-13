/* 文件职责：按原版 aquifer 网格生成 GPU 批次所需的确定性候选位置。 */
package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * aquifer 候选坐标输入规划器。
 */
final class AquiferPositionPlanner {
    private static final int GRID_XZ_SIZE = 3;
    private static final int[] SURFACE_OFFSETS = {
            0, 0,
            -2, -1,
            -1, -1,
            0, -1,
            1, -1,
            -3, 0,
            -2, 0,
            -1, 0,
            1, 0,
            -2, 1,
            -1, 1,
            0, 1,
            1, 1
    };

    private AquiferPositionPlanner() {}

    static Plan plan(List<Long> chunkKeys,
                     PositionalRandomFactory randomFactory,
                     int minY,
                     int height) {
        int minGridY = Math.floorDiv(minY, 12) - 1;
        int maxGridY = Math.floorDiv(minY + height, 12) + 1;
        int gridYSize = maxGridY - minGridY + 1;
        int pointsPerChunk = GRID_XZ_SIZE * gridYSize * GRID_XZ_SIZE;
        int[] chunkCoordinates = new int[Math.multiplyExact(chunkKeys.size(), 2)];
        int[] chunkPointIndices = new int[Math.multiplyExact(chunkKeys.size(), pointsPerChunk)];
        Map<GridPoint, Integer> uniqueIndices = new LinkedHashMap<>();
        List<GridPoint> uniquePoints = new ArrayList<>();

        for (int chunkIndex = 0; chunkIndex < chunkKeys.size(); chunkIndex++) {
            long key = chunkKeys.get(chunkIndex);
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            chunkCoordinates[chunkIndex * 2] = chunkX;
            chunkCoordinates[chunkIndex * 2 + 1] = chunkZ;
            int minGridX = chunkX - 1;
            int minGridZ = chunkZ - 1;
            for (int gridYIndex = 0; gridYIndex < gridYSize; gridYIndex++) {
                int gridY = minGridY + gridYIndex;
                for (int gridZIndex = 0; gridZIndex < GRID_XZ_SIZE; gridZIndex++) {
                    int gridZ = minGridZ + gridZIndex;
                    for (int gridXIndex = 0; gridXIndex < GRID_XZ_SIZE; gridXIndex++) {
                        int gridX = minGridX + gridXIndex;
                        int point = ((gridYIndex * GRID_XZ_SIZE + gridZIndex) * GRID_XZ_SIZE + gridXIndex);
                        GridPoint gridPoint = new GridPoint(gridX, gridY, gridZ);
                        int uniqueIndex = uniqueIndices.computeIfAbsent(gridPoint, ignored -> {
                            int next = uniquePoints.size();
                            uniquePoints.add(gridPoint);
                            return next;
                        });
                        chunkPointIndices[chunkIndex * pointsPerChunk + point] = uniqueIndex;
                    }
                }
            }
        }

        int[] positions = new int[Math.multiplyExact(uniquePoints.size(), 3)];
        for (int index = 0; index < uniquePoints.size(); index++) {
            GridPoint point = uniquePoints.get(index);
            RandomSource random = randomFactory.at(point.x(), point.y(), point.z());
            int offset = index * 3;
            positions[offset] = point.x() * 16 + random.nextInt(10);
            positions[offset + 1] = point.y() * 12 + random.nextInt(9);
            positions[offset + 2] = point.z() * 16 + random.nextInt(10);
        }

        int[] pointPreliminaryIndices = new int[Math.multiplyExact(uniquePoints.size(), 13)];
        Map<SurfacePoint, Integer> preliminaryIndices = new LinkedHashMap<>();
        List<SurfacePoint> preliminaryPoints = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < uniquePoints.size(); pointIndex++) {
            int positionOffset = pointIndex * 3;
            int x = positions[positionOffset];
            int z = positions[positionOffset + 2];
            for (int offsetIndex = 0; offsetIndex < 13; offsetIndex++) {
                int sampleX = alignToQuart(x + SURFACE_OFFSETS[offsetIndex * 2] * 16);
                int sampleZ = alignToQuart(z + SURFACE_OFFSETS[offsetIndex * 2 + 1] * 16);
                SurfacePoint surfacePoint = new SurfacePoint(sampleX, sampleZ);
                int preliminaryIndex = preliminaryIndices.computeIfAbsent(surfacePoint, ignored -> {
                    int next = preliminaryPoints.size();
                    preliminaryPoints.add(surfacePoint);
                    return next;
                });
                pointPreliminaryIndices[pointIndex * 13 + offsetIndex] = preliminaryIndex;
            }
        }
        int[] preliminaryCoordinates = new int[Math.multiplyExact(preliminaryPoints.size(), 2)];
        for (int index = 0; index < preliminaryPoints.size(); index++) {
            SurfacePoint point = preliminaryPoints.get(index);
            preliminaryCoordinates[index * 2] = point.x();
            preliminaryCoordinates[index * 2 + 1] = point.z();
        }
        return new Plan(
                chunkCoordinates,
                positions,
                chunkPointIndices,
                preliminaryCoordinates,
                pointPreliminaryIndices,
                minGridY,
                gridYSize,
                pointsPerChunk,
                uniquePoints.size(),
                preliminaryPoints.size());
    }

    record Plan(int[] chunkCoordinates,
                int[] positions,
                int[] chunkPointIndices,
                int[] preliminaryCoordinates,
                int[] pointPreliminaryIndices,
                int minGridY,
                int gridYSize,
                int pointsPerChunk,
                int uniquePointCount,
                int preliminaryPointCount) {}

    private static int alignToQuart(int value) {
        return Math.floorDiv(value, 4) * 4;
    }

    private record GridPoint(int x, int y, int z) {}

    private record SurfacePoint(int x, int z) {}
}
