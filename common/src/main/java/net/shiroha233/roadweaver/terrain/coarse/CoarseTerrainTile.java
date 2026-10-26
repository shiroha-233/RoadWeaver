package net.shiroha233.roadweaver.terrain.coarse;

import net.shiroha233.roadweaver.terrain.PathTerrainField;

/**
 * 单个持久化粗采样地形瓦片。
 */
public final class CoarseTerrainTile {
    private static final byte FLAG_COLUMN_WATER = 1;
    private static final byte FLAG_WATER_BIOME = 1 << 1;
    private static final byte FLAG_NEAR_WATER = 1 << 2;

    private final CoarseTerrainTileKey key;
    private final int seaLevel;
    private final int sampleWidth;
    private final int sampleHeight;
    private final short[] heights;
    private final short[] oceanFloors;
    private final byte[] flags;
    private final int[] terrainArgb;

    public CoarseTerrainTile(CoarseTerrainTileKey key,
                             int seaLevel,
                             int sampleWidth,
                             int sampleHeight,
                             short[] heights,
                             short[] oceanFloors,
                             byte[] flags,
                             int[] terrainArgb) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.key = key;
        this.seaLevel = seaLevel;
        this.sampleWidth = sampleWidth;
        this.sampleHeight = sampleHeight;
        this.heights = heights;
        this.oceanFloors = oceanFloors;
        this.flags = flags;
        this.terrainArgb = terrainArgb;

        int expected = Math.multiplyExact(sampleWidth, sampleHeight);
        if (sampleWidth <= 0 || sampleHeight <= 0
                || heights == null || oceanFloors == null || flags == null || terrainArgb == null
                || heights.length != expected || oceanFloors.length != expected
                || flags.length != expected || terrainArgb.length != expected) {
            throw new IllegalArgumentException("coarse terrain tile arrays do not match dimensions");
        }
    }

    public CoarseTerrainTileKey key() {
        return key;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public int sampleWidth() {
        return sampleWidth;
    }

    public int sampleHeight() {
        return sampleHeight;
    }

    public short[] heights() {
        return heights;
    }

    public short[] oceanFloors() {
        return oceanFloors;
    }

    public byte[] flags() {
        return flags;
    }

    public int[] terrainArgb() {
        return terrainArgb;
    }

    public int terrainArgb(int x, int z) {
        return terrainArgb[indexOfNearest(x, z)];
    }

    public int terrainArgbAtIndex(int index) {
        return terrainArgb[index];
    }

    public int height(int x, int z) {
        return heights[indexOfNearest(x, z)];
    }

    public int oceanFloor(int x, int z) {
        return oceanFloors[indexOfNearest(x, z)];
    }

    public boolean isColumnWater(int x, int z) {
        return hasFlag(indexOfNearest(x, z), FLAG_COLUMN_WATER);
    }

    public boolean isWaterBiome(int x, int z) {
        return hasFlag(indexOfNearest(x, z), FLAG_WATER_BIOME);
    }

    public boolean isNearWater(int x, int z) {
        return hasFlag(indexOfNearest(x, z), FLAG_NEAR_WATER);
    }

    public PathTerrainField.SampleBundle sampleBundle(int x, int z) {
        int index = indexOfNearest(x, z);
        int ocean = oceanFloors[index];
        boolean columnWater = hasFlag(index, FLAG_COLUMN_WATER);
        boolean waterBiome = hasFlag(index, FLAG_WATER_BIOME);
        int depth = Math.max(0, seaLevel - ocean);
        return new PathTerrainField.SampleBundle(heights[index], ocean, columnWater, waterBiome, depth);
    }

    public boolean contains(int x, int z) {
        return key.containsBlock(x, z);
    }

    public int sampleCount() {
        return heights.length;
    }

    public static byte flags(boolean columnWater, boolean waterBiome, boolean nearWater) {
        int value = 0;
        if (columnWater) value |= FLAG_COLUMN_WATER;
        if (waterBiome) value |= FLAG_WATER_BIOME;
        if (nearWater) value |= FLAG_NEAR_WATER;
        return (byte) value;
    }

    public static byte withNearWater(byte flags) {
        return (byte) (flags | FLAG_NEAR_WATER);
    }

    private boolean hasFlag(int index, byte flag) {
        return (flags[index] & flag) != 0;
    }

    private int indexOfNearest(int x, int z) {
        int ix = clamp(Math.floorDiv(x - key.minBlockX() + key.step() / 2, key.step()), 0, sampleWidth - 1);
        int iz = clamp(Math.floorDiv(z - key.minBlockZ() + key.step() / 2, key.step()), 0, sampleHeight - 1);
        return iz * sampleWidth + ix;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}