package net.shiroha233.roadweaver.map.tile.query;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * overlay 瓦片静态查询。
 */
public final class OverlayTileQueryService {
    private OverlayTileQueryService() {}

    public static OverlayTileScene query(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null) {
            return OverlayTileScene.empty();
        }

        boolean overworld = Level.OVERWORLD.equals(level.dimension());
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && overworld;
        int[] src = allowPredicted
                ? new int[]{StructureFileStorage.SOURCE_MANUAL, StructureFileStorage.SOURCE_PREDICTED}
                : new int[]{StructureFileStorage.SOURCE_MANUAL};

        List<StructureInfo> infos = StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, src);
        HashSet<BlockPos> uniqueStructures = new HashSet<>();
        ArrayList<BlockPos> structures = new ArrayList<>();
        for (StructureInfo info : infos) {
            if (info == null || info.pos() == null) continue;
            BlockPos pos = new BlockPos(info.pos().getX(), 0, info.pos().getZ());
            if (uniqueStructures.add(pos)) {
                structures.add(pos);
            }
        }

        ArrayList<List<BlockPos>> roads = new ArrayList<>();
        if (!overworld) {
            return new OverlayTileScene(structures, roads);
        }
        List<RoadData> roadDataList = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        for (RoadData rd : roadDataList) {
            List<RoadSegmentPlacement> segments = rd.roadSegmentList();
            if (segments == null || segments.isEmpty()) continue;
            ArrayList<BlockPos> polyline = new ArrayList<>(segments.size());
            for (RoadSegmentPlacement segment : segments) {
                BlockPos pos = segment.middlePos();
                if (pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                        && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ) {
                    polyline.add(pos);
                }
            }
            if (polyline.size() >= 2) {
                roads.add(polyline);
            }
        }

        return new OverlayTileScene(structures, roads);
    }
}
