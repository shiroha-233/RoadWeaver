package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.Collections;
import java.util.List;

/**
 * 寻路结果数据模型
 */
public record PathResult(List<BlockPos> rawPath, List<RoadSegmentPlacement> segments, boolean success) {

    public static PathResult success(List<RoadSegmentPlacement> segments) {
        return new PathResult(Collections.emptyList(), segments != null ? segments : Collections.emptyList(), true);
    }

    public static PathResult raw(List<BlockPos> rawPath) {
        return new PathResult(rawPath != null ? rawPath : Collections.emptyList(), Collections.emptyList(), true);
    }

    public static PathResult failure() {
        return new PathResult(Collections.emptyList(), Collections.emptyList(), false);
    }

    public boolean isEmpty() {
        return (segments == null || segments.isEmpty()) && (rawPath == null || rawPath.isEmpty());
    }

    public boolean hasRawPath() {
        return rawPath != null && !rawPath.isEmpty();
    }
}
