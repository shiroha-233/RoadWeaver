/* 文件职责：验证道路独立精采兜底区域为长距离绕行保留足够边距。 */
package net.shiroha233.roadweaver.planning.pipeline;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoadPathCalculatorTest {
    @Test
    void searchMarginScalesWithConnectionAndHasSafeUpperBound() {
        assertEquals(512, RoadPathCalculator.searchMargin(BlockPos.ZERO, new BlockPos(64, 0, 0)));
        assertEquals(1_536, RoadPathCalculator.searchMargin(BlockPos.ZERO, new BlockPos(1_536, 0, 0)));
        assertEquals(2_048, RoadPathCalculator.searchMargin(BlockPos.ZERO, new BlockPos(3_000, 0, 0)));
    }
}
