package net.shiroha233.roadweaver.terrain.coarse;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 持久化粗采样地形瓦片坐标。
 */
public record CoarseTerrainTileKey(ResourceLocation dimensionId,
                                   int tileX,
                                   int tileZ,
                                   int tileSizeChunks,
                                   int step,
                                   int schemaVersion) {
    public CoarseTerrainTileKey {
        if (dimensionId == null) {
            throw new IllegalArgumentException("dimensionId must not be null");
        }
        tileSizeChunks = Math.max(1, tileSizeChunks);
        step = Math.max(RoadConstants.ASTAR_STEP_MIN, step);
        schemaVersion = Math.max(1, schemaVersion);
    }

    public static CoarseTerrainTileKey forBlock(ResourceLocation dimensionId, int blockX, int blockZ, int step) {
        int tileSizeChunks = RoadConstants.COARSE_TERRAIN_TILE_SIZE_CHUNKS;
        int tileBlocks = tileSizeChunks * RoadConstants.CHUNK_SIZE_BLOCKS;
        return new CoarseTerrainTileKey(
                dimensionId,
                Math.floorDiv(blockX, tileBlocks),
                Math.floorDiv(blockZ, tileBlocks),
                tileSizeChunks,
                step,
                RoadConstants.COARSE_TERRAIN_TILE_SCHEMA_VERSION);
    }

    public int tileSizeBlocks() {
        return tileSizeChunks * RoadConstants.CHUNK_SIZE_BLOCKS;
    }

    public int minBlockX() {
        return tileX * tileSizeBlocks();
    }

    public int minBlockZ() {
        return tileZ * tileSizeBlocks();
    }

    public int maxBlockXExclusive() {
        return minBlockX() + tileSizeBlocks();
    }

    public int maxBlockZExclusive() {
        return minBlockZ() + tileSizeBlocks();
    }

    public int sampleWidth() {
        return ceilDiv(tileSizeBlocks(), step) + 1;
    }

    public int sampleHeight() {
        return ceilDiv(tileSizeBlocks(), step) + 1;
    }

    public int blockXAt(int sampleX) {
        return minBlockX() + sampleX * step;
    }

    public int blockZAt(int sampleZ) {
        return minBlockZ() + sampleZ * step;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minBlockX()
                && blockX < maxBlockXExclusive()
                && blockZ >= minBlockZ()
                && blockZ < maxBlockZExclusive();
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }
}