/* 文件职责：验证空气、固体、流体、叶类与负 Y 列的三高度图归约。 */
package net.shiroha233.roadweaver.terrain.opencl;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightmapMaterialClassifierTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void classifiesVanillaGenerationMaterials() {
        assertEquals(0, HeightmapMaterialClassifier.mask(Blocks.AIR.defaultBlockState()));
        assertEquals(HeightmapMaterialClassifier.REQUIRED_SOLID_MASK,
                HeightmapMaterialClassifier.mask(Blocks.STONE.defaultBlockState()));
        assertEquals(HeightmapMaterialClassifier.WORLD_SURFACE | HeightmapMaterialClassifier.MOTION_BLOCKING,
                HeightmapMaterialClassifier.mask(Blocks.WATER.defaultBlockState()));
        assertEquals(HeightmapMaterialClassifier.WORLD_SURFACE | HeightmapMaterialClassifier.MOTION_BLOCKING,
                HeightmapMaterialClassifier.mask(Blocks.LAVA.defaultBlockState()));
        assertEquals(HeightmapMaterialClassifier.WORLD_SURFACE | HeightmapMaterialClassifier.OCEAN_FLOOR,
                HeightmapMaterialClassifier.mask(Blocks.OAK_LEAVES.defaultBlockState()));
        assertTrue(HeightmapMaterialClassifier.oreVeinOutputsMatch(
                HeightmapMaterialClassifier.REQUIRED_SOLID_MASK));
    }

    @Test
    void reducesSyntheticColumnAcrossNegativeY() {
        int water = HeightmapMaterialClassifier.mask(Blocks.WATER.defaultBlockState());
        int stone = HeightmapMaterialClassifier.mask(Blocks.STONE.defaultBlockState());
        IntUnaryOperator masks = y -> y == 1 ? water : y == -2 ? stone : 0;

        Heights heights = reduce(3, -5, masks);
        assertEquals(2, heights.worldSurface());
        assertEquals(-1, heights.oceanFloor());
        assertEquals(2, heights.motionBlocking());
    }

    private static Heights reduce(int topY, int minY, IntUnaryOperator maskAtY) {
        int worldSurface = minY;
        int oceanFloor = minY;
        int motionBlocking = minY;
        int unresolved = HeightmapMaterialClassifier.REQUIRED_SOLID_MASK;
        for (int y = topY; y >= minY && unresolved != 0; y--) {
            int mask = maskAtY.applyAsInt(y);
            if ((unresolved & HeightmapMaterialClassifier.WORLD_SURFACE) != 0
                    && (mask & HeightmapMaterialClassifier.WORLD_SURFACE) != 0) {
                worldSurface = y + 1;
                unresolved &= ~HeightmapMaterialClassifier.WORLD_SURFACE;
            }
            if ((unresolved & HeightmapMaterialClassifier.OCEAN_FLOOR) != 0
                    && (mask & HeightmapMaterialClassifier.OCEAN_FLOOR) != 0) {
                oceanFloor = y + 1;
                unresolved &= ~HeightmapMaterialClassifier.OCEAN_FLOOR;
            }
            if ((unresolved & HeightmapMaterialClassifier.MOTION_BLOCKING) != 0
                    && (mask & HeightmapMaterialClassifier.MOTION_BLOCKING) != 0) {
                motionBlocking = y + 1;
                unresolved &= ~HeightmapMaterialClassifier.MOTION_BLOCKING;
            }
        }
        return new Heights(worldSurface, oceanFloor, motionBlocking);
    }

    private record Heights(int worldSurface, int oceanFloor, int motionBlocking) {}
}
