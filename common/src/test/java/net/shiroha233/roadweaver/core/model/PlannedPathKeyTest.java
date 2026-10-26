/* 文件职责：验证规划路径使用完整端点键消除旧 long 哈希碰撞。 */
package net.shiroha233.roadweaver.core.model;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.util.PlanningUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlannedPathKeyTest {
    @Test
    void fullEndpointPairsRemainDistinctWhenLegacyEdgeHashCollides() {
        BlockPos zero = new BlockPos(0, 0, 0);
        BlockPos five = new BlockPos(0, 0, 5);
        BlockPos four = new BlockPos(0, 0, 4);
        BlockPos seven = new BlockPos(0, 0, 7);

        assertEquals(PlanningUtils.edgeKey(zero, five), PlanningUtils.edgeKey(four, seven));
        assertNotEquals(PlannedPathKey.of(zero, five), PlannedPathKey.of(four, seven));
    }

    @Test
    void endpointOrderIsCanonical() {
        BlockPos first = new BlockPos(-12, 64, 48);
        BlockPos second = new BlockPos(91, 70, -3);

        assertEquals(PlannedPathKey.of(first, second), PlannedPathKey.of(second, first));
    }
}
