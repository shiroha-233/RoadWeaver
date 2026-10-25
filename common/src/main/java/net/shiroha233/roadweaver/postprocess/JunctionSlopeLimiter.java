/* Re-applies the road slope limit to a road whose segments were partly replaced by another road's. */
package net.shiroha233.roadweaver.postprocess;

import java.util.Arrays;

/**
 * When a side road merges into a main road, the merged segments take the main road's heights, which can be far from
 * the side road's own heights a few segments away. This spreads that difference along the side road so that no two
 * neighbouring segments differ by more than the slope limit. Fixed segments (on the main road, or on a bridge) keep
 * their heights.
 */
public final class JunctionSlopeLimiter {
    private JunctionSlopeLimiter() {}

    /**
     * @param heights        target heights, changed in place
     * @param fixed          segments whose height must not change
     * @param stepPerTwoSegs allowed height change over two segments (the road slope setting)
     */
    public static void limit(int[] heights, boolean[] fixed, int stepPerTwoSegs) {
        int n = heights.length;
        if (n < 2 || stepPerTwoSegs < 0) return;
        // Same rule as a road's own heights: at most ceil(step/2) per segment and at most `step` over two segments,
        // so a free segment d segments away from a fixed one may differ from it by ceil(d/2) * step.
        int perSegment = (stepPerTwoSegs + 1) / 2;

        int[] low = new int[n];
        int[] high = new int[n];
        Arrays.fill(low, Integer.MIN_VALUE);
        Arrays.fill(high, Integer.MAX_VALUE);
        int anchor = -1;
        for (int i = 0; i < n; i++) {
            if (fixed[i]) anchor = i;
            if (anchor >= 0) bound(low, high, i, heights[anchor], i - anchor, stepPerTwoSegs);
        }
        anchor = -1;
        for (int i = n - 1; i >= 0; i--) {
            if (fixed[i]) anchor = i;
            if (anchor >= 0) bound(low, high, i, heights[anchor], anchor - i, stepPerTwoSegs);
        }

        for (int i = 0; i < n; i++) {
            if (fixed[i]) continue;
            if (low[i] > high[i]) {
                // The fixed segments on both sides are too far apart in height for the limit: go straight between them.
                heights[i] = interpolate(heights, fixed, i);
            } else {
                heights[i] = clamp(heights[i], low[i], high[i]);
            }
        }

        // Free neighbours must respect the per-segment and two-segment limits as well.
        for (int i = 1; i < n; i++) {
            if (fixed[i]) continue;
            heights[i] = clamp(heights[i], heights[i - 1] - perSegment, heights[i - 1] + perSegment);
            if (i >= 2) heights[i] = clamp(heights[i], heights[i - 2] - stepPerTwoSegs, heights[i - 2] + stepPerTwoSegs);
        }
        for (int i = n - 2; i >= 0; i--) {
            if (fixed[i]) continue;
            heights[i] = clamp(heights[i], heights[i + 1] - perSegment, heights[i + 1] + perSegment);
            if (i + 2 < n) heights[i] = clamp(heights[i], heights[i + 2] - stepPerTwoSegs, heights[i + 2] + stepPerTwoSegs);
        }
    }

    private static void bound(int[] low, int[] high, int i, int anchorHeight, int distance, int stepPerTwoSegs) {
        int allowed = ((distance + 1) / 2) * stepPerTwoSegs;
        low[i] = Math.max(low[i], anchorHeight - allowed);
        high[i] = Math.min(high[i], anchorHeight + allowed);
    }

    private static int interpolate(int[] heights, boolean[] fixed, int i) {
        int left = i;
        while (left >= 0 && !fixed[left]) left--;
        int right = i;
        while (right < heights.length && !fixed[right]) right++;
        if (left < 0) return heights[right];
        if (right >= heights.length) return heights[left];
        double t = (i - left) / (double) (right - left);
        return (int) Math.round(heights[left] + (heights[right] - heights[left]) * t);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
