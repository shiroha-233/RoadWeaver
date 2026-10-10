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
    void splineCannotEscapeItsOwnSegmentCorridor() {
        BlockPos a = new BlockPos(0, 70, 0), b = new BlockPos(128, 70, 0);
        SplineHelper.Vec2d bounded = PathPostProcessor.boundToSegment(new SplineHelper.Vec2d(64, 100), a, b, 16);
        assertEquals(64, bounded.x());
        assertEquals(16, bounded.z());
        bounded = PathPostProcessor.boundToSegment(new SplineHelper.Vec2d(-100, 0), a, b, 2);
        assertEquals(-2, bounded.x());
    }

    @Test
    void waterNodesAndBanksAreNotRelaxed() {
        List<BlockPos> path = List.of(new BlockPos(0, 70, 0), new BlockPos(128, 70, 128),
                new BlockPos(384, 70, 128), new BlockPos(512, 70, 0));
        assertEquals(path, PathPostProcessor.relaxPathSkippingBridge(path, new boolean[]{false, true, true, false}));
    }

    @Test
    void bezierBendIsNotClippedToTwoBlocks() {
        List<BlockPos> path = List.of(new BlockPos(0, 70, 0), new BlockPos(32, 70, 0),
                new BlockPos(32, 70, 32), new BlockPos(0, 70, 64));
        var curve = SplineHelper.catmullRomBezierCasteljau(0, 0, 32, 0, 32, 32, 0, 64, 0.5);
        var bounded = PathPostProcessor.boundToSegment(curve, path.get(1), path.get(2), 16);
        assertTrue(Math.abs(bounded.x() - 32) > 2);
    }

    @Test
    void controlSimplificationLimitsSpanAndPreservesWaterEdges() {
        List<BlockPos> nodes = new java.util.ArrayList<>();
        for (int x = 0; x <= 160; x += 8) nodes.add(new BlockPos(x, 70, 0));
        boolean[] water = new boolean[nodes.size()];
        water[9] = water[10] = true;
        var indices = PathPostProcessor.boundedControlIndices(nodes, water, 32);
        assertTrue(indices.contains(8));
        assertTrue(indices.contains(9));
        assertTrue(indices.contains(10));
        assertTrue(indices.contains(11));
        for (int i = 1; i < indices.size(); i++) {
            assertTrue(nodes.get(indices.get(i)).distSqr(nodes.get(indices.get(i - 1))) <= 32 * 32);
        }
    }

    @Test
    void staircaseSearchNodesProduceWidelySpacedCurveControls() {
        List<BlockPos> nodes = new java.util.ArrayList<>();
        nodes.add(new BlockPos(0, 70, 0));
        for (int i = 1; i <= 8; i++) {
            nodes.add(new BlockPos(i * 8, 70, (i - 1) * 8));
            nodes.add(new BlockPos(i * 8, 70, i * 8));
        }
        var indices = PathPostProcessor.boundedControlIndices(nodes, new boolean[nodes.size()], 64);
        assertTrue(indices.size() < nodes.size() / 2, "grid staircases should not become Bézier control points at each step");
        assertEquals(0, indices.get(0));
        assertEquals(nodes.size() - 1, indices.get(indices.size() - 1));
    }
}
