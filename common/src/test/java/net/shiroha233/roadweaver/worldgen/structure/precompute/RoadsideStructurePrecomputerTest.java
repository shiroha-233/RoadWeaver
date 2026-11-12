/* 文件职责：验证路边结构只复用真实对齐的区域精采列。 */
package net.shiroha233.roadweaver.worldgen.structure.precompute;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.terrain.accurate.AccurateRegionBounds;
import net.shiroha233.roadweaver.terrain.accurate.AccurateTerrainRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadsideStructurePrecomputerTest {
    @Test
    void unalignedStructureCoordinateDoesNotReuseNearestGridColumn() {
        AccurateRegionBounds bounds = AccurateRegionBounds.aligned(0, 0, 16, 16, 8);
        int size = Math.toIntExact(bounds.sampleCount());
        @SuppressWarnings("unchecked")
        Holder<Biome>[] biomes = (Holder<Biome>[]) new Holder<?>[size];
        AccurateTerrainRegion region = new AccurateTerrainRegion(
                bounds, 63, new short[size], new short[size], new byte[size], biomes);

        assertTrue(RoadsideStructurePrecomputer.hasAlignedTerrainSample(region, 8, 8));
        assertFalse(RoadsideStructurePrecomputer.hasAlignedTerrainSample(region, 9, 8));
    }
}
