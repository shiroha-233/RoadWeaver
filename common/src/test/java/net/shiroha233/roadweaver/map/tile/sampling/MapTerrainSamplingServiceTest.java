/* 文件职责：验证主动地图采样的模式路由、范围上限与加权进度。 */
package net.shiroha233.roadweaver.map.tile.sampling;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateRegionBounds;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseRegionBounds;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTerrainSamplingServiceTest {
    private static final ResourceLocation DIMENSION =
            new ResourceLocation("minecraft", "overworld");

    @Test
    void routesManualSamplingFromTheActualEffectiveMode() {
        assertEquals(TerrainSamplingMode.LEGACY_DIRECT,
                MapTerrainSamplingService.resolveSamplingMode(session(
                        TerrainSamplingMode.LEGACY_DIRECT, TerrainSamplingMode.LEGACY_DIRECT)));
        assertEquals(TerrainSamplingMode.COARSE_CORRIDOR,
                MapTerrainSamplingService.resolveSamplingMode(session(
                        TerrainSamplingMode.COARSE_CORRIDOR, TerrainSamplingMode.COARSE_CORRIDOR)));
        assertEquals(TerrainSamplingMode.FULL_REGION,
                MapTerrainSamplingService.resolveSamplingMode(session(
                        TerrainSamplingMode.FULL_REGION, TerrainSamplingMode.FULL_REGION)));
        assertEquals(TerrainSamplingMode.LEGACY_DIRECT,
                MapTerrainSamplingService.resolveSamplingMode(session(
                        TerrainSamplingMode.FULL_REGION, TerrainSamplingMode.LEGACY_DIRECT)));
        assertNull(MapTerrainSamplingService.resolveSamplingMode(null));
    }

    @Test
    void enforcesTheMatchingSamplingLimits() {
        CoarseRegionBounds normalCoarse = CoarseRegionBounds.aligned(DIMENSION, 0, 0, 2048, 1024, 8);
        CoarseRegionBounds excessiveCoarse = CoarseRegionBounds.aligned(DIMENSION, 0, 0, 20_000, 20_000, 8);
        AccurateRegionBounds normalAccurate = AccurateRegionBounds.aligned(0, 0, 2048, 1024, 8);
        AccurateRegionBounds excessiveAccurate = AccurateRegionBounds.aligned(0, 0, 20_000, 20_000, 8);

        assertTrue(MapTerrainSamplingService.isWithinCoarseManualLimits(normalCoarse));
        assertFalse(MapTerrainSamplingService.isWithinCoarseManualLimits(excessiveCoarse));
        assertTrue(MapTerrainSamplingService.isWithinAccurateManualLimits(normalAccurate));
        assertFalse(MapTerrainSamplingService.isWithinAccurateManualLimits(excessiveAccurate));
    }

    @Test
    void reportsWeightedProgressForBothPhases() {
        assertEquals(0, MapTerrainSamplingService.weightedPercent(0, 10, 0, 70));
        assertEquals(35, MapTerrainSamplingService.weightedPercent(5, 10, 0, 70));
        assertEquals(70, MapTerrainSamplingService.weightedPercent(10, 10, 0, 70));
        assertEquals(85, MapTerrainSamplingService.weightedPercent(5, 10, 70, 30));
        assertEquals(100, MapTerrainSamplingService.weightedPercent(10, 10, 70, 30));
    }

    private static TerrainSamplingSessionSnapshot session(TerrainSamplingMode configured,
                                                          TerrainSamplingMode effective) {
        return new TerrainSamplingSessionSnapshot(configured, effective, "cpu", "", "");
    }
}
