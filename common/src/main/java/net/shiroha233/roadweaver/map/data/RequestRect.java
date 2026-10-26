package net.shiroha233.roadweaver.map.data;

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
}
