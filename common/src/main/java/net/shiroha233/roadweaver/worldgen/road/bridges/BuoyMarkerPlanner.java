package net.shiroha233.roadweaver.worldgen.road.bridges;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 浮标标记规划器
 */
public final class BuoyMarkerPlanner {
    private BuoyMarkerPlanner() {}

    private static double dist2d(BlockPos a, BlockPos b) {
        long dx = (long) b.getX() - a.getX();
        long dz = (long) b.getZ() - a.getZ();
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    public static boolean[] markersForBridgeRanges(List<BlockPos> middlePositions, List<int[]> bridgeRanges, int intervalBlocks) {
        if (middlePositions == null || middlePositions.isEmpty()) return new boolean[0];
        int n = middlePositions.size();
        boolean[] res = new boolean[n];
        if (bridgeRanges == null || bridgeRanges.isEmpty()) return res;

        int interval = Math.max(1, intervalBlocks);
        for (int[] r : bridgeRanges) {
            if (r == null || r.length < 2) continue;
            int s = Math.max(0, r[0] + 1);
            int e = Math.min(n - 1, r[1] - 1);
            if (s > e) continue;

            double acc = 0.0;
            double next = 0.0;
            for (int idx = s; idx <= e; idx++) {
                if (idx > s) {
                    acc += dist2d(middlePositions.get(idx - 1), middlePositions.get(idx));
                }
                if (acc + 1e-6 >= next) {
                    res[idx] = true;
                    next += interval;
                }
            }
        }
        return res;
    }

    public static boolean[] markersForMask(List<BlockPos> middlePositions, boolean[] mask, int intervalBlocks) {
        if (middlePositions == null || middlePositions.isEmpty()) return new boolean[0];
        int n = middlePositions.size();
        boolean[] res = new boolean[n];
        if (mask == null || mask.length != n) return res;

        int interval = Math.max(1, intervalBlocks);
        int idx = 0;
        while (idx < n) {
            while (idx < n && !mask[idx]) idx++;
            if (idx >= n) break;
            int start = idx;
            while (idx < n && mask[idx]) idx++;
            int end = idx - 1;

            double acc = 0.0;
            double next = 0.0;
            for (int i = start; i <= end; i++) {
                if (i > start) {
                    acc += dist2d(middlePositions.get(i - 1), middlePositions.get(i));
                }
                if (acc + 1e-6 >= next) {
                    res[i] = true;
                    next += interval;
                }
            }
        }
        return res;
    }
}
