package net.shiroha233.roadweaver.postprocess;

import java.util.Arrays;

public final class JunctionSlopeLimiter {
    private JunctionSlopeLimiter() {}

    public static void limit(int[] heights, boolean[] fixed, int stepPerTwoSegs) {
        int n = heights.length;
        if (n < 2 || stepPerTwoSegs < 0) return;
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
                heights[i] = interpolate(heights, fixed, i);
            } else {
                heights[i] = clamp(heights[i], low[i], high[i]);
            }
        }

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
