package net.shiroha233.roadweaver.postprocess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionSlopeLimiterTest {
    private static boolean[] fixedPrefix(int n, int count) {
        boolean[] fixed = new boolean[n];
        for (int i = 0; i < count; i++) fixed[i] = true;
        return fixed;
    }

    @Test
    void spreadsADropAfterAMergedStretch() {
        // The case from the test world: merged at 108, the side road's own heights drop to 94 within four segments.
        int[] heights = {108, 108, 108, 105, 101, 98, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94,
                94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94};
        JunctionSlopeLimiter.limit(heights, fixedPrefix(heights.length, 3), 1);
        assertEquals(108, heights[2]);
        for (int i = 3; i < heights.length; i++) {
            assertTrue(Math.abs(heights[i] - heights[i - 1]) <= 1, "step at " + i);
            assertTrue(heights[i] >= 108 - (i - 2 + 1) / 2, "too steep at " + i + ": " + heights[i]);
        }
        assertEquals(94, heights[heights.length - 1]);
    }

    @Test
    void leavesARoadWithinTheLimitAlone() {
        int[] heights = {70, 70, 71, 71, 72, 72, 72, 71, 71, 70};
        int[] before = heights.clone();
        JunctionSlopeLimiter.limit(heights, fixedPrefix(heights.length, 2), 1);
        assertArrayEquals(before, heights);
    }

    @Test
    void neverMovesFixedSegments() {
        int[] heights = {60, 90, 60, 90, 60};
        boolean[] fixed = {true, false, true, false, true};
        JunctionSlopeLimiter.limit(heights, fixed, 1);
        assertEquals(60, heights[0]);
        assertEquals(60, heights[2]);
        assertEquals(60, heights[4]);
        // One segment from a fixed one may still differ by one block, as on any road.
        assertTrue(heights[1] >= 60 && heights[1] <= 61, "segment 1: " + heights[1]);
        assertTrue(heights[3] >= 60 && heights[3] <= 61, "segment 3: " + heights[3]);
    }

    @Test
    void goesStraightBetweenAnchorsThatAreTooFarApart() {
        int[] heights = {100, 100, 100, 100, 80};
        boolean[] fixed = {true, false, false, false, true};
        JunctionSlopeLimiter.limit(heights, fixed, 1);
        assertEquals(100, heights[0]);
        assertEquals(80, heights[4]);
        assertTrue(heights[1] > heights[3], "should descend between the anchors");
    }
}
