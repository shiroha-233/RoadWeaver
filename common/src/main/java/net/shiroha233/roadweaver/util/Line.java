package net.shiroha233.roadweaver.util;

import net.minecraft.world.phys.Vec3;

/**
 * 线段几何计算，提供最近点投影和 Frenet 标架
 */
public class Line {

    private final Vec3 start;
    private final Vec3 end;

    public Line(Vec3 start, Vec3 end) {
        this.start = start;
        this.end = end;
    }

    public static class Frame {
        public Vec3 closestPoint;
        public Vec3 tangent;
        public double globalT;
        public Vec3 tangent0;
        public Vec3 normal0;
        public Vec3 binormal0;

        @Override
        public String toString() {
            return String.format("Closet: %s\nTangent: %s\nGlobal t: %.4f", closestPoint, tangent, globalT);
        }
    }

    public Frame getFrame(Vec3 testPoint) {
        Vec3 direction = end.subtract(start);

        if (direction.length() == 0) {
            Frame res = new Frame();
            res.closestPoint = start;
            res.tangent = new Vec3(0, 0, 0);
            res.globalT = 0;
            res.tangent0 = new Vec3(0, 0, 0);
            res.normal0 = new Vec3(0, 1, 0);
            res.binormal0 = new Vec3(0, 0, 0);
            return res;
        }

        Vec3 tangent = direction.normalize();
        Vec3 lineToTest = testPoint.subtract(start);
        double t = lineToTest.dot(tangent);

        Vec3 closestPoint;
        if (t <= 0) {
            closestPoint = start;
        } else if (t >= direction.length()) {
            closestPoint = end;
        } else {
            closestPoint = start.add(tangent.scale(t));
        }

        Frame res = new Frame();
        res.closestPoint = closestPoint;
        res.tangent = tangent;
        res.globalT = t / direction.length();
        res.tangent0 = new Vec3(res.tangent.x, 0, res.tangent.z).normalize();
        res.normal0 = new Vec3(0, 1, 0);
        res.binormal0 = res.tangent0.cross(res.normal0).normalize();

        return res;
    }

    public double getTotalLength() {
        return start.distanceTo(end);
    }
}
