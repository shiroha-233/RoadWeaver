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
import static net.shiroha233.roadweaver.pathfinding.impl.TerrainGradientHelper.*;

/**
 * 势场法 + 梯度流寻路器
 */
public final class PotentialFieldPathfinder implements Pathfinder {

    private static final int BIOME_BASE_COST = 12;
    private static final int SEARCH_BUFFER = 128;

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

        int manhattan = manhattan2d(start, end);
        int dynamicBuffer = Math.min(1024, Math.max(SEARCH_BUFFER, manhattan / 3));
        int minX = Math.min(start.getX(), end.getX()) - dynamicBuffer;
        int maxX = Math.max(start.getX(), end.getX()) + dynamicBuffer;
        int minZ = Math.min(start.getZ(), end.getZ()) - dynamicBuffer;
        int maxZ = Math.max(start.getZ(), end.getZ()) + dynamicBuffer;

        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, null, 0.0, heuristic(start, end, cfg));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int stepsBudget = Math.max(5000, maxSteps * RoadConstants.POTENTIAL_FIELD_STEPS_MULTIPLIER);
        ThrottleHelper.resetThrottle();
        try {
            while (stepsBudget > 0) {
                if (Thread.currentThread().isInterrupted()) return null;

                Node current = pollActiveOpenNode(openSet, allNodes, closed);
                if (current == null) break;

                stepsBudget--;
                ThrottleHelper.throttle(RoadConstants.DEFAULT_DUTY_CYCLE);
                if (Thread.currentThread().isInterrupted()) return null;

                if (manhattan2d(current.pos, end) < (int) (d * RoadConstants.POTENTIAL_FIELD_SUCCESS_DISTANCE_FACTOR)) {
                    return reconstructRawPath(current);
                }

                closed.add(current.pos);
                allNodes.remove(current.pos);

                double goalDx = end.getX() - current.pos.getX();
                double goalDz = end.getZ() - current.pos.getZ();
                double[] grad = terrainGradient(terrain, current.pos.getX(), current.pos.getZ(), d);
                double gradMag = gradientMagnitude(grad[0], grad[1]);
                double[] contour = contourDirection(grad[0], grad[1], goalDx, goalDz);

                for (int[] off : offsets) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    if (nxz.getX() < minX || nxz.getX() > maxX || nxz.getZ() < minZ || nxz.getZ() > maxZ) continue;
                    if (!terrain.contains(nxz.getX(), nxz.getZ())) continue;

                    int y = heightSampler(terrain, nxz.getX(), nxz.getZ());
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    if (closed.contains(np)) continue;

                    double moveCost = computeMoveCost(
                            current.pos, np, nxz, off, d,
                            grad, gradMag, contour,
                            terrain, cfg);

                    double tentativeG = current.g + moveCost;
                    Node existing = allNodes.get(np);
                    if (existing != null && tentativeG >= existing.g) continue;

                    double h = heuristic(np, end, cfg);
                    double f = tentativeG + h;
                    Node next = new Node(np, current, tentativeG, f);
                    allNodes.put(np, next);
                    openSet.add(next);
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

    private double computeMoveCost(BlockPos current, BlockPos np, BlockPos nxz,
                                   int[] off, int d,
                                   double[] grad, double gradMag, double[] contour,
                                   PathTerrainField terrain,
                                   PathfindingCostConfig cfg) {
        double moveX = off[0];
        double moveZ = off[1];
        int offsetSum = Math.abs(off[0]) + Math.abs(off[1]);
        boolean isDiag = (offsetSum == 2 * d);
        double horizDist = isDiag ? d * 1.414 : d;
        double baseCost = isDiag ? cfg.diagStepCost() : cfg.orthoStepCost();

        double alignment = contourAlignment(moveX, moveZ, contour[0], contour[1]);
        double contourDiscount = RoadConstants.DEFAULT_CONTOUR_DISCOUNT;
        double contourFactor = 1.0 - alignment * contourDiscount * Math.min(1.0, gradMag * 2.0);
        double adjustedBase = baseCost * Math.max(0.3, contourFactor);

        PathTerrainField.SampleBundle b = terrain.sampleBundle(np.getX(), np.getZ());
        int biomeCost = b.waterBiome() ? (BIOME_BASE_COST * 3) : 0;
        boolean waterColumn = b.columnWater();
        boolean nearWater = isNearWaterLike(terrain, nxz.getX(), nxz.getZ(), d);
        int waterDepth = b.waterDepth();

        int elevation = Math.abs(np.getY() - current.getY());
        double grade = computeGrade(current, np, horizDist);
        double gradePenalty = 0.0;
        if (grade > RoadConstants.DEFAULT_SOFT_GRADE_LIMIT) {
            gradePenalty += RoadConstants.DEFAULT_SOFT_GRADE_PENALTY
                    * (grade - RoadConstants.DEFAULT_SOFT_GRADE_LIMIT)
                    / RoadConstants.DEFAULT_SOFT_GRADE_LIMIT;
        }
        if (grade > RoadConstants.DEFAULT_HARD_GRADE_LIMIT) {
            gradePenalty += RoadConstants.DEFAULT_HARD_GRADE_PENALTY
                    * (grade - RoadConstants.DEFAULT_HARD_GRADE_LIMIT)
                    / RoadConstants.DEFAULT_HARD_GRADE_LIMIT;
        }

        double elevCost = elevation * (double) elevation * cfg.elevationWeight() * 0.5;

        double gradAlignPenalty = 0.0;
        if (gradMag > 1e-4) {
            double moveMag = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (moveMag > 1e-4) {
                double gAlign = Math.abs(moveX * grad[0] + moveZ * grad[1]) / (moveMag * gradMag);
                gradAlignPenalty = gAlign * gradMag * RoadConstants.DEFAULT_GRADIENT_ALIGN_PENALTY * d;
            }
        }

        int stabilityCost = calculateTerrainStability(terrain, np, np.getY(), d);

        double waterPenalty = 0.0;
        if (waterColumn) {
            waterPenalty = RoadConstants.DEFAULT_PF_WATER_BASE_PENALTY
                    + waterDepth * (double) waterDepth * cfg.waterDepthWeight() * 2.0;
        }
        double nearWaterPenalty = nearWater ? (cfg.nearWaterCost() * 3.0) : 0.0;

        return adjustedBase
                + gradePenalty
                + elevCost
                + gradAlignPenalty
                + stabilityCost * cfg.stabilityWeight()
                + biomeCost * cfg.biomeWeight()
                + waterPenalty
                + nearWaterPenalty;
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
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * cfg.heuristicWeight() * RoadConstants.POTENTIAL_FIELD_HEURISTIC_DAMPING;
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
