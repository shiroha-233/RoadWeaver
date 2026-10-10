package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 高度轮廓服务
 */
public final class HeightProfileService {
    private HeightProfileService() {}

    public static record HeightProfile(boolean usePersisted, int[] smoothedY) {}

    public static HeightProfile build(WorldGenLevel world,
                                      List<BlockPos> middlePositions,
                                      ChunkPos currentChunk,
                                      int averagingRadius,
                                      ModConfig cfg,
                                      List<Integer> targetY) {
        boolean slopeLimitEnabled = cfg != null && cfg.slopeLimitEnabled();
        int maxSlopeStepPerTwoSegments = cfg != null ? cfg.maxSlopeStepPerTwoSegments() : 1;
        return build(world, middlePositions, currentChunk, averagingRadius, slopeLimitEnabled, maxSlopeStepPerTwoSegments, targetY);
    }

    public static HeightProfile build(WorldGenLevel world,
                                     List<BlockPos> middlePositions,
                                     ChunkPos currentChunk,
                                     int averagingRadius,
                                     boolean slopeLimitEnabled,
                                     int maxSlopeStepPerTwoSegments,
                                     List<Integer> targetY) {
        int n = middlePositions.size();
        boolean usePersisted = targetY != null && targetY.size() == n;
        if (usePersisted) {
            return new HeightProfile(true, null);
        }
        int[] baseYArr = new int[n];
        int currentChunkX = currentChunk.x;
        int currentChunkZ = currentChunk.z;
        for (int ii = 0; ii < n; ii++) {
            BlockPos mid = middlePositions.get(ii);
            int heightSum = 0;
            int heightCount = 0;
            int from = Math.max(0, ii - averagingRadius);
            int to = Math.min(n - 1, ii + averagingRadius);
            for (int jj = from; jj <= to; jj++) {
                BlockPos sp = middlePositions.get(jj);
                // 位移比较替代 new ChunkPos(sp).equals(currentChunk)，避免逐点分配
                if ((sp.getX() >> 4) == currentChunkX && (sp.getZ() >> 4) == currentChunkZ) {
                    heightSum += world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
                    heightCount++;
                }
            }
            if (heightCount == 0) {
                if ((mid.getX() >> 4) == currentChunkX && (mid.getZ() >> 4) == currentChunkZ) {
                    baseYArr[ii] = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mid.getX(), mid.getZ());
                } else {
                    baseYArr[ii] = mid.getY();
                }
            } else {
                baseYArr[ii] = (int) Math.round(heightSum / (double) heightCount);
            }
        }
        if (!slopeLimitEnabled) {
            int[] noSmoothed = new int[n];
            for (int ii = 0; ii < n; ii++) noSmoothed[ii] = baseYArr[ii];
            return new HeightProfile(false, noSmoothed);
        }

        int[] smoothed = new int[n];
        for (int ii = 0; ii < n; ii++) smoothed[ii] = baseYArr[ii];
        int step2 = Math.max(0, Math.min(8, maxSlopeStepPerTwoSegments));
        int halfLow = Math.max(0, step2 / 2);
        int halfHigh = Math.max(0, (step2 + 1) / 2);
        
        for (int ii = 1; ii < n; ii++) {
            int y = smoothed[ii];
            if (ii == 1) {
                int py = smoothed[ii - 1];
                if (y > py + halfLow) y = py + halfLow;
                if (y < py - halfLow) y = py - halfLow;
            } else {
                int py = smoothed[ii - 1];
                if (y > py + halfHigh) y = py + halfHigh;
                if (y < py - halfHigh) y = py - halfHigh;
                int p2 = smoothed[ii - 2];
                int hi = p2 + step2;
                int lo = p2 - step2;
                if (y > hi) y = hi;
                if (y < lo) y = lo;
            }
            smoothed[ii] = y;
        }
        
        for (int ii = n - 2; ii >= 0; ii--) {
            int y = smoothed[ii];
            if (ii == n - 2) {
                int ny = smoothed[ii + 1];
                if (y > ny + halfLow) y = ny + halfLow;
                if (y < ny - halfLow) y = ny - halfLow;
            } else {
                int ny = smoothed[ii + 1];
                if (y > ny + halfHigh) y = ny + halfHigh;
                if (y < ny - halfHigh) y = ny - halfHigh;
                int n2 = smoothed[ii + 2];
                int hi = n2 + step2;
                int lo = n2 - step2;
                if (y > hi) y = hi;
                if (y < lo) y = lo;
            }
            smoothed[ii] = y;
        }
        return new HeightProfile(false, smoothed);
    }
}
