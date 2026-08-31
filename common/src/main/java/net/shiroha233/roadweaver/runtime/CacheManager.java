/* 文件职责：统一管理服务端、维度与地形采样相关缓存的生命周期。 */
package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLAccurateProgramCache;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLAvailability;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLCoarseHeightBatchSampler;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLRuntime;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLWorldSupport;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileCache;
import net.shiroha233.roadweaver.persistence.RoadSpatialIndex;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprintService;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.planning.path.PlannedPathCache;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessions;
import net.shiroha233.roadweaver.structures.precompute.PendingRoadsideVillageStorage;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一缓存生命周期管理器。
 */
public final class CacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private CacheManager() {}

    /**
     * 服务端启动时初始化缓存状态。
     */
    public static void onServerStarted() {
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        RoadSpatialIndex.clearAllCache();
        StructureFileStorage.shutdown();
        WorldgenFingerprintService.clearMemo();
        TerrainSamplingStats.reset();
        AccurateSamplingStats.reset();
        OpenCLAvailability.reset();
        OpenCLCoarseHeightBatchSampler.clearProgramCache();
        OpenCLWorldSupport.clear();
        TerrainSamplingSessions.clearAll();
        AutomaticPlanningSamplingActivities.clearAll();
        RoadWorldgenPlanCache.clearAll();
        LOGGER.debug("CacheManager: 缓存已初始化");
    }

    /**
     * 服务端停止时清理所有缓存。
     */
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
        PlannedPathCache.clearAll();
        CoarseTerrainTileCache.clearAll();
        OpenCLRuntime.closeAll();
        OpenCLWorldSupport.clear();

        LOGGER.debug("CacheManager: 所有缓存已清理");
    }

    /**
     * 维度卸载时清理该维度关联缓存。
     */
    public static void onDimensionUnload(ServerLevel level) {
        if (level == null) return;

        try {
            StructureFileStorage.flush(level);
        } catch (Exception e) {
            LOGGER.warn("刷新维度 {} 结构状态失败: {}",
                    level.dimension().location(), e.getMessage());
        }

        TerrainSamplingSessions.clear(level);
        AutomaticPlanningSamplingActivities.clear(level);
        AccurateHeightSampler.clearForLevel(level);
        PlannedPathCache.clear(level);
        RoadWorldgenPlanCache.clear(level);
        try {
            OpenCLAccurateProgramCache.clear(level.getChunkSource().getGeneratorState().randomState());
        } catch (Throwable failure) {
            LOGGER.debug("清理维度 {} OpenCL 精采样程序失败", level.dimension().location(), failure);
        }
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        try {
            SignTextService.flushPersistentFallback(level);
        } catch (Exception e) {
            LOGGER.warn("刷新维度 {} 路牌文本失败: {}",
                    level.dimension().location(), e.getMessage());
        }

        try {
            RoadShardStorage.closeConnection(level);
        } catch (Exception e) {
            LOGGER.warn("关闭维度 {} 数据库连接失败: {}",
                    level.dimension().location(), e.getMessage());
        }

        RoadSpatialIndex.clearCache(level);
        PendingStructureStorage.clearAll();
        PendingRoadsideVillageStorage.clearAll();
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        SignTextService.onDimensionUnload(level);

        LOGGER.debug("CacheManager: 维度 {} 缓存已清理", level.dimension().location());
    }

    /**
     * 道路数据变更后使对应区块的空间索引缓存失效。
     */
    public static void invalidateRoadCache(ServerLevel level, int chunkX, int chunkZ) {
        RoadSpatialIndex.invalidateChunk(level, chunkX, chunkZ);
    }
}
