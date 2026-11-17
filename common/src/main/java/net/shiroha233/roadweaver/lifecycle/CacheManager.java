package net.shiroha233.roadweaver.lifecycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.metrics.AccurateSamplingStats;
import net.shiroha233.roadweaver.core.metrics.TerrainSamplingStats;
import net.shiroha233.roadweaver.persistence.ConnectionListUpdater;
import net.shiroha233.roadweaver.persistence.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprintService;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.planning.path.PlannedPathCache;
import net.shiroha233.roadweaver.planning.path.RawPathDebugService;
import net.shiroha233.roadweaver.planning.pipeline.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.pipeline.TerrainSamplingSessions;
import net.shiroha233.roadweaver.terrain.AccurateHeightSampler;
import net.shiroha233.roadweaver.terrain.TerrainSamplingCache;
import net.shiroha233.roadweaver.terrain.coarse.CoarseTerrainTileCache;
import net.shiroha233.roadweaver.util.WorldRootPaths;
import net.shiroha233.roadweaver.worldgen.road.RoadSpatialIndex;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;
import net.shiroha233.roadweaver.worldgen.road.decoration.text.SignTextService;
import net.shiroha233.roadweaver.worldgen.structure.precompute.PendingRoadsideVillageStorage;
import net.shiroha233.roadweaver.worldgen.structure.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.worldgen.structure.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.worldgen.structure.registry.RoadsideStructureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一缓存生命周期管理器。
 */
public final class CacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private CacheManager() {}

    public static void onServerStarted() {
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        RoadSpatialIndex.clearAllCache();
        StructureFileStorage.shutdown();
        WorldgenFingerprintService.clearMemo();
        TerrainSamplingStats.reset();
        AccurateSamplingStats.reset();
        TerrainSamplingSessions.clearAll();
        TerrainSamplingCache.clearAllBiomes();
        AutomaticPlanningSamplingActivities.clearAll();
        RoadWorldgenPlanCache.clearAll();
        WorldRootPaths.clearAll();
        LOGGER.debug("CacheManager: 缓存已初始化");
    }

    public static void onServerStopping(Iterable<ServerLevel> levels) {
        ServerLevel overworld = null;
        for (ServerLevel level : levels) {
            if (level != null && Level.OVERWORLD.equals(level.dimension())) {
                overworld = level;
                break;
            }
        }
        if (overworld != null) {
            try {
                SignTextService.flushPersistentFallback(overworld);
            } catch (Exception e) {
                LOGGER.warn("刷新 SignTextService 失败: {}", e.getMessage());
            }
            try {
                RoadShardStorage.flushAll(overworld);
            } catch (Exception e) {
                LOGGER.warn("刷新 RoadShardStorage 失败: {}", e.getMessage());
            }
        }

        RoadShardStorage.shutdown();
        StructureFileStorage.shutdown();
        WorldgenFingerprintService.clearMemo();
        RoadWorldgenPlanCache.clearAll();

        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        PendingStructureStorage.clearAll();
        PendingRoadsideVillageStorage.clearAll();
        RoadSpatialIndex.clearAllCache();
        RoadPlanningService.resetAll();
        SignTextService.clearPending();
        TerrainSamplingStats.reset();
        TerrainSamplingSessions.clearAll();
        AccurateHeightSampler.clearAll();
        TerrainSamplingCache.clearAllBiomes();
        PlannedPathCache.clearAll();
        RawPathDebugService.clearAll();
        CoarseTerrainTileCache.clearAll();
        WorldRootPaths.clearAll();

        LOGGER.debug("CacheManager: 所有缓存已清理");
    }

    public static void onDimensionUnload(ServerLevel level) {
        if (level == null) return;

        try {
            StructureFileStorage.flush(level);
        } catch (Exception e) {
            LOGGER.warn("刷新维度 {} 结构状态失败: {}",
                    level.dimension().identifier(), e.getMessage());
        }

        TerrainSamplingSessions.clear(level);
        AutomaticPlanningSamplingActivities.clear(level);
        AccurateHeightSampler.clearForLevel(level);
        TerrainSamplingCache.clearBiomesForLevel(level);
        PlannedPathCache.clear(level);
        RawPathDebugService.clear(level);
        ConnectionListUpdater.clear(level);
        RoadWorldgenPlanCache.clear(level);
        WorldRootPaths.clear(level);
        CoarseTerrainTileCache.clearAll();
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        try {
            SignTextService.flushPersistentFallback(level);
        } catch (Exception e) {
            LOGGER.warn("刷新维度 {} 路牌文本失败: {}",
                    level.dimension().identifier(), e.getMessage());
        }

        try {
            RoadShardStorage.closeConnection(level);
        } catch (Exception e) {
            LOGGER.warn("关闭维度 {} 数据库连接失败: {}",
                    level.dimension().identifier(), e.getMessage());
        }

        RoadSpatialIndex.clearCache(level);
        PendingStructureStorage.clearAll();
        PendingRoadsideVillageStorage.clearAll();
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        SignTextService.onDimensionUnload(level);

        LOGGER.debug("CacheManager: 维度 {} 缓存已清理", level.dimension().identifier());
    }
}
