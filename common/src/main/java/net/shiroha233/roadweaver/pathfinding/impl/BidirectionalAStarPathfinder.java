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
 * 双向 A* 寻路
 */
public final class BidirectionalAStarPathfinder implements Pathfinder {

    private static final int BIOME_BASE_COST = 12;
    private static final double HEURISTIC_EPSILON = 0.2;

    @Override
    public PathResult findPath(BlockPos start, BlockPos end, int width,
                               ServerLevel level, int maxSteps,
                               TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig cfg) {
        List<BlockPos> rawPath = searchRawPath(start, end, level, maxSteps, terrain, cfg);
        if (rawPath == null) return PathResult.failure();
        if (rawPath.isEmpty()) return PathResult.success(Collections.emptyList());
        List<RoadSegmentPlacement> segments = reconstructPath(rawPath, width, level, cache, false);
        return segments != null ? PathResult.success(segments) : PathResult.failure();
    }

    @Override
    public PathResult findRawPath(BlockPos start, BlockPos end,
                                  ServerLevel level, int maxSteps,
                                  TerrainSamplingCache cache, PathTerrainField terrain, PathfindingCostConfig cfg) {
        List<BlockPos> rawPath = searchRawPath(start, end, level, maxSteps, terrain, cfg);
        if (rawPath == null) return PathResult.failure();
        if (rawPath.isEmpty()) return PathResult.success(Collections.emptyList());
        return PathResult.raw(rawPath);
    }

    private List<BlockPos> searchRawPath(BlockPos start, BlockPos end,
                                         ServerLevel level, int maxSteps,
                                         PathTerrainField terrain, PathfindingCostConfig cfg) {
        if (start.equals(end)) return Collections.emptyList();

        int d = cfg.effectiveAStarStep();
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        PriorityQueue<Node> openF = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        PriorityQueue<Node> openB = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> nodesF = new HashMap<>();
        Map<BlockPos, Node> nodesB = new HashMap<>();
        Set<BlockPos> closedF = new HashSet<>();
        Set<BlockPos> closedB = new HashSet<>();

        Node startNode = new Node(start, null, 0.0, heuristic(start, end, cfg));
        Node endNode = new Node(end, null, 0.0, heuristic(end, start, cfg));
        openF.add(startNode);
        nodesF.put(start, startNode);
        openB.add(endNode);
        nodesB.put(end, endNode);

        int stepsBudget = Math.max(1, maxSteps);
        ThrottleHelper.resetThrottle();
        try {
            while (stepsBudget > 0) {
                if (Thread.currentThread().isInterrupted()) return null;

                Node peekF = peekActiveOpenNode(openF, nodesF, closedF);
                Node peekB = peekActiveOpenNode(openB, nodesB, closedB);
                if (peekF == null || peekB == null) break;

                stepsBudget--;
                ThrottleHelper.throttle(RoadConstants.DEFAULT_DUTY_CYCLE);
                if (Thread.currentThread().isInterrupted()) return null;

                boolean expandForward;
                expandForward = peekF.f <= peekB.f;

                Meet meet;
                if (expandForward) {
                    meet = expandOneSide(openF, nodesF, closedF, nodesB, closedB,
                            true, start, end, terrain, offsets, d, cfg);
                } else {
                    meet = expandOneSide(openB, nodesB, closedB, nodesF, closedF,
                            false, end, start, terrain, offsets, d, cfg);
                }

                if (meet != null) {
                    return reconstructRawPath(meet.forward, meet.backward);
                }
            }
            return null;
        } finally {
            openF.clear(); openB.clear();
            nodesF.clear(); nodesB.clear();
            closedF.clear(); closedB.clear();
            ThrottleHelper.clearThrottle();
        }
    }

    private Meet expandOneSide(PriorityQueue<Node> open,
                               Map<BlockPos, Node> nodesThis, Set<BlockPos> closedThis,
                               Map<BlockPos, Node> nodesOther, Set<BlockPos> closedOther,
                               boolean isForward, BlockPos from, BlockPos to,
                               PathTerrainField terrain,
                               int[][] offsets, int d, PathfindingCostConfig cfg) {
        Node current = pollActiveOpenNode(open, nodesThis, closedThis);
        if (current == null) return null;
        closedThis.add(current.pos);

        for (int[] off : offsets) {
            if (Thread.currentThread().isInterrupted()) return null;
            BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
            if (!terrain.contains(nxz.getX(), nxz.getZ())) continue;
            int y = heightSampler(terrain, nxz.getX(), nxz.getZ());
            BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
            if (closedThis.contains(np)) continue;

            double tentativeG = current.g + computeMoveCost(current.pos, np, nxz, off, d,
                    from, to, terrain, cfg);

            Node existing = nodesThis.get(np);
            if (existing != null && tentativeG >= existing.g) continue;

            double h = heuristic(np, to, cfg);
            double f = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
            Node next = new Node(np, current, tentativeG, f);
            nodesThis.put(np, next);
            open.add(next);

            Node other = nodesOther.get(np);
            if (other == null && closedOther.contains(np)) other = nodesOther.get(np);
            if (other != null) {
                return isForward ? new Meet(next, other) : new Meet(other, next);
            }
        }
        return null;
    }

    private double computeMoveCost(BlockPos current, BlockPos np, BlockPos nxz, int[] off, int d,
                                   BlockPos from, BlockPos to, PathTerrainField terrain,
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
        double deviation = deviation2d(np, from, to);
        double deviationCost = deviation * cfg.deviationWeight() / Math.max(1.0, d);

        return stepCost
                + elevation * cfg.elevationWeight()
                + biomeCost * cfg.biomeWeight()
                + stabilityCost * cfg.stabilityWeight()
                + waterDepthCost
                + nearWaterCost
                + deviationCost;
    }

    private List<RoadSegmentPlacement> reconstructPath(List<BlockPos> rawPath,
                                                       int width, ServerLevel level,
                                                       TerrainSamplingCache cache,
                                                       boolean needsRefinement) {
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        if (needsRefinement) {
            rawPath = accurate.samplePathHeights(rawPath, 0);
        }
        return PathPostProcessor.process(rawPath, width, level, cache,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH, accurate);
    }

    private List<BlockPos> reconstructRawPath(Node meetForward, Node meetBackward) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node cur = meetForward;
        while (cur != null) { rawPath.add(cur.pos); cur = cur.parent; }
        Collections.reverse(rawPath);

        Node backStart = (meetBackward != null && meetBackward.pos.equals(meetForward.pos))
                ? meetBackward.parent : meetBackward;
        cur = backStart;
        while (cur != null) { rawPath.add(cur.pos); cur = cur.parent; }
        return rawPath;
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

    private static boolean isActiveOpenNode(Node current, Map<BlockPos, Node> nodesThis, Set<BlockPos> closedThis) {
        return current != null
                && !closedThis.contains(current.pos)
                && nodesThis.get(current.pos) == current;
    }

    private static Node peekActiveOpenNode(PriorityQueue<Node> open,
                                           Map<BlockPos, Node> nodesThis,
                                           Set<BlockPos> closedThis) {
        while (!open.isEmpty()) {
            Node current = open.peek();
            if (isActiveOpenNode(current, nodesThis, closedThis)) {
                return current;
            }
            open.poll();
        }
        return null;
    }

    private static Node pollActiveOpenNode(PriorityQueue<Node> open,
                                           Map<BlockPos, Node> nodesThis,
                                           Set<BlockPos> closedThis) {
        while (!open.isEmpty()) {
            Node current = open.poll();
            if (isActiveOpenNode(current, nodesThis, closedThis)) {
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

    private static final class Meet {
        final Node forward;
        final Node backward;
        Meet(Node forward, Node backward) {
            this.forward = forward; this.backward = backward;
        }
    }
}
