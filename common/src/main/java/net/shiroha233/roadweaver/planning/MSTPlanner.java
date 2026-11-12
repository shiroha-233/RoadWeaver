package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.util.PlanningUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 基于最小生成树（Kruskal）的路网规划器
 */
public final class MSTPlanner implements NetworkPlanner {

    @Override
    public List<StructureConnection> plan(List<BlockPos> points, int maxEdgeLenBlocks) {
        return planMST(points, maxEdgeLenBlocks);
    }

    public static List<StructureConnection> planMST(List<BlockPos> points, int maxEdgeLenBlocks) {
        if (points == null || points.size() < 2) return List.of();

        ArrayList<BlockPos> unique = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (BlockPos p : points) {
            BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
            long key = PlanningUtils.pos2dKey(q);
            if (seen.add(key)) unique.add(q);
        }
        int n = unique.size();
        if (n < 2) return List.of();

        long maxD2 = maxEdgeLenBlocks > 0 ? (long) maxEdgeLenBlocks * (long) maxEdgeLenBlocks : Long.MAX_VALUE;

        record Edge(int a, int b, long d2) {}

        ArrayList<Edge> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BlockPos pi = unique.get(i);
            long ix = pi.getX();
            long iz = pi.getZ();
            for (int j = i + 1; j < n; j++) {
                BlockPos pj = unique.get(j);
                long dx = ix - pj.getX();
                long dz = iz - pj.getZ();
                long d2 = dx * dx + dz * dz;
                if (d2 > maxD2) continue;
                edges.add(new Edge(i, j, d2));
            }
        }
        if (edges.isEmpty()) return List.of();

        edges.sort((e1, e2) -> Long.compare(e1.d2(), e2.d2()));

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        ArrayList<StructureConnection> result = new ArrayList<>();
        HashSet<Long> edgeKeys = new HashSet<>();

        for (Edge e : edges) {
            int ra = find(parent, e.a());
            int rb = find(parent, e.b());
            if (ra == rb) continue;
            parent[rb] = ra;

            int ia = Math.min(e.a(), e.b());
            int ib = Math.max(e.a(), e.b());
            long key = (((long) ia) << 32) ^ (long) ib;
            if (!edgeKeys.add(key)) continue;

            BlockPos pa = unique.get(e.a());
            BlockPos pb = unique.get(e.b());
            result.add(new StructureConnection(pa, pb));
        }

        return result;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }
}
