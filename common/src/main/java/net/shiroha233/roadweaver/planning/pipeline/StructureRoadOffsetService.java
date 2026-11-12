package net.shiroha233.roadweaver.planning.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.map.search.StructurePredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构道路偏移服务
 */
public final class StructureRoadOffsetService {
    private StructureRoadOffsetService() {}

    private enum StructureCategory {
        VILLAGE,
        OTHER,
        UNKNOWN
    }

    private static final int MATCH_TOLERANCE_BLOCKS = 16;

    @Deprecated
    public static BlockPos adjustEndpoint(ServerLevel level, BlockPos endpoint, BlockPos otherEnd) {
        return endpoint;
    }

    public static List<RoadSegmentPlacement> trimPathNearStructure(
            ServerLevel level,
            List<RoadSegmentPlacement> segments,
            BlockPos rawStart,
            BlockPos rawEnd) {
        
        if (segments == null || segments.size() < 3) return segments;
        if (!Level.OVERWORLD.equals(level.dimension())) return segments;
        
        int startOffset = getOffsetBlocksForEndpoint(level, rawStart);
        int endOffset = getOffsetBlocksForEndpoint(level, rawEnd);
        
        if (startOffset <= 0 && endOffset <= 0) return segments;
        
        int n = segments.size();
        int trimStart = 0;
        int trimEnd = n;
        
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
        
        if (trimStart >= trimEnd || trimEnd - trimStart < 3) {
            int mid = n / 2;
            trimStart = Math.max(0, mid - 2);
            trimEnd = Math.min(n, mid + 3);
        }
        
        return new ArrayList<>(segments.subList(trimStart, trimEnd));
    }

    public static int getOffsetBlocksForEndpoint(ServerLevel level, BlockPos endpoint) {
        StructureCategory cat = detectCategory(level, endpoint);
        var cfg = ConfigService.get();
        return switch (cat) {
            case VILLAGE -> cfg.villageRoadOffset();
            case OTHER -> cfg.otherStructureRoadOffset();
            default -> 0;
        };
    }

    private static StructureCategory detectCategory(ServerLevel level, BlockPos endpoint) {
        if (!Level.OVERWORLD.equals(level.dimension())) return StructureCategory.UNKNOWN;

        int cx = endpoint.getX() >> 4;
        int cz = endpoint.getZ() >> 4;
        int searchRadius = 1;
        int tolerance = Math.max(MATCH_TOLERANCE_BLOCKS, ConfigService.get().aStarStep() + 8);
        long tol2 = (long) tolerance * (long) tolerance;

        List<StructureInfo> villageInfos = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cx - searchRadius, cz - searchRadius,
                cx + searchRadius, cz + searchRadius,
                true,
                List.of("#minecraft:village"),
                List.of()
        );
        if (villageInfos != null && !villageInfos.isEmpty()) {
            for (StructureInfo info : villageInfos) {
                BlockPos p = info.pos();
                long dx = (long) p.getX() - endpoint.getX();
                long dz = (long) p.getZ() - endpoint.getZ();
                if (dx * dx + dz * dz <= tol2) {
                    return StructureCategory.VILLAGE;
                }
            }
        }

        var cfg = ConfigService.get();
        List<String> whitelist = cfg.structureWhitelist();
        if (whitelist != null && !whitelist.isEmpty()) {
            List<StructureInfo> otherInfos = StructurePredictor.predictOverworldStructuresInRect(
                    level,
                    cx - searchRadius, cz - searchRadius,
                    cx + searchRadius, cz + searchRadius,
                    true,
                    whitelist,
                    cfg.structureBlacklist()
            );
            if (otherInfos != null && !otherInfos.isEmpty()) {
                for (StructureInfo info : otherInfos) {
                    BlockPos p = info.pos();
                    long dx = (long) p.getX() - endpoint.getX();
                    long dz = (long) p.getZ() - endpoint.getZ();
                    if (dx * dx + dz * dz <= tol2) {
                        return StructureCategory.OTHER;
                    }
                }
            }
        }

        return StructureCategory.UNKNOWN;
    }
}
