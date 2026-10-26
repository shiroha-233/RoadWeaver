/* 文件职责：验证 aquifer 候选网格在负坐标和区块边界的确定性布局。 */
package net.shiroha233.roadweaver.terrain.opencl;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AquiferPositionPlannerTest {
    @Test
    void matchesPositionalRandomFactoryForNegativeChunksAndFullHeightRange() {
        PositionalRandomFactory factory = RandomSource.create(0x5EEDL).forkPositional();
        List<Long> chunks = List.of(ChunkPos.asLong(-2, 3), ChunkPos.asLong(0, -1));
        AquiferPositionPlanner.Plan plan = AquiferPositionPlanner.plan(chunks, factory, -64, 384);

        assertArrayEquals(new int[]{-2, 3, 0, -1}, plan.chunkCoordinates());
        assertEquals(-7, plan.minGridY());
        assertEquals(35, plan.gridYSize());
        assertEquals(315, plan.pointsPerChunk());
        assertPoint(plan, factory, 0, -3, -7, 2);
        assertPoint(plan, factory, 0, -1, 27, 4);
        assertPoint(plan, factory, 1, -1, -7, -2);
        assertPoint(plan, factory, 1, 1, 27, 0);
    }

    private static void assertPoint(AquiferPositionPlanner.Plan plan,
                                    PositionalRandomFactory factory,
                                    int chunkIndex,
                                    int gridX,
                                    int gridY,
                                    int gridZ) {
        int chunkX = plan.chunkCoordinates()[chunkIndex * 2];
        int chunkZ = plan.chunkCoordinates()[chunkIndex * 2 + 1];
        int xIndex = gridX - (chunkX - 1);
        int yIndex = gridY - plan.minGridY();
        int zIndex = gridZ - (chunkZ - 1);
        int point = (yIndex * 3 + zIndex) * 3 + xIndex;
        int uniquePoint = plan.chunkPointIndices()[chunkIndex * plan.pointsPerChunk() + point];
        int offset = uniquePoint * 3;
        RandomSource random = factory.at(gridX, gridY, gridZ);
        assertEquals(gridX * 16 + random.nextInt(10), plan.positions()[offset]);
        assertEquals(gridY * 12 + random.nextInt(9), plan.positions()[offset + 1]);
        assertEquals(gridZ * 16 + random.nextInt(10), plan.positions()[offset + 2]);
    }

    @Test
    void adjacentChunksShareOverlappingAquiferPoints() {
        PositionalRandomFactory factory = RandomSource.create(42L).forkPositional();
        AquiferPositionPlanner.Plan plan = AquiferPositionPlanner.plan(
                List.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0)), factory, -64, 384);

        assertEquals(420, plan.uniquePointCount());
        assertEquals(630, plan.chunkPointIndices().length);
        assertTrue(plan.uniquePointCount() < 2 * plan.pointsPerChunk());
        assertEquals(plan.uniquePointCount() * 13, plan.pointPreliminaryIndices().length);
        assertTrue(plan.preliminaryPointCount() < plan.uniquePointCount() * 13);

        int[] offsets = {
                0, 0, -2, -1, -1, -1, 0, -1, 1, -1, -3, 0, -2, 0,
                -1, 0, 1, 0, -2, 1, -1, 1, 0, 1, 1, 1
        };
        int uniquePoint = plan.chunkPointIndices()[0];
        int pointX = plan.positions()[uniquePoint * 3];
        int pointZ = plan.positions()[uniquePoint * 3 + 2];
        for (int offsetIndex = 0; offsetIndex < 13; offsetIndex++) {
            int preliminaryIndex = plan.pointPreliminaryIndices()[uniquePoint * 13 + offsetIndex];
            assertEquals(Math.floorDiv(pointX + offsets[offsetIndex * 2] * 16, 4) * 4,
                    plan.preliminaryCoordinates()[preliminaryIndex * 2]);
            assertEquals(Math.floorDiv(pointZ + offsets[offsetIndex * 2 + 1] * 16, 4) * 4,
                    plan.preliminaryCoordinates()[preliminaryIndex * 2 + 1]);
        }
    }
}
