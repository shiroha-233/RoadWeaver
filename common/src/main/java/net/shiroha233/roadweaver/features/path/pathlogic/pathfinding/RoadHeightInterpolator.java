package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 道路高度插值器
 */
public final class RoadHeightInterpolator {
    private RoadHeightInterpolator() {}

    public static int getInterpolatedY(int x, int z, List<BlockPos> centers, int[] targetY) {
        if (centers == null || centers.isEmpty() || targetY == null || targetY.length == 0) {
            return 64;
        }
        
        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            return targetY[0];
        }
        
        if (targetY.length != n) {
            return targetY[0];
        }
        
        ProjectionResult proj = findNearestProjection(x, z, centers, 0, n - 2);
        return interpolateY(proj.segmentIndex, proj.t, targetY);
    }

    public static int getInterpolatedYNear(int x,
                                           int z,
                                           List<BlockPos> centers,
                                           int[] targetY,
                                           int segmentIndex,
                                           int radius) {
        if (centers == null || centers.isEmpty() || targetY == null || targetY.length == 0) {
            return 64;
        }
        int size = Math.min(centers.size(), targetY.length);
        if (size == 1) return targetY[0];
        if (size != centers.size() || size != targetY.length) return targetY[0];

        int searchRadius = Math.max(1, radius);
        int anchor = Math.max(0, Math.min(size - 2, segmentIndex));
        int start = Math.max(0, anchor - searchRadius);
        int end = Math.min(size - 2, anchor + searchRadius);
        ProjectionResult projection = findNearestProjection(x, z, centers, start, end);
        return interpolateY(projection.segmentIndex, projection.t, targetY);
    }

    private record ProjectionResult(int segmentIndex, double t, double distSq) {}

    private static ProjectionResult findNearestProjection(int x,
                                                          int z,
                                                          List<BlockPos> centers,
                                                          int searchStart,
                                                          int searchEnd) {
        int bestSegment = searchStart;
        double bestT = 0.0;
        double bestDistSq = Double.MAX_VALUE;
        
        for (int i = searchStart; i <= searchEnd; i++) {
            BlockPos a = centers.get(i);
            BlockPos b = centers.get(i + 1);
            
            double ax = a.getX();
            double az = a.getZ();
            double bx = b.getX();
            double bz = b.getZ();
            
            double dx = bx - ax;
            double dz = bz - az;
            double lenSq = dx * dx + dz * dz;
            
            double t;
            if (lenSq < 1e-9) {
                t = 0.0;
            } else {
                t = ((x - ax) * dx + (z - az) * dz) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
            }
            
            double projX = ax + t * dx;
            double projZ = az + t * dz;
            double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestSegment = i;
                bestT = t;
            }
        }
        
        return new ProjectionResult(bestSegment, bestT, bestDistSq);
    }

    private static int interpolateY(int segmentIndex, double t, int[] targetY) {
        int y0 = targetY[segmentIndex];
        int y1 = targetY[segmentIndex + 1];
        double interpolated = y0 + t * (y1 - y0);
        return (int) Math.round(interpolated);
    }

    public static int[] batchInterpolate(List<BlockPos> positions, 
                                         int segmentIndex,
                                         List<BlockPos> centers, 
                                         int[] targetY) {
        if (positions == null || positions.isEmpty()) {
            return new int[0];
        }
        if (centers == null || targetY == null || centers.size() < 2 || centers.size() != targetY.length) {
            return new int[positions.size()];
        }
        
        int[] results = new int[positions.size()];
        int n = centers.size();

        int extendedRadius = 20;
        int anchor = Math.max(0, Math.min(n - 2, segmentIndex));
        int searchStart = Math.max(0, anchor - extendedRadius);
        int searchEnd = Math.min(n - 2, anchor + extendedRadius);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            int x = pos.getX();
            int z = pos.getZ();

            ProjectionResult projection = findNearestProjection(x, z, centers, searchStart, searchEnd);
            results[i] = interpolateY(projection.segmentIndex, projection.t, targetY);
        }
        
        return results;
    }
}
