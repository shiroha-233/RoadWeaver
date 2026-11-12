package net.shiroha233.roadweaver.pathfinding.impl;

import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig.PathfindingAlgorithm;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;

/**
 * 寻路器工厂，根据算法枚举创建对应的寻路器实例。
 */
public final class PathfinderFactory {

    private PathfinderFactory() {}

    public static Pathfinder create(PathfindingAlgorithm algorithm) {
        return switch (algorithm) {
            case ASTAR_BASIC -> new BasicAStarPathfinder();
            case ASTAR_BIDIRECTIONAL -> new BidirectionalAStarPathfinder();
            case GRADIENT_DESCENT -> new GradientDescentPathfinder();
            case POTENTIAL_FIELD -> new PotentialFieldPathfinder();
        };
    }
}
