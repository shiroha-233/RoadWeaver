package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoarseTerrainRegionPathfindingTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");

    @Test
    void preparedGridUsesExistingTilesAcrossNegativeAndPositiveBoundaries() {
        CoarseRegionBounds bounds = CoarseRegionBounds.aligned(DIMENSION, -264, -8, 264, 8, 8);
        Map<CoarseTerrainTileKey, CoarseTerrainTile> tiles = new HashMap<>();
        for (int tileX = -2; tileX <= 1; tileX++) {
            tiles.put(key(tileX, 0), tile(tileX));
        }
        CoarseTerrainRegion region = new CoarseTerrainRegion(bounds, 63, tiles);
        int[] xs = {-264, -256, -8, 0, 248, 256, 264};
        int[] heights = new int[xs.length];
        PathSnapshot[] before = new PathSnapshot[xs.length];
        for (int i = 0; i < xs.length; i++) {
            heights[i] = region.height(xs[i], 0);
            before[i] = snapshot(region, xs[i], 0);
        }

        region.preparePathfinding();
        CoarseTerrainTile original = tiles.get(key(0, 0));
        original.heights()[0] = 120;
        assertEquals(70, region.height(0, 0));
        for (int i = 0; i < xs.length; i++) {
            assertEquals(heights[i], region.height(xs[i], 0));
            assertEquals(before[i], snapshot(region, xs[i], 0));
        }
        assertEquals(63, region.height(0, -8));
        assertFalse(region.isColumnWater(0, -8));
        assertTrue(region.isNearWater(-8, 0, 8));
        assertEquals(63, region.height(1024, 0));
        region.dispose();
    }

    @Test
    void oversizedRegionDoesNotMaterializeAnotherLargeGrid() {
        int width = (int) Math.sqrt(RoadConstants.COARSE_REGION_MAX_SAMPLES) + 1;
        CoarseRegionBounds bounds = CoarseRegionBounds.aligned(DIMENSION, 0, 0, width * 8, width * 8, 8);
        CoarseTerrainRegion region = new CoarseTerrainRegion(bounds, 63, Map.of(key(0, 0), tile(0)));
        region.preparePathfinding();
        assertEquals(70, region.height(0, 0));
        assertEquals(63, region.height(width * 8, width * 8));
        region.dispose();
    }

    private static PathSnapshot snapshot(CoarseTerrainRegion region, int x, int z) {
        return new PathSnapshot(region.height(x, z), region.oceanFloor(x, z),
                region.isColumnWater(x, z), region.isWaterBiome(x, z),
                region.isNearWater(x, z, 8), region.sampleBundle(x, z));
    }

    private record PathSnapshot(int height, int floor, boolean water, boolean waterBiome,
                                boolean nearWater, net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField.SampleBundle bundle) {}

    private static CoarseTerrainTileKey key(int tileX, int tileZ) {
        return new CoarseTerrainTileKey(DIMENSION, tileX, tileZ, 16, 8, RoadConstants.COARSE_TERRAIN_TILE_SCHEMA_VERSION);
    }

    private static CoarseTerrainTile tile(int tileX) {
        CoarseTerrainTileKey key = key(tileX, 0);
        int width = key.sampleWidth();
        int height = key.sampleHeight();
        int size = width * height;
        short[] heights = new short[size];
        short[] floors = new short[size];
        byte[] flags = new byte[size];
        for (int i = 0; i < size; i++) {
            heights[i] = (short) (70 + tileX);
            floors[i] = 58;
            flags[i] = CoarseTerrainTile.flags(tileX == -1, tileX == -1, tileX == -1);
        }
        return new CoarseTerrainTile(key, 63, width, height, heights, floors, flags, new int[size]);
    }
}
