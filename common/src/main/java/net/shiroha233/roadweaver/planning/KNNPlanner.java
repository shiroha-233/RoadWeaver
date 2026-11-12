package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.StructureConnection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 K-近邻 + Gabriel 图过滤 + 角度约束的路网拓扑规划器
 */
public final class KNNPlanner implements NetworkPlanner {

    @Override
    public List<StructureConnection> plan(List<BlockPos> points, int maxEdgeLenBlocks) {
        return planKNN(points,
                RoadConstants.DEFAULT_KNN_K,
                maxEdgeLenBlocks,
                RoadConstants.DEFAULT_KNN_ALPHA,
                RoadConstants.DEFAULT_KNN_MIN_ANGLE_DEG,
                RoadConstants.DEFAULT_KNN_DEGREE_CAP);
    }

    public static List<StructureConnection> planKNN(List<BlockPos> points, int k, int maxEdgeLenBlocks) {
        return planKNN(points, k, maxEdgeLenBlocks, 2.5, 25.0, 3);
    }

    public static List<StructureConnection> planKNN(List<BlockPos> points, int k, int maxEdgeLenBlocks,
                                                     double alpha, double minAngleDeg) {
        return planKNN(points, k, maxEdgeLenBlocks, alpha, minAngleDeg, 3);
    }

    public static List<StructureConnection> planKNN(List<BlockPos> points, int k, int maxEdgeLenBlocks,
                                                     double alpha, double minAngleDeg, int degreeCap) {
        if (points == null || points.size() < 2 || k <= 0) return List.of();
        final int n = points.size();
        final long maxDist2 = maxEdgeLenBlocks > 0 ? (long) maxEdgeLenBlocks * (long) maxEdgeLenBlocks : Long.MAX_VALUE;
        final double minCos = Math.cos(Math.toRadians(Math.max(0.0, Math.min(89.0, minAngleDeg))));
        final int degCap = Math.max(1, degreeCap);

        Set<Long> edgeKeys = new HashSet<>();
        List<StructureConnection> edges = new ArrayList<>();

        long[] nn2 = new long[n];
        for (int i = 0; i < n; i++) {
            long best = Long.MAX_VALUE;
            BlockPos a = points.get(i);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                long d2 = dist2(a, points.get(j));
                if (d2 < best) best = d2;
            }
            nn2[i] = best;
        }

        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            List<Neighbor> cand = new ArrayList<>();
            BlockPos a = points.get(i);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                BlockPos b = points.get(j);
                long d2 = dist2(a, b);
                if (d2 > maxDist2) continue;
                if (alpha <= 0 || (double) d2 <= (alpha * alpha) * (double) Math.max(1L, Math.max(nn2[i], nn2[j]))) {
                    cand.add(new Neighbor(j, d2));
                }
            }
            cand.sort((p, q) -> Long.compare(p.d2, q.d2));
            int limit = Math.min(k, cand.size());
            for (int t = 0; t < limit; t++) {
                int j = cand.get(t).idx;
                if (!gabrielOk(points, i, j, cand)) continue;
                if (adj.get(i).size() >= degCap || adj.get(j).size() >= degCap) continue;
                if (!angleOk(points, adj.get(i), i, j, minCos)) continue;
                if (!angleOk(points, adj.get(j), j, i, minCos)) {
                }
                int aIdx = Math.min(i, j);
                int bIdx = Math.max(i, j);
                long key = (((long) aIdx) << 32) ^ (long) bIdx;
                if (edgeKeys.add(key)) {
                    edges.add(new StructureConnection(points.get(aIdx), points.get(bIdx)));
                    adj.get(i).add(j);
                    adj.get(j).add(i);
                }
            }
        }
        return edges;
    }

    private static long dist2(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean angleOk(List<BlockPos> pts, List<Integer> neighbors, int i, int j, double minCos) {
        if (neighbors.isEmpty()) return true;
        BlockPos a = pts.get(i);
        BlockPos b = pts.get(j);
        long abx = (long) b.getX() - a.getX();
        long abz = (long) b.getZ() - a.getZ();
        double abLen = Math.hypot((double) abx, (double) abz);
        if (abLen == 0) return false;
        double abxN = abx / abLen;
        double abzN = abz / abLen;
        for (int nb : neighbors) {
            BlockPos c = pts.get(nb);
            long acx = (long) c.getX() - a.getX();
            long acz = (long) c.getZ() - a.getZ();
            double acLen = Math.hypot((double) acx, (double) acz);
            if (acLen == 0) continue;
            double acxN = acx / acLen;
            double aczN = acz / acLen;
            double cos = abxN * acxN + abzN * aczN;
            if (cos > minCos) return false;
        }
        return true;
    }

    private static boolean gabrielOk(List<BlockPos> pts, int i, int j, List<Neighbor> local) {
        BlockPos a = pts.get(i);
        BlockPos b = pts.get(j);
        long ab2 = dist2(a, b);
        for (Neighbor nb : local) {
            int k = nb.idx;
            if (k == i || k == j) continue;
            BlockPos c = pts.get(k);
            long s = dist2(a, c) + dist2(b, c);
            if (s <= ab2) return false;
        }
        return true;
    }

    private static final class Neighbor {
        final int idx;
        final long d2;
        Neighbor(int idx, long d2) { this.idx = idx; this.d2 = d2; }
    }

    public static List<StructureConnection> connectComponents(List<BlockPos> points,
                                                               List<StructureConnection> base,
                                                               int maxJoinLenBlocks,
                                                               double minAngleDeg,
                                                               int degreeCap) {
        int n = points != null ? points.size() : 0;
        if (n < 2) return List.of();
        long maxD2 = maxJoinLenBlocks > 0 ? (long) maxJoinLenBlocks * (long) maxJoinLenBlocks : Long.MAX_VALUE;
        double minCos = Math.cos(Math.toRadians(Math.max(0.0, Math.min(89.0, minAngleDeg))));

        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        java.util.HashMap<BlockPos, Integer> index = new java.util.HashMap<>(n * 2);
        for (int i = 0; i < n; i++) index.put(points.get(i), i);

        java.util.function.IntUnaryOperator find = new java.util.function.IntUnaryOperator() {
            @Override public int applyAsInt(int x) { while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; } return x; }
        };
        java.util.function.BiConsumer<Integer, Integer> unite = (a, b) -> {
            int ra = find.applyAsInt(a);
            int rb = find.applyAsInt(b);
            if (ra != rb) parent[rb] = ra;
        };

        Set<Long> keys = new HashSet<>();
        if (base != null) {
            for (StructureConnection c : base) {
                Integer ia = index.get(c.from());
                Integer ib = index.get(c.to());
                if (ia == null || ib == null) continue;
                int a = Math.min(ia, ib);
                int b = Math.max(ia, ib);
                long key = (((long) a) << 32) ^ (long) b;
                keys.add(key);
                unite.accept(ia, ib);
                adj.get(ia).add(ib);
                adj.get(ib).add(ia);
            }
        }

        class Pair { int a, b; long d2; Pair(int a, int b, long d2){this.a=a;this.b=b;this.d2=d2;} }
        List<Pair> cand = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (find.applyAsInt(i) == find.applyAsInt(j)) continue;
                long d2 = dist2(points.get(i), points.get(j));
                if (d2 <= maxD2) cand.add(new Pair(i, j, d2));
            }
        }
        cand.sort((p, q) -> Long.compare(p.d2, q.d2));

        List<StructureConnection> added = new ArrayList<>();
        for (Pair p : cand) {
            int ra = find.applyAsInt(p.a);
            int rb = find.applyAsInt(p.b);
            if (ra == rb) continue;
            if (adj.get(p.a).size() >= degreeCap || adj.get(p.b).size() >= degreeCap) continue;
            if (!angleOk(points, adj.get(p.a), p.a, p.b, minCos)) continue;
            if (!angleOk(points, adj.get(p.b), p.b, p.a, minCos)) {}
            int a = Math.min(p.a, p.b);
            int b = Math.max(p.a, p.b);
            long key = (((long) a) << 32) ^ (long) b;
            if (keys.add(key)) {
                added.add(new StructureConnection(points.get(a), points.get(b)));
                adj.get(p.a).add(p.b);
                adj.get(p.b).add(p.a);
                unite.accept(p.a, p.b);
            }
        }
        return added;
    }
}
