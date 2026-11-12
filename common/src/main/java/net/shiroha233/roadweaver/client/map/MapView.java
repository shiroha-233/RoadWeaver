package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.shiroha233.roadweaver.map.data.MapSnapshot;

/**
 * 地图视图管理
 */
public final class MapView {
    private double minX, maxX, minZ, maxZ;

    public void resetFromSnapshot(MapSnapshot snapshot) {
        this.minX = snapshot.minX();
        this.maxX = snapshot.maxX();
        this.minZ = snapshot.minZ();
        this.maxZ = snapshot.maxZ();
        if (maxX - minX < 1) maxX = minX + 1;
        if (maxZ - minZ < 1) maxZ = minZ + 1;
    }

    public void calibrateInitialToPlayer(Minecraft mc, int contentW, int contentH, int gridTargetPx) {
        if (mc == null || mc.player == null) return;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double desiredBlocksPerCell = 64.0;
        double desiredPxPerBlock = gridTargetPx / desiredBlocksPerCell;
        if (desiredPxPerBlock <= 0) return;
        double rangeX = contentW / desiredPxPerBlock;
        double rangeZ = contentH / desiredPxPerBlock;
        this.minX = px - rangeX * 0.5;
        this.maxX = px + rangeX * 0.5;
        this.minZ = pz - rangeZ * 0.5;
        this.maxZ = pz + rangeZ * 0.5;
        lockAspect(contentW, contentH);
        clampZoom(contentW, contentH, gridTargetPx);
    }

    public int toScreenX(int blockX, int mapX, int innerPad, int contentW) {
        double rangeX = Math.max(1.0, maxX - minX);
        double nx = (blockX - minX) / rangeX;
        return mapX + innerPad + (int) Math.round(nx * contentW);
    }

    public int toScreenY(int blockZ, int mapY, int innerPad, int contentH) {
        double rangeZ = Math.max(1.0, maxZ - minZ);
        double nz = (blockZ - minZ) / rangeZ;
        return mapY + innerPad + (int) Math.round(nz * contentH);
    }

    public double screenToWorldX(double sx, int mapX, int innerPad, int contentW) {
        double nx = (sx - (mapX + innerPad)) / Math.max(1.0, contentW);
        return minX + nx * Math.max(1.0, maxX - minX);
    }

    public double screenToWorldZ(double sy, int mapY, int innerPad, int contentH) {
        double ny = (sy - (mapY + innerPad)) / Math.max(1.0, contentH);
        return minZ + ny * Math.max(1.0, maxZ - minZ);
    }

    public boolean isInViewWorld(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean segmentInViewWorld(int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        if (maxX < this.minX) return false;
        if (minX > this.maxX) return false;
        if (maxZ < this.minZ) return false;
        if (minZ > this.maxZ) return false;
        return true;
    }

    public double pxPerBlockX(int contentW) {
        return contentW / Math.max(1.0, (maxX - minX));
    }

    public double pxPerBlockZ(int contentH) {
        return contentH / Math.max(1.0, (maxZ - minZ));
    }

    public void lockAspect(int contentW, int contentH) {
        if (contentW <= 0 || contentH <= 0) return;
        double aspect = contentW / (double) contentH;
        double rx = Math.max(1.0, maxX - minX);
        double rz = Math.max(1.0, maxZ - minZ);
        double r = rx / rz;
        if (Math.abs(r - aspect) < 1e-6) return;
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        if (r > aspect) {
            double nrz = rx / aspect;
            minZ = cz - nrz * 0.5;
            maxZ = cz + nrz * 0.5;
        } else {
            double nrx = rz * aspect;
            minX = cx - nrx * 0.5;
            maxX = cx + nrx * 0.5;
        }
    }

    public void clampZoom(int contentW, int contentH, int gridTargetPx) {
        if (contentW <= 0 || contentH <= 0) return;
        double minPpb = gridTargetPx / (512.0 * 16.0);
        double maxPpb = gridTargetPx / 2.0;
        double rx = Math.max(1.0, maxX - minX);
        double rz = Math.max(1.0, maxZ - minZ);
        double ppbX = contentW / rx;
        double ppbZ = contentH / rz;
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        boolean changed = false;
        if (ppbX > maxPpb) { rx = contentW / maxPpb; changed = true; }
        else if (ppbX < minPpb) { rx = contentW / minPpb; changed = true; }
        if (ppbZ > maxPpb) { rz = contentH / maxPpb; changed = true; }
        else if (ppbZ < minPpb) { rz = contentH / minPpb; changed = true; }
        if (changed) {
            minX = cx - rx * 0.5; maxX = cx + rx * 0.5;
            minZ = cz - rz * 0.5; maxZ = cz + rz * 0.5;
            lockAspect(contentW, contentH);
        }
    }

    public void applyZoomAround(double cx, double cz, double factor, int contentW, int contentH, int gridTargetPx) {
        double rx = maxX - minX;
        double rz = maxZ - minZ;
        double nrx = Math.max(1.0, rx * factor);
        double nrz = Math.max(1.0, rz * factor);
        double ax = (cx - minX) / rx;
        double az = (cz - minZ) / rz;
        minX = cx - ax * nrx;
        maxX = minX + nrx;
        minZ = cz - az * nrz;
        maxZ = minZ + nrz;
        lockAspect(contentW, contentH);
        clampZoom(contentW, contentH, gridTargetPx);
    }

    public void panByScreenDelta(double dx, double dy, int contentW, int contentH) {
        double rx = maxX - minX;
        double rz = maxZ - minZ;
        double wx = -dx / Math.max(1.0, contentW) * rx;
        double wz = -dy / Math.max(1.0, contentH) * rz;
        minX += wx; maxX += wx;
        minZ += wz; maxZ += wz;
    }

    public void centerOn(double worldX, double worldZ, int contentW, int contentH) {
        double rx = maxX - minX;
        double rz = maxZ - minZ;
        minX = worldX - rx * 0.5;
        maxX = worldX + rx * 0.5;
        minZ = worldZ - rz * 0.5;
        maxZ = worldZ + rz * 0.5;
        lockAspect(contentW, contentH);
    }

    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinZ() { return minZ; }
    public double getMaxZ() { return maxZ; }
}
