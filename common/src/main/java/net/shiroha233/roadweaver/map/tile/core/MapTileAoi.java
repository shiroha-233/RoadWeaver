package net.shiroha233.roadweaver.map.tile.core;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * 围绕动态规划窗口定义的瓦片采样 AOI。
 */
public record MapTileAoi(Identifier dimensionId, int centerBlockX, int centerBlockZ, int radiusBlocks) {
    public MapTileAoi {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (radiusBlocks < 0) {
            throw new IllegalArgumentException("radiusBlocks must be >= 0");
        }
    }

    public int minBlockX() {
        return centerBlockX - radiusBlocks;
    }

    public int maxBlockX() {
        return centerBlockX + radiusBlocks;
    }

    public int minBlockZ() {
        return centerBlockZ - radiusBlocks;
    }

    public int maxBlockZ() {
        return centerBlockZ + radiusBlocks;
    }

    public int diameterBlocks() {
        return radiusBlocks * 2;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minBlockX() && blockX <= maxBlockX()
                && blockZ >= minBlockZ() && blockZ <= maxBlockZ();
    }

    public MapTileRect tileRect(int zoom) {
        return MapTileScheme.tileRectForBlockRect(zoom, minBlockX(), minBlockZ(), maxBlockX(), maxBlockZ());
    }
}