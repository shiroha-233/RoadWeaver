package net.shiroha233.roadweaver.planning.pipeline;

import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 桥梁过渡调整器
 */
public final class BridgeTransitionAdjuster {
    private BridgeTransitionAdjuster() {}

    public static int[] adjust(int[] baseY, List<int[]> bridgeRanges, ModConfig cfg) {
        if (baseY == null || bridgeRanges == null || bridgeRanges.isEmpty()) return baseY;
        int n = baseY.length;
        int rampN = Math.max(0, cfg.bridgeRampSegments());
        if (rampN <= 0) return baseY;

        int[] original = baseY.clone();
        int[] adjusted = baseY.clone();

        for (int[] r : bridgeRanges) {
            int a = Math.max(0, Math.min(n - 1, r[0]));
            int b = Math.max(0, Math.min(n - 1, r[1]));
            if (a > b) { int t = a; a = b; b = t; }
            int leftStart = Math.max(0, a - rampN);
            int rightEnd = Math.min(n - 1, b + rampN);

            int boundaryLeftY = original[a];
            int boundaryRightY = original[b];

            int countLeft = a - leftStart;
            if (countLeft > 0) {
                for (int j = leftStart; j <= a - 1; j++) {
                    int dist = (a - 1) - j;
                    double w = (countLeft == 0) ? 0.0 : (1.0 - (dist / (double) countLeft));
                    double target = original[j] * (1.0 - w) + boundaryLeftY * w;
                    adjusted[j] = (int) Math.round(target);
                }
            }

            int countRight = rightEnd - b;
            if (countRight > 0) {
                for (int j = b + 1; j <= rightEnd; j++) {
                    int dist = j - (b + 1);
                    double w = (countRight == 0) ? 0.0 : (1.0 - (dist / (double) countRight));
                    double target = original[j] * (1.0 - w) + boundaryRightY * w;
                    adjusted[j] = (int) Math.round(target);
                }
            }
        }

        int step = Math.max(0, Math.min(8, cfg.maxSlopeStepPerTwoSegments()));
        if (!cfg.slopeLimitEnabled() || step <= 0) {
            return adjusted;
        }

        for (int i = 1; i < n; i++) {
            if (adjusted[i] > adjusted[i - 1] + step) adjusted[i] = adjusted[i - 1] + step;
            if (adjusted[i] < adjusted[i - 1] - step) adjusted[i] = adjusted[i - 1] - step;
        }
        for (int i = n - 2; i >= 0; i--) {
            if (adjusted[i] > adjusted[i + 1] + step) adjusted[i] = adjusted[i + 1] + step;
            if (adjusted[i] < adjusted[i + 1] - step) adjusted[i] = adjusted[i + 1] - step;
        }
        return adjusted;
    }
}
