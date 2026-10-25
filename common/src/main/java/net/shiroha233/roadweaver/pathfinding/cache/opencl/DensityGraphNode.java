package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * Density graph 紧凑节点。
 */
public record DensityGraphNode(
        DensityGraphNodeType type,
        int left,
        int right,
        int extraA,
        int extraB,
        double valueA,
        double valueB,
        double valueC,
        double valueD
) {}
