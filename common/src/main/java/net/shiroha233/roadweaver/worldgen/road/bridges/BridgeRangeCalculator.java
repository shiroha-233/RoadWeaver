package net.shiroha233.roadweaver.worldgen.road.bridges;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桥梁范围计算器
 */
public final class BridgeRangeCalculator {
    private BridgeRangeCalculator() {}

    public static record RangeResult(boolean[] isBridge, List<int[]> mergedRanges, boolean[] skipSegments) {}

    private static double dist2d(BlockPos a, BlockPos b) {
        long dx = (long) b.getX() - a.getX();
        long dz = (long) b.getZ() - a.getZ();
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    private static int estimateRangeLengthBlocks(List<BlockPos> middlePositions, int a, int b) {
        if (middlePositions == null || middlePositions.isEmpty()) return 0;
        int n = middlePositions.size();
        int from = Math.max(0, Math.min(n - 1, a));
        int to = Math.max(0, Math.min(n - 1, b));
        if (from > to) {
            int t = from;
            from = to;
            to = t;
        }
        double sum = 0.0;
        for (int i = from; i < to; i++) {
            sum += dist2d(middlePositions.get(i), middlePositions.get(i + 1));
        }
        return (int) Math.round(sum);
    }

    public static RangeResult compute(List<BlockPos> middlePositions, List<RoadSpan> spans) {
        ModConfig cfg = ConfigService.get();
        return compute(middlePositions, spans, cfg);
    }

    public static RangeResult compute(List<BlockPos> middlePositions,
                                      List<RoadSpan> spans,
                                      ModConfig cfg) {
        int n = middlePositions.size();
        boolean[] isBridge = new boolean[n];
        boolean[] skipSegments = new boolean[n];
        List<int[]> bridgeRanges = new ArrayList<>();

        if (cfg == null) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        if (!cfg.bridgeEnabled()) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        int minLength = Math.max(1, cfg.bridgeMinLength());
        int mergeGap = Math.max(1, cfg.bridgeMergeGap());
        boolean useBuoysInstead = cfg.bridgeUseBuoysInstead();
        int maxLenBlocks = useBuoysInstead ? 0 : Math.max(0, cfg.bridgeMaxLengthBlocks());
        
        if (spans != null) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < n; i++) indexMap.put(middlePositions.get(i).asLong(), i);
            for (RoadSpan sp : spans) {
                if (sp.type() != SpanType.BRIDGE) continue;
                Integer si = indexMap.get(sp.start().asLong());
                Integer ei = indexMap.get(sp.end().asLong());
                if (si == null || ei == null) continue;
                int a = Math.max(0, Math.min(si, ei));
                int b = Math.min(n - 1, Math.max(si, ei));
                bridgeRanges.add(new int[]{a, b});
            }
        }
        
        if (!bridgeRanges.isEmpty()) {
            bridgeRanges.sort(Comparator.comparingInt(o -> o[0]));
            
            List<int[]> merged = new ArrayList<>();
            int[] cur = bridgeRanges.get(0);
            for (int idx = 1; idx < bridgeRanges.size(); idx++) {
                int[] nxt = bridgeRanges.get(idx);
                if (nxt[0] <= cur[1] + mergeGap) {
                    cur[1] = Math.max(cur[1], nxt[1]);
                } else {
                    merged.add(cur);
                    cur = nxt;
                }
            }
            merged.add(cur);
            
            List<int[]> filtered = new ArrayList<>();
            for (int[] r : merged) {
                int len = r[1] - r[0] + 1;
                if (len >= minLength) {
                    filtered.add(r);
                }
            }
            List<int[]> filteredByMaxLen = new ArrayList<>();
            for (int[] r : filtered) {
                if (maxLenBlocks <= 0) {
                    filteredByMaxLen.add(r);
                    continue;
                }
                int approxLen = estimateRangeLengthBlocks(middlePositions, r[0], r[1]);
                if (approxLen > maxLenBlocks) {
                    int s = Math.max(0, r[0]);
                    int e = Math.min(n - 1, r[1]);
                    for (int k = s; k <= e; k++) {
                        skipSegments[k] = true;
                    }
                } else {
                    filteredByMaxLen.add(r);
                }
            }
            bridgeRanges = filteredByMaxLen;
        }
        
        for (int[] r : bridgeRanges) {
            for (int k = r[0]; k <= r[1]; k++) {
                isBridge[k] = true;
            }
        }
        
        return new RangeResult(isBridge, bridgeRanges, skipSegments);
    }
}
