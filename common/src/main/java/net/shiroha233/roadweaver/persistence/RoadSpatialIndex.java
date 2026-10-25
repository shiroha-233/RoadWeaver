package net.shiroha233.roadweaver.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;
import net.shiroha233.roadweaver.worldgen.road.RoadDensityStamp;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;

import java.util.List;

/**
 * 道路空间查询入口。
 *
 * <p>道路 footprint 已在 RoadChunkPlan 编译为 16x16 列 stamp。植被阶段发生在道路
 * TOP_LAYER Feature 之前，因此它与 Beardifier 共享同一个会话计划，不需要第二套 LRU。</p>
 */
public final class RoadSpatialIndex {
    private RoadSpatialIndex() {
    }

    public static boolean isNearRoad(LevelSimulatedReader level, BlockPos pos) {
        return isNearRoad(resolve(level), pos);
    }

    public static boolean isNearRoad(WorldGenLevel level, BlockPos pos) {
        return isNearRoad(resolve(level), pos);
    }

    public static boolean isNearRoad(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        ModConfig config = ConfigService.get();
        if (config == null || !config.roadAppearance().roadsEnabled()
                || !config.roadAppearance().preventTreesOnRoad()) {
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        RoadChunkPlan plan = RoadWorldgenPlanCache.get(level, chunkPos, config);
        RoadDensityStamp stamp = plan.densityStamp();
        int localX = pos.getX() - chunkPos.getMinBlockX();
        int localZ = pos.getZ() - chunkPos.getMinBlockZ();
        return stamp.isOccupied(localX, localZ)
                || stamp.fillLateral(localX, localZ) > 0.0F
                || stamp.carveLateral(localX, localZ) > 0.0F;
    }

    public static void clearCache(ServerLevel level) {
        RoadWorldgenPlanCache.clear(level);
    }

    public static void clearAllCache() {
        RoadWorldgenPlanCache.clearAll();
    }

    public static void invalidateChunk(ServerLevel level, int chunkX, int chunkZ) {
        RoadWorldgenPlanCache.invalidate(level, List.of(ChunkPos.asLong(chunkX, chunkZ)));
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel resolve(LevelSimulatedReader level) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        if (level instanceof WorldGenRegion region) return region.getLevel();
        if (level instanceof WorldGenLevel worldGenLevel) return resolve(worldGenLevel);
        return null;
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel resolve(WorldGenLevel level) {
        if (level instanceof ServerLevel serverLevel) return serverLevel;
        if (level instanceof WorldGenRegion region) return region.getLevel();
        Level backingLevel = level == null ? null : level.getLevel();
        return backingLevel instanceof ServerLevel serverLevel ? serverLevel : null;
    }
}
