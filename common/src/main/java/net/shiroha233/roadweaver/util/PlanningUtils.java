package net.shiroha233.roadweaver.util;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.StructureConnection;

/**
 * 路径规划相关的工具方法集合
 */
public final class PlanningUtils {
    private PlanningUtils() {}

    public static long pos2dKey(BlockPos p) {
        long x = p.getX();
        long z = p.getZ();
        return (x << 32) ^ (z & 0xffffffffL);
    }

    public static BlockPos posFrom2dKey(long key) {
        return new BlockPos((int) (key >> 32), 0, (int) key);
    }

    public static long edgeKey(BlockPos a, BlockPos b) {
        long ka = pos2dKey(a);
        long kb = pos2dKey(b);
        return edgeKey(ka, kb);
    }

    public static long edgeKey(long ka, long kb) {
        long lo = Math.min(ka, kb);
        long hi = Math.max(ka, kb);
        return (hi << 1) ^ lo;
    }

    public static boolean sameEdge(StructureConnection a, StructureConnection b) {
        BlockPos af = a.from(), at = a.to();
        BlockPos bf = b.from(), bt = b.to();
        return (af.equals(bf) && at.equals(bt)) || (af.equals(bt) && at.equals(bf));
    }

    public static boolean sameEdge(StructureConnection c, BlockPos a, BlockPos b) {
        BlockPos cf = c.from();
        BlockPos ct = c.to();
        return (cf.equals(a) && ct.equals(b)) || (cf.equals(b) && ct.equals(a));
    }
}
