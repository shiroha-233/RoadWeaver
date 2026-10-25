package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.*;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.*;

/**
 * 基础 A* 寻路实现
 */
public final class BasicAStarPathfinder implements Pathfinder {

    private static final int BIOME_BASE_COST = 12;
    private static final double HEURISTIC_EPSILON = 0.2;

    @Override
    public PathResult findPath(BlockPos start, BlockPos end, int width,
                               ServerLevel level, int maxSteps,
                               TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig cfg) {
        List<BlockPos> rawPath = searchRawPath(start, end, level, maxSteps, terrain, cfg);
        if (rawPath == null || rawPath.isEmpty()) {
            return PathResult.failure();
        }
        List<RoadSegmentPlacement> segments = reconstructPath(rawPath, width, level, cache, false);
        return segments != null ? PathResult.success(segments) : PathResult.failure();
    }

    @Override
    public PathResult findRawPath(BlockPos start, BlockPos end,
                                  ServerLevel level, int maxSteps,
                                  TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig cfg) {
        List<BlockPos> rawPath = searchRawPath(start, end, level, maxSteps, terrain, cfg);
        return rawPath == null || rawPath.isEmpty() ? PathResult.failure() : PathResult.raw(rawPath);
    }

    private List<BlockPos> searchRawPath(BlockPos start, BlockPos end,
                                         ServerLevel level, int maxSteps,
                                         PathTerrainField terrain, PathfindingCostConfig cfg) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, null, 0.0, heuristic(start, end, cfg));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int d = cfg.effectiveAStarStep();
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        int stepsBudget = Math.max(1, maxSteps);
        ThrottleHelper.resetThrottle();
        try {
            while (stepsBudget > 0) {
                if (Thread.currentThread().isInterrupted()) return null;

                Node current = pollActiveOpenNode(openSet, allNodes, closed);
                if (current == null) break;

                stepsBudget--;
                ThrottleHelper.throttle(RoadConstants.DEFAULT_DUTY_CYCLE);
                if (Thread.currentThread().isInterrupted()) return null;

                if (manhattan2d(current.pos, end) < d * 2) {
                    return reconstructRawPath(current);
                }

                closed.add(current.pos);
                allNodes.remove(current.pos);

                for (int[] off : offsets) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    if (!terrain.contains(nxz.getX(), nxz.getZ())) continue;
                    int y = heightSampler(terrain, nxz.getX(), nxz.getZ());
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    if (closed.contains(np)) continue;

                    double tentativeG = current.g + computeMoveCost(current.pos, np, nxz, off, d,
                            start, end, terrain, cfg);

                    Node n = allNodes.get(np);
                    if (n == null || tentativeG < n.g) {
                        double h = heuristic(np, end, cfg);
                        double f = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
                        n = new Node(np, current, tentativeG, f);
                        allNodes.put(np, n);
                        openSet.add(n);
                    }
                }
            }
            return null;
        } finally {
            openSet.clear();
            allNodes.clear();
            closed.clear();
            ThrottleHelper.clearThrottle();
        }
    }

    private double computeMoveCost(BlockPos current, BlockPos np, BlockPos nxz, int[] off, int d,
                                   BlockPos start, BlockPos end, PathTerrainField terrain,
                                   PathfindingCostConfig cfg) {
        PathTerrainField.SampleBundle b = terrain.sampleBundle(np.getX(), np.getZ());
        int waterDepth = b.waterDepth();
        int biomeCost = b.waterBiome() ? BIOME_BASE_COST : 0;
        int elevation = Math.abs(np.getY() - current.getY());
        int offsetSum = Math.abs(off[0]) + Math.abs(off[1]);
        double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
        int stabilityCost = calculateTerrainStability(terrain, np, np.getY(), d);
        boolean nearWater = isNearWaterLike(terrain, nxz.getX(), nxz.getZ(), d);
        int waterDepthCost = b.columnWater() ? (int) (waterDepth * cfg.waterDepthWeight()) : 0;
        int nearWaterCost = nearWater ? cfg.nearWaterCost() : 0;
        double deviation = deviation2d(np, start, end);
        double deviationCost = deviation * cfg.deviationWeight() / Math.max(1.0, d);

        return stepCost
                + elevation * cfg.elevationWeight()
                + biomeCost * cfg.biomeWeight()
                + stabilityCost * cfg.stabilityWeight()
                + waterDepthCost
                + nearWaterCost
                + deviationCost;
    }

    private List<RoadSegmentPlacement> reconstructPath(List<BlockPos> rawPath, int width,
                                                       ServerLevel level, TerrainSamplingCache cache,
                                                       boolean needsRefinement) {
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        if (needsRefinement) {
            rawPath = accurate.samplePathHeights(rawPath, 0);
        }
        return PathPostProcessor.process(rawPath, width, level, cache,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH, accurate);
    }

    private List<BlockPos> reconstructRawPath(Node endNode) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node c = endNode;
        while (c != null) { rawPath.add(c.pos); c = c.parent; }
        Collections.reverse(rawPath);
        return rawPath;
    }

    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristic(BlockPos a, BlockPos b, PathfindingCostConfig cfg) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        double approx = Math.abs(dx) + Math.abs(dz) - 0.6 * Math.min(Math.abs(dx), Math.abs(dz));
        return approx * cfg.heuristicWeight();
    }

    private static double deviation2d(BlockPos p, BlockPos a, BlockPos b) {
        double ax = a.getX(), az = a.getZ(), bx = b.getX(), bz = b.getZ();
        double num = Math.abs((bz - az) * p.getX() - (bx - ax) * p.getZ() + bx * az - bz * ax);
        double den = Math.hypot(bx - ax, bz - az);
        return den <= 0.0 ? 0.0 : num / den;
    }

    private static boolean isActiveOpenNode(Node current, Map<BlockPos, Node> allNodes, Set<BlockPos> closed) {
        return current != null
                && !closed.contains(current.pos)
                && allNodes.get(current.pos) == current;
    }

    private static Node pollActiveOpenNode(PriorityQueue<Node> openSet,
                                           Map<BlockPos, Node> allNodes,
                                           Set<BlockPos> closed) {
        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (isActiveOpenNode(current, allNodes, closed)) {
                return current;
            }
        }
        return null;
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;
        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos; this.parent = parent; this.g = g; this.f = f;
        }
    }
}
