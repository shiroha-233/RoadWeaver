/* 文件职责：验证区域级精确量化地形场的范围覆盖与最近步长格读取。 */
package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccurateTerrainRegionTest {
    @Test
    void coveredCoordinatesReuseNearestAccurateGridColumn() {
        AccurateRegionBounds bounds = AccurateRegionBounds.aligned(-7, -7, 9, 9, 8);
        int size = Math.toIntExact(bounds.sampleCount());
        short[] heights = new short[size];
        short[] oceanFloors = new short[size];
        byte[] flags = new byte[size];
        @SuppressWarnings("unchecked")
        Holder<Biome>[] biomes = (Holder<Biome>[]) new Holder<?>[size];

        int sampledIndex = bounds.indexOf(0, 0);
        heights[sampledIndex] = 91;
        oceanFloors[sampledIndex] = 55;
        flags[sampledIndex] = AccurateTerrainRegion.flags(true, true);
        AccurateTerrainRegion region = new AccurateTerrainRegion(bounds, 63, heights, oceanFloors, flags, biomes);

        assertTrue(region.contains(0, 0));
        assertTrue(region.hasAccurateSample(0, 0));
        assertEquals(91, region.height(0, 0));
        assertEquals(55, region.oceanFloor(0, 0));
        assertTrue(region.isColumnWater(0, 0));
        assertTrue(region.isWaterBiome(0, 0));

        assertTrue(region.contains(1, 0));
        assertTrue(region.hasAccurateSample(1, 0));
        assertEquals(91, region.height(1, 0));
        assertEquals(55, region.oceanFloor(1, 0));
        assertFalse(region.contains(17, 0));
    }

    @Test
    void waterLookupUsesOnlyAlignedNeighborColumns() {
        AccurateRegionBounds bounds = AccurateRegionBounds.aligned(0, 0, 16, 0, 8);
        int size = Math.toIntExact(bounds.sampleCount());
        short[] heights = new short[size];
        short[] oceanFloors = new short[size];
        byte[] flags = new byte[size];
        @SuppressWarnings("unchecked")
        Holder<Biome>[] biomes = (Holder<Biome>[]) new Holder<?>[size];

        flags[bounds.indexOf(8, 0)] = AccurateTerrainRegion.flags(true, false);
        AccurateTerrainRegion region = new AccurateTerrainRegion(bounds, 63, heights, oceanFloors, flags, biomes);

        assertTrue(region.isNearWater(0, 0, 1));
        assertTrue(region.isNearWater(1, 0, 1));
        assertFalse(region.isNearWater(-1, 0, 1));
    }
}
