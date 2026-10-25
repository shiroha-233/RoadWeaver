package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.isWaterLike;

/**
 * 道路跨度提取器。
 */
public final class PathSpanExtractor {
    private static final int TUNNEL_SLOPE_ABS_THRESHOLD = 4;
    private static final int TUNNEL_RUN_MIN_LENGTH = 3;

    private PathSpanExtractor() {}

    public static List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
                                              ServerLevel level,
                                              TerrainSamplingCache cache,
                                              int bridgeMinWaterDepth) {
        return extractSpans(segments, level, cache, null, bridgeMinWaterDepth);
    }

    public static List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
                                              ServerLevel level,
                                              TerrainSamplingCache cache,
                                              PathTerrainField terrain,
                                              int bridgeMinWaterDepth) {
        List<RoadSpan> spans = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return spans;
        }

        List<BlockPos> centers = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement seg : segments) {
            centers.add(seg.middlePos());
        }

        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos center : centers) {
            if (terrain == null || !terrain.hasAccurateSample(center.getX(), center.getZ())) {
                missing.add(center);
            }
        }
        Map<Long, AccurateHeightSample> samples = accurate.samplePositions(missing);
        collectBridgeSpans(spans, centers, level, cache, terrain, accurate, samples, bridgeMinWaterDepth);
        collectTunnelSpans(spans, centers);
        return spans;
    }

    private static void collectBridgeSpans(List<RoadSpan> spans,
                                           List<BlockPos> centers,
                                           ServerLevel level,
                                           TerrainSamplingCache cache,
                                           PathTerrainField terrain,
                                           AccurateHeightSampler accurate,
                                           Map<Long, AccurateHeightSample> samples,
                                           int bridgeMinWaterDepth) {
        int minWaterDepth = bridgeMinWaterDepth;
        boolean inWater = false;
        int waterStart = -1;

        for (int i = 0; i < centers.size(); i++) {
            BlockPos pos = centers.get(i);
            boolean water = isBridgeWater(level, cache, terrain, accurate, samples, pos, minWaterDepth);

            if (water && !inWater) {
                inWater = true;
                waterStart = i;
            } else if (!water && inWater) {
                appendBridgeSpan(spans, centers, waterStart, i);
                inWater = false;
                waterStart = -1;
            }
        }

        if (inWater && waterStart >= 0) {
            appendBridgeSpan(spans, centers, waterStart, centers.size() - 1);
        }
    }

    private static boolean isBridgeWater(ServerLevel level,
                                         TerrainSamplingCache cache,
                                         PathTerrainField terrain,
                                         AccurateHeightSampler accurate,
                                         Map<Long, AccurateHeightSample> samples,
                                         BlockPos pos,
                                         int minWaterDepth) {
        if (terrain != null && terrain.hasAccurateSample(pos.getX(), pos.getZ())) {
            return terrain.isBridgeWater(pos.getX(), pos.getZ(), minWaterDepth);
        }
        int sea = level.getSeaLevel();
        AccurateHeightSample sample = samples.get(AccurateHeightSample.key(pos.getX(), pos.getZ()));
        int oceanFloor = sample != null ? sample.oceanFloorWg() : accurate.oceanFloorWg(pos.getX(), pos.getZ());
        int surfaceY = sample != null ? sample.worldSurfaceWg() : accurate.worldSurfaceWg(pos.getX(), pos.getZ());
        boolean biomeWater = isWaterLike(cache, pos.getX(), pos.getZ(), level) && oceanFloor < sea;
        boolean heightWater = (surfaceY <= sea + 1) && (oceanFloor < surfaceY - 1);
        boolean waterColumn = biomeWater || heightWater;
        int waterDepth = waterColumn ? Math.max(0, sea - oceanFloor) : 0;
        return waterColumn && waterDepth >= Math.max(1, minWaterDepth);
    }

    private static void appendBridgeSpan(List<RoadSpan> spans,
                                         List<BlockPos> centers,
                                         int waterStart,
                                         int endIndex) {
        int startIdx = Math.max(0, waterStart - 1);
        BlockPos start = centers.get(startIdx);
        BlockPos end = centers.get(Math.min(endIndex, centers.size() - 1));
        spans.add(new RoadSpan(start, end, SpanType.BRIDGE));
    }

    private static void collectTunnelSpans(List<RoadSpan> spans, List<BlockPos> centers) {
        int runStart = -1;
        for (int i = 1; i < centers.size(); i++) {
            BlockPos previous = centers.get(i - 1);
            BlockPos current = centers.get(i);
            int dy = Math.abs(current.getY() - previous.getY());
            boolean steep = dy >= TUNNEL_SLOPE_ABS_THRESHOLD;
            if (steep) {
                if (runStart < 0) {
                    runStart = i - 1;
                }
            } else if (runStart >= 0) {
                appendTunnelSpan(spans, centers, runStart, i);
                runStart = -1;
            }
        }

        if (runStart >= 0) {
            appendTunnelSpan(spans, centers, runStart, centers.size() - 1);
        }
    }

    private static void appendTunnelSpan(List<RoadSpan> spans,
                                         List<BlockPos> centers,
                                         int runStart,
                                         int endIndex) {
        int len = endIndex - runStart;
        if (len < TUNNEL_RUN_MIN_LENGTH) {
            return;
        }
        BlockPos start = centers.get(runStart);
        BlockPos end = centers.get(endIndex);
        spans.add(new RoadSpan(start, end, SpanType.TUNNEL));
    }
}
