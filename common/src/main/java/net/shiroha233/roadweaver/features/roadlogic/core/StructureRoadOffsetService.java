package net.shiroha233.roadweaver.features.roadlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.search.StructurePredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * 专门负责根据结构类型决定道路端点需要从结构点向外缩进多少格。
 * 
 * 职责：
 * - 检测给定端点附近是否属于特定结构（目前主要是原版村庄）
 * - 按结构类别返回一个"缩进距离"（单位：方块）
 * - 根据实际路径方向，裁剪掉进入结构保护区的路段
 * 
 * 重要改进（v2）：
 * - 不再在规划阶段预设偏移方向
 * - 改为在 A* 寻路完成后，根据路径实际的入口方向来裁剪路段
 * - 这样即使路径从意外方向绕过来，也不会穿过结构
 */
public final class StructureRoadOffsetService {
    private StructureRoadOffsetService() {
    }

    private enum StructureCategory {
        VILLAGE,
        OTHER,
        UNKNOWN
    }

    // 当坐标与预测结构点不完全重合时，允许的匹配容差（半径，单位：方块）
    private static final int MATCH_TOLERANCE_BLOCKS = 16;

    /**
     * @deprecated 使用 {@link #trimPathNearStructure} 代替，在寻路完成后裁剪路径
     */
    @Deprecated
    public static BlockPos adjustEndpoint(ServerLevel level, BlockPos endpoint, BlockPos otherEnd) {
        // 保留旧方法签名以兼容，但现在直接返回原端点
        // 实际的结构保护由 trimPathNearStructure 在寻路后处理
        return endpoint;
    }

    /**
     * 在 A* 寻路完成后，裁剪掉进入结构保护区的路段。
     * 
     * 原理：
     * - 从路径两端向中间扫描
     * - 找到第一个离开结构保护区的点作为新端点
     * - 裁剪掉保护区内的路段
     * 
     * @param level    世界
     * @param segments 原始路径段列表
     * @param rawStart 原始起点（结构中心）
     * @param rawEnd   原始终点（结构中心）
     * @return 裁剪后的路径段列表
     */
    public static List<Records.RoadSegmentPlacement> trimPathNearStructure(
            ServerLevel level,
            List<Records.RoadSegmentPlacement> segments,
            BlockPos rawStart,
            BlockPos rawEnd) {
        
        if (segments == null || segments.size() < 3) return segments;
        if (!Level.OVERWORLD.equals(level.dimension())) return segments;
        
        int startOffset = getOffsetBlocksForEndpoint(level, rawStart);
        int endOffset = getOffsetBlocksForEndpoint(level, rawEnd);
        
        // 如果两端都不需要偏移，直接返回
        if (startOffset <= 0 && endOffset <= 0) return segments;
        
        int n = segments.size();
        int trimStart = 0;  // 从头部裁剪到这个索引（不包含）
        int trimEnd = n;    // 从这个索引开始裁剪到尾部（不包含）
        
        // 从起点端扫描，找到第一个离开保护区的点
        if (startOffset > 0) {
            long offsetSq = (long) startOffset * startOffset;
            for (int i = 0; i < n; i++) {
                BlockPos pos = segments.get(i).middlePos();
                long dx = (long) pos.getX() - rawStart.getX();
                long dz = (long) pos.getZ() - rawStart.getZ();
                long distSq = dx * dx + dz * dz;
                if (distSq >= offsetSq) {
                    trimStart = i;
                    break;
                }
            }
        }
        
        // 从终点端扫描，找到第一个离开保护区的点
        if (endOffset > 0) {
            long offsetSq = (long) endOffset * endOffset;
            for (int i = n - 1; i >= 0; i--) {
                BlockPos pos = segments.get(i).middlePos();
                long dx = (long) pos.getX() - rawEnd.getX();
                long dz = (long) pos.getZ() - rawEnd.getZ();
                long distSq = dx * dx + dz * dz;
                if (distSq >= offsetSq) {
                    trimEnd = i + 1;
                    break;
                }
            }
        }
        
        // 确保裁剪后至少保留一些路段
        if (trimStart >= trimEnd || trimEnd - trimStart < 3) {
            // 如果裁剪后路径太短，保留中间部分
            int mid = n / 2;
            trimStart = Math.max(0, mid - 2);
            trimEnd = Math.min(n, mid + 3);
        }
        
        // 返回裁剪后的子列表
        return new ArrayList<>(segments.subList(trimStart, trimEnd));
    }

    /**
     * 获取指定端点的保护区半径（方块数）
     */
    public static int getOffsetBlocksForEndpoint(ServerLevel level, BlockPos endpoint) {
        StructureCategory cat = detectCategory(level, endpoint);
        int configOffset = ConfigService.get().structureRoadOffset();
        return switch (cat) {
            case VILLAGE -> configOffset;
            default -> 0;
        };
    }

    private static StructureCategory detectCategory(ServerLevel level, BlockPos endpoint) {
        if (!Level.OVERWORLD.equals(level.dimension())) return StructureCategory.UNKNOWN;

        // 扩大搜索范围：搜索端点所在区块及周围 1 格区块（共 3x3 区块）
        // 这样可以处理端点与结构中心不在同一区块的边界情况
        int cx = endpoint.getX() >> 4;
        int cz = endpoint.getZ() >> 4;
        int searchRadius = 1; // 搜索半径（区块）

        List<Records.StructureInfo> infos = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cx - searchRadius, cz - searchRadius,
                cx + searchRadius, cz + searchRadius,
                true,
                java.util.List.of("#minecraft:village"),
                java.util.List.of()
        );
        if (infos == null || infos.isEmpty()) return StructureCategory.UNKNOWN;

        // 扩大容差范围，适应 A* 网格对齐后的偏移
        int tolerance = Math.max(MATCH_TOLERANCE_BLOCKS, ConfigService.get().aStarStep() + 8);
        long tol2 = (long) tolerance * (long) tolerance;
        for (Records.StructureInfo info : infos) {
            BlockPos p = info.pos();
            long dx = (long) p.getX() - endpoint.getX();
            long dz = (long) p.getZ() - endpoint.getZ();
            long d2 = dx * dx + dz * dz;
            if (d2 <= tol2) {
                return StructureCategory.VILLAGE;
            }
        }
        return StructureCategory.UNKNOWN;
    }
}
