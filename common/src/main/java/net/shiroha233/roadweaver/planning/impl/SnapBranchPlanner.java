package net.shiroha233.roadweaver.planning.impl;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.NetworkPlanner;
import net.shiroha233.roadweaver.util.PlanningUtils;

import java.util.*;

/**
 * 点线吸附三岔规划：采用逐点插入策略，将新点优先吸附到现有线段形成三岔
 */
public final class SnapBranchPlanner implements NetworkPlanner {

    private static final int FALLBACK_POINT_LIMIT = 700;
    private static final double MIN_PROJECTION_T = 0.10;
    private static final double MAX_PROJECTION_T = 0.90;
    private static final double MAX_SPLIT_STRETCH = 1.20;
    private static final double EDGE_ATTACH_BIAS = -6.0;

    @Override
    public List<StructureConnection> plan(List<BlockPos> points, int maxEdgeLenBlocks) {
        if (points == null || points.size() < 2) return List.of();
        List<BlockPos> unique = unique2d(points);
        if (unique.size() < 2) return List.of();
        if (unique.size() > FALLBACK_POINT_LIMIT) {
            return RNGPlanner.planRNG(unique, maxEdgeLenBlocks);
        }

        long maxD2 = maxEdgeLenBlocks > 0
                ? (long) maxEdgeLenBlocks * (long) maxEdgeLenBlocks
                : Long.MAX_VALUE;

        MutableGraph graph = new MutableGraph(unique);
        Pair seed = findNearestPair(unique, maxD2);
        if (seed == null) {
            return RNGPlanner.planRNG(unique, maxEdgeLenBlocks);
        }
        graph.addEdge(seed.a, seed.b);

        boolean[] connected = new boolean[unique.size()];
        connected[seed.a] = true;
        connected[seed.b] = true;
        int connectedCount = (seed.a == seed.b) ? 1 : 2;

        int guard = unique.size() * 3;
        while (connectedCount < unique.size() && guard-- > 0) {
            Insertion best = findBestInsertion(graph, unique.size(), connected, maxD2);
            if (best == null) break;
            applyInsertion(graph, best, maxD2);
            if (!connected[best.pointIndex]) {
                connected[best.pointIndex] = true;
                connectedCount++;
            }
        }

        if (connectedCount < unique.size()) {
            greedyConnectRemaining(graph, connected, maxD2);
        }

        List<StructureConnection> out = graph.toConnections();
        if (out.isEmpty()) {
            return RNGPlanner.planRNG(unique, maxEdgeLenBlocks);
        }
        return out;
    }

    private static Insertion findBestInsertion(MutableGraph graph,
                                               int baseCount,
                                               boolean[] connected,
                                               long maxD2) {
        Insertion best = null;
        for (int i = 0; i < baseCount; i++) {
            if (connected[i]) continue;
            Insertion cand = bestForPoint(graph, i, maxD2);
            if (cand == null) continue;
            if (best == null || cand.cost < best.cost
                    || (cand.cost == best.cost && cand.type == InsertionType.EDGE && best.type != InsertionType.EDGE)) {
                best = cand;
            }
        }
        return best;
    }

    private static Insertion bestForPoint(MutableGraph graph, int pointIndex, long maxD2) {
        BlockPos p = graph.vertex(pointIndex);
        Insertion best = null;

        int nearestNode = -1;
        long nearestNodeD2 = Long.MAX_VALUE;
        for (int v : graph.activeVertices()) {
            if (v == pointIndex) continue;
            long d2 = dist2(p, graph.vertex(v));
            if (d2 > maxD2) continue;
            if (d2 < nearestNodeD2) {
                nearestNodeD2 = d2;
                nearestNode = v;
            }
        }
        if (nearestNode >= 0) {
            best = new Insertion(InsertionType.NODE, pointIndex, nearestNode, -1, null, Math.sqrt(nearestNodeD2));
        }

        for (long ek : graph.edgeKeys()) {
            int a = (int) (ek >> 32);
            int b = (int) (ek & 0xffffffffL);
            if (a == pointIndex || b == pointIndex) continue;
            BlockPos pa = graph.vertex(a);
            BlockPos pb = graph.vertex(b);

            Projection pr = projectToSegment(p, pa, pb);
            if (pr == null) continue;
            if (pr.t <= MIN_PROJECTION_T || pr.t >= MAX_PROJECTION_T) continue;

            BlockPos j = new BlockPos((int) Math.round(pr.x), 0, (int) Math.round(pr.z));
            if (same2d(j, pa) || same2d(j, pb) || same2d(j, p)) continue;

            long pj2 = dist2(p, j);
            long aj2 = dist2(pa, j);
            long bj2 = dist2(pb, j);
            if (pj2 > maxD2 || aj2 > maxD2 || bj2 > maxD2) continue;

            double oldLen = Math.sqrt(dist2(pa, pb));
            if (oldLen < 1e-6) continue;
            double splitLen = Math.sqrt(aj2) + Math.sqrt(bj2);
            double stretch = splitLen / oldLen;
            if (stretch > MAX_SPLIT_STRETCH) continue;

            double delta = (splitLen - oldLen) + Math.sqrt(pj2) + EDGE_ATTACH_BIAS;
            Insertion cand = new Insertion(InsertionType.EDGE, pointIndex, a, b, j, delta);
            if (best == null || cand.cost < best.cost
                    || (cand.cost == best.cost && cand.type == InsertionType.EDGE && best.type != InsertionType.EDGE)) {
                best = cand;
            }
        }
        return best;
    }

    private static void applyInsertion(MutableGraph graph, Insertion insertion, long maxD2) {
        int p = insertion.pointIndex;
        if (insertion.type == InsertionType.NODE) {
            if (insertion.a >= 0 && dist2(graph.vertex(p), graph.vertex(insertion.a)) <= maxD2) {
                graph.addEdge(p, insertion.a);
            }
            return;
        }

        int a = insertion.a;
        int b = insertion.b;
        if (a < 0 || b < 0 || insertion.junction == null) {
            return;
        }
        if (!graph.hasEdge(a, b)) {
            if (dist2(graph.vertex(p), graph.vertex(a)) <= maxD2) graph.addEdge(p, a);
            return;
        }

        int j = graph.addVertex(insertion.junction);
        if (j == p || j == a || j == b) {
            int fallback = (dist2(graph.vertex(p), graph.vertex(a)) <= dist2(graph.vertex(p), graph.vertex(b))) ? a : b;
            if (dist2(graph.vertex(p), graph.vertex(fallback)) <= maxD2) graph.addEdge(p, fallback);
            return;
        }

        if (dist2(graph.vertex(p), graph.vertex(j)) > maxD2
                || dist2(graph.vertex(a), graph.vertex(j)) > maxD2
                || dist2(graph.vertex(b), graph.vertex(j)) > maxD2) {
            int fallback = (dist2(graph.vertex(p), graph.vertex(a)) <= dist2(graph.vertex(p), graph.vertex(b))) ? a : b;
            if (dist2(graph.vertex(p), graph.vertex(fallback)) <= maxD2) graph.addEdge(p, fallback);
            return;
        }

        graph.removeEdge(a, b);
        graph.addEdge(a, j);
        graph.addEdge(j, b);
        graph.addEdge(p, j);
    }

    private static void greedyConnectRemaining(MutableGraph graph, boolean[] connected, long maxD2) {
        for (int i = 0; i < connected.length; i++) {
            if (connected[i]) continue;
            int nearest = -1;
            long best = Long.MAX_VALUE;
            for (int v : graph.activeVertices()) {
                if (v == i) continue;
                long d2 = dist2(graph.vertex(i), graph.vertex(v));
                if (d2 > maxD2) continue;
                if (d2 < best) {
                    best = d2;
                    nearest = v;
                }
            }
            if (nearest >= 0) {
                graph.addEdge(i, nearest);
                connected[i] = true;
            }
        }
    }

    private static Pair findNearestPair(List<BlockPos> points, long maxD2) {
        Pair bestPair = null;
        long bestD2 = Long.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                long d2 = dist2(points.get(i), points.get(j));
                if (d2 > maxD2) continue;
                if (d2 < bestD2) {
                    bestD2 = d2;
                    bestPair = new Pair(i, j);
                }
            }
        }
        return bestPair;
    }

    private static Projection projectToSegment(BlockPos p, BlockPos a, BlockPos b) {
        double ax = a.getX();
        double az = a.getZ();
        double bx = b.getX();
        double bz = b.getZ();
        double px = p.getX();
        double pz = p.getZ();
        double vx = bx - ax;
        double vz = bz - az;
        double len2 = vx * vx + vz * vz;
        if (len2 <= 1e-9) return null;
        double t = ((px - ax) * vx + (pz - az) * vz) / len2;
        return new Projection(ax + t * vx, az + t * vz, t);
    }

    private static List<BlockPos> unique2d(List<BlockPos> points) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (BlockPos p : points) {
            if (p == null) continue;
            BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
            long k = PlanningUtils.pos2dKey(q);
            if (seen.add(k)) out.add(q);
        }
        return out;
    }

    private static long dist2(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean same2d(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ();
    }

    private record Projection(double x, double z, double t) {}

    private record Pair(int a, int b) {}

    private enum InsertionType {
        NODE,
        EDGE
    }

    private static final class Insertion {
        private final InsertionType type;
        private final int pointIndex;
        private final int a;
        private final int b;
        private final BlockPos junction;
        private final double cost;

        private Insertion(InsertionType type, int pointIndex, int a, int b, BlockPos junction, double cost) {
            this.type = type;
            this.pointIndex = pointIndex;
            this.a = a;
            this.b = b;
            this.junction = junction;
            this.cost = cost;
        }
    }

    private static final class MutableGraph {
        private final ArrayList<BlockPos> vertices = new ArrayList<>();
        private final ArrayList<HashSet<Integer>> adj = new ArrayList<>();
        private final HashSet<Long> edges = new HashSet<>();
        private final HashMap<Long, Integer> posToIndex = new HashMap<>();

        MutableGraph(List<BlockPos> points) {
            for (BlockPos p : points) addVertex(p);
        }

        BlockPos vertex(int i) {
            return vertices.get(i);
        }

        int addVertex(BlockPos p) {
            BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
            long key = PlanningUtils.pos2dKey(q);
            Integer old = posToIndex.get(key);
            if (old != null) return old;
            int idx = vertices.size();
            vertices.add(q);
            adj.add(new HashSet<>());
            posToIndex.put(key, idx);
            return idx;
        }

        void addEdge(int a, int b) {
            if (a == b) return;
            long k = edgeKey(a, b);
            if (!edges.add(k)) return;
            adj.get(a).add(b);
            adj.get(b).add(a);
        }

        void removeEdge(int a, int b) {
            if (a == b) return;
            long k = edgeKey(a, b);
            if (!edges.remove(k)) return;
            adj.get(a).remove(b);
            adj.get(b).remove(a);
        }

        boolean hasEdge(int a, int b) {
            return edges.contains(edgeKey(a, b));
        }

        Set<Long> edgeKeys() {
            return edges;
        }

        Set<Integer> activeVertices() {
            HashSet<Integer> out = new HashSet<>();
            for (int i = 0; i < adj.size(); i++) {
                if (!adj.get(i).isEmpty()) out.add(i);
            }
            return out;
        }

        List<StructureConnection> toConnections() {
            ArrayList<StructureConnection> out = new ArrayList<>(edges.size());
            for (long k : edges) {
                int a = (int) (k >> 32);
                int b = (int) (k & 0xffffffffL);
                out.add(new StructureConnection(vertices.get(a), vertices.get(b)));
            }
            return out;
        }

        private static long edgeKey(int a, int b) {
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            return (((long) lo) << 32) ^ (hi & 0xffffffffL);
        }
    }
}
