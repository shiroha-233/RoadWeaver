package net.shiroha233.roadweaver.planning.terrain;

/**
 * 自动规划地形采样的闭区间边界。
 */
public record AutomaticPlanningSamplingBounds(int minX, int minZ, int maxX, int maxZ) {
    public AutomaticPlanningSamplingBounds {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid automatic planning sampling bounds");
        }
    }
}
