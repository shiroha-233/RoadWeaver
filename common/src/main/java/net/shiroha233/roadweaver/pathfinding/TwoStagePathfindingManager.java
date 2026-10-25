package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * 两阶段寻路管理器
 */
public final class TwoStagePathfindingManager {
    private TwoStagePathfindingManager() {}

    public static List<RoadSegmentPlacement> refineSegmentHeights(ServerLevel level,
                                                                  List<RoadSegmentPlacement> segments,
                                                                  AccurateHeightSampler accurate) {
        if (level == null || segments == null || segments.isEmpty() || accurate == null) {
            return segments;
        }

        List<BlockPos> centers = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement segment : segments) {
            if (segment != null) {
                centers.add(segment.middlePos());
            }
        }
        accurate.prefetchPositions(centers);

        List<RoadSegmentPlacement> out = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement seg : segments) {
            if (seg == null) continue;
            BlockPos mid = seg.middlePos();
            int y = accurate.surfaceHeight(mid.getX(), mid.getZ());
            out.add(new RoadSegmentPlacement(
                    new BlockPos(mid.getX(), y, mid.getZ()),
                    seg.positions()));
        }
        return out;
    }
}
