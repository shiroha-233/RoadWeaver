package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.Map;

/**
 * 区域级粗采样结果。
 */
public final class CoarseTerrainRegion implements PathTerrainField {
    private static final byte FLAG_COLUMN_WATER = 1;
    private static final byte FLAG_WATER_BIOME = 1 << 1;
    private static final byte FLAG_NEAR_WATER = 1 << 2;

    private final CoarseRegionBounds bounds;
    private final int seaLevel;
    private short[] heights;
    private short[] oceanFloors;
    private byte[] flags;
    private int[] terrainArgb;
    private Map<CoarseTerrainTileKey, CoarseTerrainTile> tiles;
    private CoarseTerrainTile[] pathTiles;
    private int pathTileMinX;
    private int pathTileMinZ;
    private int pathTileWidth;

    public CoarseTerrainRegion(CoarseRegionBounds bounds,
                               int seaLevel,
                               Map<CoarseTerrainTileKey, CoarseTerrainTile> tiles) {
        if (bounds == null || tiles == null || tiles.isEmpty()) {
            throw new IllegalArgumentException("coarse region tiles must not be empty");
        }
        this.bounds = bounds;
        this.seaLevel = seaLevel;
        this.heights = null;
        this.oceanFloors = null;
        this.flags = null;
        this.terrainArgb = null;
        this.tiles = Map.copyOf(tiles);
    }

    public CoarseTerrainRegion(CoarseRegionBounds bounds,
                               int seaLevel,
                               short[] heights,
                               short[] oceanFloors,
                               byte[] flags,
                               int[] terrainArgb) {
        this.bounds = bounds;
        this.seaLevel = seaLevel;
        this.heights = heights;
        this.oceanFloors = oceanFloors;
        this.flags = flags;
        this.terrainArgb = terrainArgb;
        this.tiles = null;
        int expected = Math.toIntExact(bounds.sampleCount());
        if (heights.length != expected || oceanFloors.length != expected
                || flags.length != expected || terrainArgb.length != expected) {
            throw new IllegalArgumentException("coarse region arrays do not match bounds");
        }
    }

    public CoarseRegionBounds bounds() {
        return bounds;
    }

    public void preparePathfinding() {
        if (tiles == null || heights != null) return;
        if (pathTiles == null) {
            int tileBlocks = CoarseTerrainTileKey.forBlock(bounds.dimensionId(), bounds.minX(), bounds.minZ(), bounds.step())
                    .tileSizeBlocks();
            pathTileMinX = Math.floorDiv(bounds.minX(), tileBlocks);
            pathTileMinZ = Math.floorDiv(bounds.minZ(), tileBlocks);
            pathTileWidth = Math.floorDiv(bounds.maxX(), tileBlocks) - pathTileMinX + 1;
            int tileHeight = Math.floorDiv(bounds.maxZ(), tileBlocks) - pathTileMinZ + 1;
            pathTiles = new CoarseTerrainTile[Math.multiplyExact(pathTileWidth, tileHeight)];
            for (Map.Entry<CoarseTerrainTileKey, CoarseTerrainTile> entry : tiles.entrySet()) {
                int ix = entry.getKey().tileX() - pathTileMinX;
                int iz = entry.getKey().tileZ() - pathTileMinZ;
                if (ix >= 0 && ix < pathTileWidth && iz >= 0 && iz < tileHeight) {
                    pathTiles[iz * pathTileWidth + ix] = entry.getValue();
                }
            }
        }
        if (bounds.sampleCount() > RoadConstants.COARSE_REGION_MAX_SAMPLES) return;
        int size = Math.toIntExact(bounds.sampleCount());
        short[] regionHeights = new short[size];
        short[] regionFloors = new short[size];
        byte[] regionFlags = new byte[size];
        for (int iz = 0; iz < bounds.height(); iz++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int z = bounds.blockZAt(iz);
            int row = iz * bounds.width();
            for (int ix = 0; ix < bounds.width(); ix++) {
                int x = bounds.blockXAt(ix);
                CoarseTerrainTile tile = tileAt(x, z);
                if (tile == null) {
                    regionHeights[row + ix] = (short) seaLevel;
                    regionFloors[row + ix] = (short) seaLevel;
                } else {
                    SampleBundle bundle = tile.sampleBundle(x, z);
                    regionHeights[row + ix] = (short) bundle.height();
                    regionFloors[row + ix] = (short) bundle.oceanFloor();
                    regionFlags[row + ix] = CoarseTerrainTile.flags(
                            bundle.columnWater(), bundle.waterBiome(), tile.isNearWater(x, z));
                }
            }
        }
        heights = regionHeights;
        oceanFloors = regionFloors;
        flags = regionFlags;
    }

    public int terrainArgbAtIndex(int index) {
        if (tiles == null) return terrainArgb[index];
        int ix = index % bounds.width();
        int iz = index / bounds.width();
        int x = bounds.blockXAt(ix);
        int z = bounds.blockZAt(iz);
        CoarseTerrainTile tile = tileAt(x, z);
        return tile != null ? tile.terrainArgb(x, z) : 0x00000000;
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    @Override
    public int height(int x, int z) {
        if (!contains(x, z)) return seaLevel;
        if (heights == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            return tile != null ? tile.height(x, z) : seaLevel;
        }
        return heights[bounds.indexOfNearest(x, z)];
    }

    @Override
    public int oceanFloor(int x, int z) {
        if (!contains(x, z)) return seaLevel;
        if (oceanFloors == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            return tile != null ? tile.oceanFloor(x, z) : seaLevel;
        }
        return oceanFloors[bounds.indexOfNearest(x, z)];
    }

    @Override
    public boolean isColumnWater(int x, int z) {
        if (!contains(x, z)) return false;
        if (flags == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            return tile != null && tile.isColumnWater(x, z);
        }
        return hasFlag(bounds.indexOfNearest(x, z), FLAG_COLUMN_WATER);
    }

    @Override
    public boolean isNearWater(int x, int z, int neighborDistance) {
        if (!contains(x, z)) return false;
        if (flags == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            if (tile != null && tile.isNearWater(x, z)) return true;
        } else if (hasFlag(bounds.indexOfNearest(x, z), FLAG_NEAR_WATER)) {
            return true;
        }
        int steps = Math.max(1, ceilDiv(Math.max(0, neighborDistance), bounds.step()));
        for (int dz = -steps; dz <= steps; dz++) {
            for (int dx = -steps; dx <= steps; dx++) {
                if (dx == 0 && dz == 0) continue;
                int wx = x + dx * bounds.step();
                int wz = z + dz * bounds.step();
                if (contains(wx, wz) && isColumnWater(wx, wz)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Holder<Biome> biome(int x, int z) {
        return null;
    }

    @Override
    public boolean contains(int x, int z) {
        return bounds.contains(x, z);
    }

    @Override
    public int step() {
        return bounds.step();
    }

    /**
     * 释放内部大数组与瓦片引用，协助 GC 回收。调用后此对象不可再用。
     */
    @Override
    public void dispose() {
        heights = null;
        oceanFloors = null;
        flags = null;
        terrainArgb = null;
        tiles = null;
        pathTiles = null;
    }

    @Override
    public boolean isWaterBiome(int x, int z) {
        if (!contains(x, z)) return false;
        if (flags == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            return tile != null && tile.isWaterBiome(x, z);
        }
        return hasFlag(bounds.indexOfNearest(x, z), FLAG_WATER_BIOME);
    }

    @Override
    public SampleBundle sampleBundle(int x, int z) {
        if (!contains(x, z)) {
            return new SampleBundle(seaLevel, seaLevel, false, false, 0);
        }
        if (heights == null) {
            CoarseTerrainTile tile = tileAt(x, z);
            return tile != null ? tile.sampleBundle(x, z) : new SampleBundle(seaLevel, seaLevel, false, false, 0);
        }
        int index = bounds.indexOfNearest(x, z);
        int ocean = oceanFloors[index];
        boolean columnWater = hasFlag(index, FLAG_COLUMN_WATER);
        boolean waterBiome = hasFlag(index, FLAG_WATER_BIOME);
        int depth = Math.max(0, seaLevel - ocean);
        return new SampleBundle(heights[index], ocean, columnWater, waterBiome, depth);
    }

    static byte flags(boolean columnWater, boolean waterBiome, boolean nearWater) {
        int value = 0;
        if (columnWater) value |= FLAG_COLUMN_WATER;
        if (waterBiome) value |= FLAG_WATER_BIOME;
        if (nearWater) value |= FLAG_NEAR_WATER;
        return (byte) value;
    }

    private CoarseTerrainTile tileAt(int x, int z) {
        if (pathTiles != null) {
            int tileBlocks = RoadConstants.COARSE_TERRAIN_TILE_SIZE_CHUNKS * RoadConstants.CHUNK_SIZE_BLOCKS;
            int ix = Math.floorDiv(x, tileBlocks) - pathTileMinX;
            int iz = Math.floorDiv(z, tileBlocks) - pathTileMinZ;
            int tileHeight = pathTiles.length / pathTileWidth;
            return ix >= 0 && ix < pathTileWidth && iz >= 0 && iz < tileHeight
                    ? pathTiles[iz * pathTileWidth + ix] : null;
        }
        if (tiles == null) return null;
        CoarseTerrainTileKey key = CoarseTerrainTileKey.forBlock(bounds.dimensionId(), x, z, bounds.step());
        return tiles.get(key);
    }

    private boolean hasFlag(int index, byte flag) {
        return (flags[index] & flag) != 0;
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }
}
