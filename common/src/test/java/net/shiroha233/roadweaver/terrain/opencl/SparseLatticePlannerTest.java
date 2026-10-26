/* 文件职责：验证量化列只调度其三线性插值实际依赖的 lattice 角点。 */
package net.shiroha233.roadweaver.terrain.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparseLatticePlannerTest {
    @Test
    void stepEightColumnsUseSixteenOfTwentyFiveHorizontalCorners() {
        int[] columns = {
                0, 0, 0,
                0, 8, 0,
                0, 0, 8,
                0, 8, 8
        };

        SparseLatticePlanner.Plan plan = SparseLatticePlanner.plan(1, columns, 4, 4, 48);

        assertTrue(plan.sparse());
        assertEquals(16 * 49, plan.workItemCount());
        assertEquals(25 * 49, plan.latticePointsPerChunk());
        assertEquals(0, plan.references()[0]);
        assertEquals(16 * 49, plan.references().length);
    }

    @Test
    void completeChunkUsesImplicitDenseIndexing() {
        int[] columns = new int[16 * 16 * 3];
        int index = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                columns[index++] = 0;
                columns[index++] = x;
                columns[index++] = z;
            }
        }

        SparseLatticePlanner.Plan plan = SparseLatticePlanner.plan(1, columns, 4, 4, 48);

        assertFalse(plan.sparse());
        assertEquals(25 * 49, plan.workItemCount());
        assertEquals(1, plan.references().length);
    }
}
