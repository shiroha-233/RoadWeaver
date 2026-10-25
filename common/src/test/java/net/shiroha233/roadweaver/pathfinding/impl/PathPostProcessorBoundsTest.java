package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PathPostProcessorBoundsTest {
    @Test
    void longEdgesCannotMoveCornersDozensOfBlocks() {
        List<BlockPos> path = List.of(new BlockPos(0, 70, 0), new BlockPos(128, 70, 128),
                new BlockPos(384, 70, 128), new BlockPos(512, 70, 0));
        List<BlockPos> relaxed = PathPostProcessor.relaxPathSkippingBridge(path, new boolean[4]);
        for (int i = 0; i < path.size(); i++) assertTrue(path.get(i).distSqr(relaxed.get(i)) <= 2);
        assertEquals(path.get(0), relaxed.get(0));
        assertEquals(path.get(3), relaxed.get(3));
    }

    @Test
    void waterNodesAndBanksAreNotRelaxed() {
        List<BlockPos> path = List.of(new BlockPos(0, 70, 0), new BlockPos(128, 70, 128),
                new BlockPos(384, 70, 128), new BlockPos(512, 70, 0));
        assertEquals(path, PathPostProcessor.relaxPathSkippingBridge(path, new boolean[]{false, true, true, false}));
    }

    @Test
    void simplifyPathKeepsDirectionChanges() {
        List<BlockPos> path = List.of(new BlockPos(0, 70, 0), new BlockPos(64, 70, 0),
                new BlockPos(64, 70, 64), new BlockPos(128, 70, 64));
        List<BlockPos> simplified = PathPostProcessor.simplifyPath(path);
        assertEquals(4, simplified.size());
    }
}
