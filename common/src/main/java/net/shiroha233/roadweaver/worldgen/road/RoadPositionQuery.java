package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;

/**
 * 道路位置查询门面，用于阻止树木在道路上生成
 */
public final class RoadPositionQuery {
    private RoadPositionQuery() {}

    public static boolean isOnRoad(LevelSimulatedReader level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    public static boolean isOnRoad(ServerLevel level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    public static boolean isOnRoad(WorldGenLevel level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    public static void clearCache(ServerLevel level) {
        RoadSpatialIndex.clearCache(level);
    }

    public static void clearAllCache() {
        RoadSpatialIndex.clearAllCache();
    }

    public static void invalidateChunk(ServerLevel level, int cx, int cz) {
        RoadSpatialIndex.invalidateChunk(level, cx, cz);
    }
}
