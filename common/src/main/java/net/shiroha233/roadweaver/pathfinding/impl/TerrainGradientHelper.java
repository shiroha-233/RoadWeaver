package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.heightSampler;

/**
 * 地形梯度与等高线计算工具
 */
public final class TerrainGradientHelper {
    private TerrainGradientHelper() {}

    public static double[] terrainGradient(PathTerrainField terrain, int x, int z, int step) {
        int center = heightSampler(terrain, x, z);
        int hE = terrain.contains(x + step, z) ? heightSampler(terrain, x + step, z) : center;
        int hW = terrain.contains(x - step, z) ? heightSampler(terrain, x - step, z) : center;
        int hN = terrain.contains(x, z + step) ? heightSampler(terrain, x, z + step) : center;
        int hS = terrain.contains(x, z - step) ? heightSampler(terrain, x, z - step) : center;
        double dhdx = (hE - hW) / (2.0 * step);
        double dhdz = (hN - hS) / (2.0 * step);
        return new double[]{dhdx, dhdz};
    }

    public static double[] contourDirection(double gradX, double gradZ,
                                            double goalDx, double goalDz) {
        double cx = -gradZ;
        double cz = gradX;
        double mag = Math.sqrt(cx * cx + cz * cz);
        if (mag < 1e-6) {
            double gm = Math.sqrt(goalDx * goalDx + goalDz * goalDz);
            return gm > 1e-6 ? new double[]{goalDx / gm, goalDz / gm} : new double[]{1.0, 0.0};
        }
        cx /= mag;
        cz /= mag;
        if (cx * goalDx + cz * goalDz < 0) {
            cx = -cx;
            cz = -cz;
        }
        return new double[]{cx, cz};
    }

    public static double contourAlignment(double moveX, double moveZ,
                                          double contourX, double contourZ) {
        double moveMag = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveMag < 1e-6) return 0.0;
        return (moveX * contourX + moveZ * contourZ) / moveMag;
    }

    public static double computeGrade(BlockPos from, BlockPos to, double horizDist) {
        if (horizDist < 1e-6) return 0.0;
        return Math.abs(to.getY() - from.getY()) / horizDist;
    }

    public static double gradientMagnitude(double gradX, double gradZ) {
        return Math.sqrt(gradX * gradX + gradZ * gradZ);
    }
}
