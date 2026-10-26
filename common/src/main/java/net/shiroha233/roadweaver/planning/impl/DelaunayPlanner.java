package net.shiroha233.roadweaver.planning.impl;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.NetworkPlanner;
import net.shiroha233.roadweaver.util.PlanningUtils;

import java.util.*;

/**
 * 基于 Delaunay 三角剖分的路网拓扑规划器
 */
public final class DelaunayPlanner implements NetworkPlanner {

    @Override
    public List<StructureConnection> plan(List<BlockPos> points, int maxEdgeLenBlocks) {
        return planDelaunay(points, maxEdgeLenBlocks);
    }

    public static List<StructureConnection> planDelaunay(List<BlockPos> points, int maxEdgeLenBlocks) {
        if (points == null || points.size() < 2) return List.of();
        ArrayList<BlockPos> unique = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (BlockPos p : points) {
            long k = PlanningUtils.pos2dKey(new BlockPos(p.getX(), 0, p.getZ()));
            if (seen.add(k)) unique.add(new BlockPos(p.getX(), 0, p.getZ()));
        }
        if (unique.size() < 2) return List.of();

        Bounds b = bounds(unique);
        double dx = b.maxX - b.minX;
        double dz = b.maxZ - b.minZ;
        double delta = Math.max(dx, dz);
        if (delta <= 0) delta = 1.0;
        double cx = (b.minX + b.maxX) * 0.5;
        double cz = (b.minZ + b.maxZ) * 0.5;
        Vertex v1 = new Vertex(cx - 2 * delta, cz - delta);
        Vertex v2 = new Vertex(cx, cz + 2 * delta);
        Vertex v3 = new Vertex(cx + 2 * delta, cz - delta);

        int n = unique.size();
        ArrayList<Vertex> verts = new ArrayList<>(n + 3);
        for (BlockPos p : unique) verts.add(new Vertex(p.getX(), p.getZ()));
        verts.add(v1); verts.add(v2); verts.add(v3);
        int s1 = n, s2 = n + 1, s3 = n + 2;

        ArrayList<Tri> tris = new ArrayList<>();
        tris.add(new Tri(s1, s2, s3));

        for (int i = 0; i < n; i++) {
            Vertex p = verts.get(i);
            ArrayList<Tri> bad = new ArrayList<>();
            for (Tri t : tris) {
                if (t.invalid) continue;
                if (inCircumcircle(verts, t, p)) bad.add(t);
            }
            ArrayList<Edge> polygon = new ArrayList<>();
            for (Tri t : bad) {
                t.invalid = true;
                addOrRemove(polygon, new Edge(t.a, t.b));
                addOrRemove(polygon, new Edge(t.b, t.c));
                addOrRemove(polygon, new Edge(t.c, t.a));
            }
            tris.removeIf(tr -> tr.invalid);
            for (Edge e : polygon) {
                tris.add(new Tri(e.u, e.v, i));
            }
        }

        tris.removeIf(t -> t.contains(s1) || t.contains(s2) || t.contains(s3));

        long maxD2 = maxEdgeLenBlocks > 0 ? (long) maxEdgeLenBlocks * (long) maxEdgeLenBlocks : Long.MAX_VALUE;
        HashSet<Long> edgeKeys = new HashSet<>();
        ArrayList<StructureConnection> out = new ArrayList<>();
        for (Tri t : tris) {
            addEdge(out, edgeKeys, unique, t.a, t.b, maxD2);
            addEdge(out, edgeKeys, unique, t.b, t.c, maxD2);
            addEdge(out, edgeKeys, unique, t.c, t.a, maxD2);
        }
        return out;
    }

    private static void addEdge(List<StructureConnection> out, Set<Long> keys, List<BlockPos> pts, int ia, int ib, long maxD2) {
        if (ia >= pts.size() || ib >= pts.size()) return;
        int a = Math.min(ia, ib), b = Math.max(ia, ib);
        long key = (((long) a) << 32) ^ (long) b;
        if (!keys.add(key)) return;
        BlockPos pa = pts.get(ia);
        BlockPos pb = pts.get(ib);
        long dx = (long) pa.getX() - pb.getX();
        long dz = (long) pa.getZ() - pb.getZ();
        long d2 = dx * dx + dz * dz;
        if (d2 > maxD2) return;
        out.add(new StructureConnection(pa, pb));
    }

    private static void addOrRemove(List<Edge> polygon, Edge e) {
        int idx = -1;
        for (int i = 0; i < polygon.size(); i++) {
            if (polygon.get(i).equals(e)) { idx = i; break; }
        }
        if (idx >= 0) polygon.remove(idx); else polygon.add(e);
    }

    private static boolean inCircumcircle(List<Vertex> verts, Tri t, Vertex p) {
        Vertex a = verts.get(t.a), b = verts.get(t.b), c = verts.get(t.c);
        double area2 = (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
        if (Math.abs(area2) < 1e-6) return false;
        double ax = a.x - p.x, ay = a.z - p.z;
        double bx = b.x - p.x, by = b.z - p.z;
        double cx = c.x - p.x, cy = c.z - p.z;
        double det = (ax * ax + ay * ay) * (bx * cy - by * cx)
                - (bx * bx + by * by) * (ax * cy - ay * cx)
                + (cx * cx + cy * cy) * (ax * by - ay * bx);
        if (area2 > 0) return det > 1e-6; else return det < -1e-6;
    }

    private static Bounds bounds(List<BlockPos> pts) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (BlockPos p : pts) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static final class Bounds {
        final double minX, minZ, maxX, maxZ;
        Bounds(double minX, double minZ, double maxX, double maxZ) { this.minX=minX; this.minZ=minZ; this.maxX=maxX; this.maxZ=maxZ; }
    }

    private static final class Vertex {
        final double x, z;
        Vertex(double x, double z) { this.x = x; this.z = z; }
    }

    private static final class Tri {
        final int a, b, c; boolean invalid;
        Tri(int a, int b, int c) { this.a = a; this.b = b; this.c = c; }
        boolean contains(int v) { return a == v || b == v || c == v; }
    }

    private static final class Edge {
        final int u, v;
        Edge(int a, int b) { if (a < b) { this.u = a; this.v = b; } else { this.u = b; this.v = a; } }
        @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof Edge e)) return false; return u == e.u && v == e.v; }
        @Override public int hashCode() { return (u * 73471) ^ v; }
    }
}
