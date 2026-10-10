/* 文件职责：对道路路径执行精确高度修正、平滑与桥梁后处理。 */
package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccuratePathHeightResolver;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.CurveMode;
import net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.Vec2d;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.*;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.isWaterLike;
import static net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.*;

/**
 * 路径后处理器
 */
public final class PathPostProcessor {
    private PathPostProcessor() {}

    /** Maximum lateral allowance for normal spline smoothing before terrain checks reject it. */
    private static final double MAX_SPLINE_OFFSET = 16.0;
    private static final double MAX_CONTROL_SPAN = 64.0;
    private static final double CONTROL_DEVIATION = 4.0;

    public static List<RoadSegmentPlacement> process(List<BlockPos> rawPath, int width,
                                                     ServerLevel level, TerrainSamplingCache cache,
                                                     int bridgeMinWaterDepth) {
        return process(rawPath, width, level, cache, bridgeMinWaterDepth, CurveMode.CATMULL_ROM, null);
    }

    public static List<RoadSegmentPlacement> process(List<BlockPos> rawPath, int width,
                                                     ServerLevel level, TerrainSamplingCache cache,
                                                     int bridgeMinWaterDepth,
                                                     AccurateHeightSampler accurate) {
        return process(rawPath, width, level, cache, null, bridgeMinWaterDepth, CurveMode.CATMULL_ROM, accurate);
    }

    public static List<RoadSegmentPlacement> process(List<BlockPos> rawPath, int width,
                                                     ServerLevel level, TerrainSamplingCache cache,
                                                     int bridgeMinWaterDepth,
                                                     CurveMode curveMode,
                                                     AccurateHeightSampler accurate) {
        return process(rawPath, width, level, cache, null, bridgeMinWaterDepth, curveMode, accurate);
    }

    public static List<RoadSegmentPlacement> process(List<BlockPos> rawPath, int width,
                                                     ServerLevel level, TerrainSamplingCache cache,
                                                     PathTerrainField terrain,
                                                     int bridgeMinWaterDepth,
                                                     CurveMode curveMode,
                                                     AccurateHeightSampler accurate) {
        if (rawPath == null || rawPath.size() < 2) return new ArrayList<>();

        int[] rawTargetY = new int[rawPath.size()];
        for (int i = 0; i < rawPath.size(); i++) {
            rawTargetY[i] = rawPath.get(i).getY();
        }

        AccurateHeightSampler sampler = accurate != null
                ? accurate
                : (cache != null ? cache.getAccurateSampler(level) : AccurateHeightSampler.create(level));

        boolean[] bridgeMask = detectBridgeMask(rawPath, level, cache, terrain, bridgeMinWaterDepth, sampler);
        List<BlockPos> reference;
        boolean[] controlBridgeMask;
        List<Integer> indices = boundedControlIndices(rawPath, bridgeMask, MAX_CONTROL_SPAN);
        reference = new ArrayList<>(indices.size());
        controlBridgeMask = new boolean[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            reference.add(rawPath.get(indices.get(i)));
            controlBridgeMask[i] = bridgeMask[indices.get(i)];
        }
        List<BlockPos> controlPoints = relaxPathSkippingBridge(reference, controlBridgeMask);
        if (controlPoints.size() < 2) return new ArrayList<>();

        List<Vec2d> splinePoints = generateSplinePoints(controlPoints, controlBridgeMask, curveMode, reference, sampler, level.getSeaLevel());
        List<Double> centerDists = new ArrayList<>();
        List<BlockPos> centers = extractCenters(splinePoints, rawPath, rawTargetY, centerDists);
        centers = AccuratePathHeightResolver.resolve(centers, null, sampler);
        return rasterizeSegments(splinePoints, centers, centerDists, width);
    }

    private static List<Vec2d> generateSplinePoints(List<BlockPos> controlPoints,
                                                      boolean[] bridgeMask, CurveMode curveMode, List<BlockPos> reference,
                                                      AccurateHeightSampler sampler, int seaLevel) {
        List<BlockPos> ext = new ArrayList<>();
        ext.add(controlPoints.get(0));
        ext.addAll(controlPoints);
        ext.add(controlPoints.get(controlPoints.size() - 1));

        List<Vec2d> splinePoints = new ArrayList<>();
        List<Vec2d> baseline = new ArrayList<>();
        List<Integer> segmentIndices = new ArrayList<>();
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            BlockPos p0 = ext.get(i), p1 = ext.get(i + 1), p2 = ext.get(i + 2), p3 = ext.get(i + 3);
            double dist = Math.sqrt(p1.distSqr(p2));
            int steps = Math.max(1, (int) Math.ceil(dist * 4.0));
            boolean bridgeSeg = (i < bridgeMask.length - 1) && (bridgeMask[i] || bridgeMask[i + 1]);

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                Vec2d pt;
                if (bridgeSeg) {
                    pt = lerp2d(p1.getX(), p1.getZ(), p2.getX(), p2.getZ(), t);
                } else if (curveMode == CurveMode.BEZIER_CASTELJAU) {
                    pt = catmullRomBezierCasteljau(p0.getX(), p0.getZ(), p1.getX(), p1.getZ(),
                            p2.getX(), p2.getZ(), p3.getX(), p3.getZ(), t);
                } else {
                    pt = catmullRomSpline(p0.getX(), p0.getZ(), p1.getX(), p1.getZ(),
                            p2.getX(), p2.getZ(), p3.getX(), p3.getZ(), t);
                }
                BlockPos a = reference.get(i), b = reference.get(i + 1);
                splinePoints.add(boundToSegment(pt, a, b, MAX_SPLINE_OFFSET));
                baseline.add(lerp2d(a.getX(), a.getZ(), b.getX(), b.getZ(), t));
                segmentIndices.add(i);
            }
        }
        BlockPos last = controlPoints.get(controlPoints.size() - 1);
        splinePoints.add(new Vec2d(last.getX(), last.getZ()));
        baseline.add(new Vec2d(last.getX(), last.getZ()));
        segmentIndices.add(controlPoints.size() - 2);
        // Reject the entire local curve if smoothing introduces a new water crossing or
        // moves it onto substantially different terrain. Heights are sampled at the new X/Z,
        // not guessed from the old search nodes.
        List<BlockPos> checks = new ArrayList<>();
        for (int i = 0; i < splinePoints.size(); i++) {
            BlockPos candidate = rounded(splinePoints.get(i)), original = rounded(baseline.get(i));
            if (!candidate.equals(original)) { checks.add(candidate); checks.add(original); }
        }
        Map<Long, AccurateHeightSample> samples = sampler.samplePositions(checks);
        boolean[] rejected = new boolean[controlPoints.size() - 1];
        for (int i = 0; i < splinePoints.size(); i++) {
            BlockPos candidate = rounded(splinePoints.get(i)), original = rounded(baseline.get(i));
            if (candidate.equals(original)) continue;
            AccurateHeightSample c = samples.get(AccurateHeightSample.key(candidate.getX(), candidate.getZ()));
            AccurateHeightSample o = samples.get(AccurateHeightSample.key(original.getX(), original.getZ()));
            if (c == null || o == null || Math.abs(sampler.surfaceHeight(c) - sampler.surfaceHeight(o)) > 8
                    || (c.oceanFloorWg() < c.worldSurfaceWg() && o.oceanFloorWg() >= o.worldSurfaceWg())
                    || (c.oceanFloorWg() < seaLevel && o.oceanFloorWg() >= seaLevel)) {
                rejected[segmentIndices.get(i)] = true;
            }
        }
        for (int i = 0; i < splinePoints.size(); i++) {
            if (rejected[segmentIndices.get(i)]) splinePoints.set(i, baseline.get(i));
        }
        // Anchor both ends of rejected curves so adjacent curves cannot leave small gaps.
        for (int i = 0; i < splinePoints.size(); i++) {
            int segment = segmentIndices.get(i);
            if (i == 0 || segmentIndices.get(i - 1) != segment) {
                if (rejected[segment] || (segment > 0 && rejected[segment - 1])) {
                    splinePoints.set(i, baseline.get(i));
                }
            }
        }
        return splinePoints;
    }

    private static BlockPos rounded(Vec2d point) {
        return new BlockPos((int) Math.round(point.x()), 0, (int) Math.round(point.z()));
    }

    static List<Integer> boundedControlIndices(List<BlockPos> path, boolean[] bridgeMask, double maxSpan) {
        List<Integer> indices = new ArrayList<>();
        indices.add(0);
        int anchor = 0;
        while (anchor < path.size() - 1) {
            int end = anchor + 1;
            while (end + 1 < path.size()) {
                int candidate = end + 1;
                if (path.get(anchor).distSqr(path.get(candidate)) > maxSpan * maxSpan
                        || bridgeMask[candidate] != bridgeMask[anchor]
                        || bridgeMask[end] != bridgeMask[anchor]) break;
                boolean fits = true;
                Vec2d a = new Vec2d(path.get(anchor).getX(), path.get(anchor).getZ());
                Vec2d b = new Vec2d(path.get(candidate).getX(), path.get(candidate).getZ());
                for (int k = anchor + 1; k < candidate; k++) {
                    if (distToSegmentSq(path.get(k).getX(), path.get(k).getZ(), a, b)
                            > CONTROL_DEVIATION * CONTROL_DEVIATION) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) break;
                end = candidate;
            }
            // Include the last bank node and the first water node as distinct anchors.
            indices.add(end);
            anchor = end;
        }
        return indices;
    }

    /** A local corridor bound, not a nearest-point search that can jump to another path branch. */
    static Vec2d boundToSegment(Vec2d point, BlockPos a, BlockPos b, double maxOffset) {
        double dx = (double) b.getX() - a.getX(), dz = (double) b.getZ() - a.getZ();
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1,
                ((point.x() - a.getX()) * dx + (point.z() - a.getZ()) * dz) / lengthSq));
        double px = a.getX() + dx * t, pz = a.getZ() + dz * t;
        double ox = point.x() - px, oz = point.z() - pz;
        double distance = Math.hypot(ox, oz);
        return distance <= maxOffset ? point : new Vec2d(px + ox * maxOffset / distance, pz + oz * maxOffset / distance);
    }

    private static BlockPos boundedRelaxation(BlockPos prev, BlockPos curr, BlockPos next) {
        double x = ((double) prev.getX() + curr.getX() * 2.0 + next.getX()) / 4;
        double z = ((double) prev.getZ() + curr.getZ() * 2.0 + next.getZ()) / 4;
        double distance = Math.hypot(x - curr.getX(), z - curr.getZ());
        double scale = distance > 1.0 ? 1.0 / distance : 1.0;
        return new BlockPos((int) Math.round(curr.getX() + (x - curr.getX()) * scale), curr.getY(),
                (int) Math.round(curr.getZ() + (z - curr.getZ()) * scale));
    }

    private static List<BlockPos> extractCenters(List<Vec2d> splinePoints,
                                                  List<BlockPos> rawPath, int[] rawTargetY,
                                                  List<Double> outCenterDists) {
        List<BlockPos> centers = new ArrayList<>();
        double currentDist = 0;
        double nextCenterDist = 0;

        for (int i = 0; i < splinePoints.size(); i++) {
            Vec2d p = splinePoints.get(i);
            if (i > 0) currentDist += p.dist(splinePoints.get(i - 1));

            if (currentDist >= nextCenterDist || i == splinePoints.size() - 1) {
                int cx = (int) Math.round(p.x());
                int cz = (int) Math.round(p.z());
                int y = interpolateY(cx, cz, rawPath, rawTargetY);
                BlockPos centerPos = new BlockPos(cx, y, cz);
                if (centers.isEmpty() || !centers.get(centers.size() - 1).equals(centerPos)) {
                    centers.add(centerPos);
                    outCenterDists.add(currentDist);
                    nextCenterDist = currentDist + 1.0;
                }
            }
        }
        return centers;
    }

    private static int interpolateY(int cx, int cz, List<BlockPos> rawPath, int[] rawTargetY) {
        double bestDistSq = Double.MAX_VALUE;
        double bestT = 0;
        int bestSeg = 0;

        for (int i = 0; i < rawPath.size() - 1; i++) {
            BlockPos a = rawPath.get(i);
            BlockPos b = rawPath.get(i + 1);
            double ax = a.getX(), az = a.getZ();
            double dx = b.getX() - ax, dz = b.getZ() - az;
            double lenSq = dx * dx + dz * dz;
            double t = (lenSq < 1e-9) ? 0.0
                    : Math.max(0.0, Math.min(1.0, ((cx - ax) * dx + (cz - az) * dz) / lenSq));
            double projX = ax + t * dx, projZ = az + t * dz;
            double dSq = (cx - projX) * (cx - projX) + (cz - projZ) * (cz - projZ);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestT = t;
                bestSeg = i;
            }
        }
        int y0 = rawTargetY[bestSeg];
        int y1 = rawTargetY[Math.min(bestSeg + 1, rawTargetY.length - 1)];
        return (int) Math.round(y0 + (y1 - y0) * bestT);
    }

    private static List<RoadSegmentPlacement> rasterizeSegments(List<Vec2d> splinePoints,
                                                                 List<BlockPos> centers,
                                                                 List<Double> centerDists,
                                                                 int width) {
        Map<Integer, Set<BlockPos>> segmentedBlocks = new HashMap<>();
        for (int i = 0; i < centers.size(); i++) segmentedBlocks.put(i, new HashSet<>());

        double halfWidth = width / 2.0;
        double halfWidthSq = halfWidth * halfWidth;
        double pathDist = 0;
        int currentCenterIdx = 0;

        for (int i = 0; i < splinePoints.size() - 1; i++) {
            Vec2d pStart = splinePoints.get(i), pEnd = splinePoints.get(i + 1);
            pathDist += pStart.dist(pEnd);
            while (currentCenterIdx < centerDists.size() - 1 && centerDists.get(currentCenterIdx + 1) < pathDist) {
                currentCenterIdx++;
            }

            int minX = (int) Math.floor(Math.min(pStart.x(), pEnd.x()) - halfWidth - 1);
            int maxX = (int) Math.ceil(Math.max(pStart.x(), pEnd.x()) + halfWidth + 1);
            int minZ = (int) Math.floor(Math.min(pStart.z(), pEnd.z()) - halfWidth - 1);
            int maxZ = (int) Math.ceil(Math.max(pStart.z(), pEnd.z()) + halfWidth + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (distToSegmentSq(x, z, pStart, pEnd) <= halfWidthSq) {
                        assignToNearestCenter(x, z, centers, currentCenterIdx, segmentedBlocks);
                    }
                }
            }
        }

        List<RoadSegmentPlacement> out = new ArrayList<>();
        for (int i = 0; i < centers.size(); i++) {
            BlockPos center = centers.get(i);
            Set<BlockPos> blocks = segmentedBlocks.get(i);
            if (blocks.isEmpty()) blocks.add(new BlockPos(center.getX(), 0, center.getZ()));
            out.add(new RoadSegmentPlacement(center, new ArrayList<>(blocks)));
        }
        return out;
    }

    private static void assignToNearestCenter(int x, int z, List<BlockPos> centers,
                                               int hint, Map<Integer, Set<BlockPos>> segmentedBlocks) {
        int bestIdx = hint;
        double bestDistSq = Double.MAX_VALUE;
        int searchStart = Math.max(0, hint - 20);
        int searchEnd = Math.min(centers.size() - 2, hint + 20);

        for (int seg = searchStart; seg <= searchEnd; seg++) {
            BlockPos a = centers.get(seg), b = centers.get(seg + 1);
            double ax = a.getX(), az = a.getZ(), dx = b.getX() - ax, dz = b.getZ() - az;
            double lenSq = dx * dx + dz * dz;
            double t = (lenSq < 1e-9) ? 0.0
                    : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / lenSq));
            double projX = ax + t * dx, projZ = az + t * dz;
            double dSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = (t < 0.5) ? seg : Math.min(seg + 1, centers.size() - 1);
            }
        }
        segmentedBlocks.get(bestIdx).add(new BlockPos(x, 0, z));
    }

    static List<BlockPos> simplifyPath(List<BlockPos> nodes) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);
        List<BlockPos> simplified = new ArrayList<>();
        simplified.add(nodes.get(0));
        for (int i = 1; i < nodes.size() - 1; i++) {
            BlockPos prev = simplified.get(simplified.size() - 1);
            BlockPos curr = nodes.get(i);
            BlockPos next = nodes.get(i + 1);
            long dx1 = (long) curr.getX() - prev.getX(), dz1 = (long) curr.getZ() - prev.getZ();
            long dx2 = (long) next.getX() - curr.getX(), dz2 = (long) next.getZ() - curr.getZ();
            if (Math.abs(dx1 * dz2 - dz1 * dx2) > 16) simplified.add(curr);
        }
        simplified.add(nodes.get(nodes.size() - 1));
        return simplified;
    }

    static boolean[] detectBridgeMask(List<BlockPos> nodes, ServerLevel level,
                                      TerrainSamplingCache cache, PathTerrainField terrain,
                                      int bridgeMinWaterDepth,
                                      AccurateHeightSampler sampler) {
        int n = nodes.size();
        boolean[] mask = new boolean[n];
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos node : nodes) {
            if (terrain == null || !terrain.hasAccurateSample(node.getX(), node.getZ())) {
                missing.add(node);
            }
        }
        Map<Long, AccurateHeightSample> samples = sampler.samplePositions(missing);
        for (int i = 0; i < n; i++) {
            BlockPos p = nodes.get(i);
            if (terrain != null && terrain.hasAccurateSample(p.getX(), p.getZ())) {
                mask[i] = terrain.isBridgeWater(p.getX(), p.getZ(), bridgeMinWaterDepth);
                continue;
            }
            int sea = level.getSeaLevel();
            AccurateHeightSample sample = samples.get(AccurateHeightSample.key(p.getX(), p.getZ()));
            int oceanFloor = sample != null ? sample.oceanFloorWg() : sampler.oceanFloorWg(p.getX(), p.getZ());
            int surfaceY = sample != null ? sample.worldSurfaceWg() : sampler.worldSurfaceWg(p.getX(), p.getZ());
            boolean biomeWater = isWaterLike(cache, p.getX(), p.getZ(), level) && oceanFloor < sea;
            boolean heightWater = (surfaceY <= sea + 1) && (oceanFloor < surfaceY - 1);
            int waterDepth = (biomeWater || heightWater) ? Math.max(0, sea - oceanFloor) : 0;
            mask[i] = waterDepth >= Math.max(1, bridgeMinWaterDepth);
        }
        return mask;
    }

    static List<BlockPos> straightenBridgeRuns(List<BlockPos> nodes, boolean[] bridgeMask) {
        if (nodes == null || nodes.size() < 3 || bridgeMask == null || bridgeMask.length != nodes.size()) {
            return nodes;
        }
        int n = nodes.size();
        List<BlockPos> out = new ArrayList<>(nodes);
        int i = 0;
        while (i < n) {
            if (!bridgeMask[i]) { i++; continue; }
            int runStart = i;
            while (i < n && bridgeMask[i]) i++;
            int runEnd = i - 1;
            int entryIdx = Math.max(0, runStart - 1);
            int exitIdx = Math.min(n - 1, runEnd + 1);
            if (exitIdx <= entryIdx) continue;
            BlockPos a = nodes.get(entryIdx), b = nodes.get(exitIdx);
            int ax = a.getX(), az = a.getZ(), dx = b.getX() - ax, dz = b.getZ() - az;
            for (int k = runStart; k <= runEnd; k++) {
                double t = Math.max(0.0, Math.min(1.0, (k - entryIdx) / (double) (exitIdx - entryIdx)));
                out.set(k, new BlockPos((int) Math.round(ax + dx * t), out.get(k).getY(),
                        (int) Math.round(az + dz * t)));
            }
        }
        return out;
    }

    static List<BlockPos> relaxPath(List<BlockPos> nodes) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);
        List<BlockPos> relaxed = new ArrayList<>();
        relaxed.add(nodes.get(0));
        for (int i = 1; i < nodes.size() - 1; i++) {
            BlockPos prev = nodes.get(i - 1), curr = nodes.get(i), next = nodes.get(i + 1);
            relaxed.add(boundedRelaxation(prev, curr, next));
        }
        relaxed.add(nodes.get(nodes.size() - 1));
        return relaxed;
    }

    static List<BlockPos> relaxPathSkippingBridge(List<BlockPos> nodes, boolean[] bridgeMask) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);
        boolean hasBridge = false;
        if (bridgeMask != null) {
            for (boolean b : bridgeMask) { if (b) { hasBridge = true; break; } }
        }
        if (!hasBridge) return relaxPath(nodes);

        List<BlockPos> relaxed = new ArrayList<>();
        relaxed.add(nodes.get(0));
        for (int i = 1; i < nodes.size() - 1; i++) {
            boolean isBridge = i < bridgeMask.length && bridgeMask[i];
            boolean nearBridge = (i - 1 >= 0 && i - 1 < bridgeMask.length && bridgeMask[i - 1])
                    || (i + 1 < bridgeMask.length && bridgeMask[i + 1]);
            if (isBridge || nearBridge) {
                relaxed.add(nodes.get(i));
                continue;
            }
            BlockPos prev = nodes.get(i - 1), curr = nodes.get(i), next = nodes.get(i + 1);
            relaxed.add(boundedRelaxation(prev, curr, next));
        }
        relaxed.add(nodes.get(nodes.size() - 1));
        return relaxed;
    }
}
