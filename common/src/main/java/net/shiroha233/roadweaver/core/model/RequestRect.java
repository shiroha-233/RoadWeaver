package net.shiroha233.roadweaver.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图快照请求/已加载区域的闭区间矩形。
 *
 * <p>协议层与视口控制器共用，故不放在 client 包下。</p>
 */
public record RequestRect(int minX, int minZ, int maxX, int maxZ) {
    public RequestRect {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid request rect");
        }
    }

    public boolean contains(RequestRect other) {
        return other != null
                && other.minX >= minX
                && other.maxX <= maxX
                && other.minZ >= minZ
                && other.maxZ <= maxZ;
    }

    public boolean intersects(RequestRect other) {
        return other != null
                && maxX >= other.minX
                && minX <= other.maxX
                && maxZ >= other.minZ
                && minZ <= other.maxZ;
    }

    public List<RequestRect> subtract(RequestRect cut) {
        if (cut == null || !intersects(cut)) {
            return List.of(this);
        }
        if (cut.contains(this)) {
            return List.of();
        }
        int intersectMinX = Math.max(minX, cut.minX);
        int intersectMinZ = Math.max(minZ, cut.minZ);
        int intersectMaxX = Math.min(maxX, cut.maxX);
        int intersectMaxZ = Math.min(maxZ, cut.maxZ);

        List<RequestRect> fragments = new ArrayList<>(4);
        if (minZ < intersectMinZ) {
            fragments.add(new RequestRect(minX, minZ, maxX, intersectMinZ - 1));
        }
        if (intersectMaxZ < maxZ) {
            fragments.add(new RequestRect(minX, intersectMaxZ + 1, maxX, maxZ));
        }
        if (minX < intersectMinX) {
            fragments.add(new RequestRect(minX, intersectMinZ, intersectMinX - 1, intersectMaxZ));
        }
        if (intersectMaxX < maxX) {
            fragments.add(new RequestRect(intersectMaxX + 1, intersectMinZ, maxX, intersectMaxZ));
        }
        return fragments;
    }
}
