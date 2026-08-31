/* 文件职责：协调结构发现、连接拓扑规划与道路地形规划用例。 */
package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;
import net.shiroha233.roadweaver.map.tile.storage.AccurateTerrainMapFingerprintGuard;
import net.shiroha233.roadweaver.map.tile.backfill.AccurateTerrainTileBackfillService;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionSampler;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.planning.path.PlannedPathCache;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.planning.terrain.RoadTerrainPlanningPipeline;
import net.shiroha233.roadweaver.planning.terrain.RoadTerrainPlanningPort;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessions;
import net.shiroha233.roadweaver.network.ServerMapPlanningActivityBridge;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.search.StructureIndexService;
import net.shiroha233.roadweaver.util.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路规划服务
 */
public final class RoadPlanningService {
    private RoadPlanningService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Set<TerrainRepairKey> TERRAIN_REPAIR_TILES = ConcurrentHashMap.newKeySet();
    private static final Set<Long> ROAD_BACKFILL_PLAN_TILES = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PLANNING_TILES = ConcurrentHashMap.newKeySet();
    private static final RoadTerrainPlanningPipeline TERRAIN_PLANNING = new RoadTerrainPlanningPipeline();

    private static void prunePlannedIfTooLarge(Level level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Set<Long> keys = new HashSet<>(provider.getPlannedTileKeys((ServerLevel) level));
        if (keys.size() > RoadConstants.MAX_PLANNED_KEYS) {
            int remove = keys.size() - RoadConstants.MAX_PLANNED_KEYS;
            Iterator<Long> it = keys.iterator();
            while (remove > 0 && it.hasNext()) { it.next(); it.remove(); remove--; }
            provider.setPlannedTileKeys((ServerLevel) level, keys);
        }
        Map<Long, Long> centers = new HashMap<>(provider.getPlannedTileCenters((ServerLevel) level));
        if (centers.size() > RoadConstants.MAX_PLANNED_KEYS) {
            int remove2 = centers.size() - RoadConstants.MAX_PLANNED_KEYS;
            Iterator<Long> it2 = centers.keySet().iterator();
            while (remove2 > 0 && it2.hasNext()) { it2.next(); it2.remove(); remove2--; }
            provider.setPlannedTileCenters((ServerLevel) level, centers);
        }
    }

    public static void initialPlan(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        ServerMapTileStorage.migrateLegacyTerrainTiles(level);
        AccurateTerrainMapFingerprintGuard.ensure(level);
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.PLANNING, "discovering_structures");
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        planRect(level, minX, minZ, maxX, maxZ);
    }

    /** 共享池积压超过该值时暂停提交新的规划任务，形成提交端背压。 */
    private static final int SHARED_BACKLOG_LIMIT = 256;

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (!cfg.planning().dynamicPlanEnabled()) return;
        if (ThreadPoolManager.sharedBacklog() > SHARED_BACKLOG_LIMIT) return;
        int radiusChunks = Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
        int stride = Math.max(1, cfg.planning().dynamicPlanStrideChunks());
        int tile = Math.max(RoadConstants.PLAN_TILE_MIN, Math.min(RoadConstants.PLAN_TILE_MAX, stride));
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        int kx = floorDiv(pcx, tile);
        int kz = floorDiv(pcz, tile);
        long key = (((long) kx) << 32) ^ (kz & 0xffffffffL);
        int minX = (pcx - radiusChunks) * 16;
        int maxX = (pcx + radiusChunks) * 16;
        int minZ = (pcz - radiusChunks) * 16;
        int maxZ = (pcz + radiusChunks) * 16;
        WorldDataProvider provider0 = WorldDataProvider.getInstance();
        boolean alreadyPlanned = provider0.getPlannedTileKeys(level).contains(key);
        boolean hasExistingRoads = RoadShardStorage.hasRoadInRect(level, minX, minZ, maxX, maxZ);
        boolean backfillPlan = hasExistingRoads && markRoadBackfillPlanTile(level, key);
        boolean shouldPlan = !alreadyPlanned || backfillPlan;
        if (!shouldPlan) {
            ensureTerrainMapTilesAsync(level, key, minX, minZ, maxX, maxZ);
            return;
        }
        if (!markPlanningTile(level, key)) return;
        AutomaticPlanningSamplingActivities.Activity samplingActivity = beginDynamicPlanningSampling(
                level,
                minX,
                minZ,
                maxX,
                maxZ,
                cfg.planning().terrainSamplingMode());
        try {
            planRectAsync(level, minX, minZ, maxX, maxZ).whenComplete((ignored, error) -> {
                try {
                    unmarkPlanningTile(level, key);
                    if (hasExistingRoads) {
                        ensureTerrainMapTilesAsync(level, key, minX, minZ, maxX, maxZ);
                    }
                    if (error != null) {
                        if (backfillPlan) unmarkRoadBackfillPlanTile(level, key);
                        LOGGER.warn("动态规划 tile 失败，保留重试机会 dimension={} tile=[{},{}]", level.dimension().location(), kx, kz, error);
                        return;
                    }
                    markPlannedTile(level, key, pcx, pcz);
                    prunePlannedIfTooLarge(level);
                } finally {
                    finishDynamicPlanningSampling(level, samplingActivity);
                }
            });
        } catch (RuntimeException submissionFailure) {
            unmarkPlanningTile(level, key);
            if (backfillPlan) {
                unmarkRoadBackfillPlanTile(level, key);
            }
            finishDynamicPlanningSampling(level, samplingActivity);
            throw submissionFailure;
        }
    }

    private static boolean markRoadBackfillPlanTile(ServerLevel level, long tileKey) {
        return ROAD_BACKFILL_PLAN_TILES.add(tileKey);
    }

    private static void unmarkRoadBackfillPlanTile(ServerLevel level, long tileKey) {
        ROAD_BACKFILL_PLAN_TILES.remove(tileKey);
    }

    private static boolean markPlanningTile(ServerLevel level, long tileKey) {
        return PLANNING_TILES.add(tileKey);
    }

    private static void unmarkPlanningTile(ServerLevel level, long tileKey) {
        PLANNING_TILES.remove(tileKey);
    }

    private static AutomaticPlanningSamplingActivities.Activity beginDynamicPlanningSampling(
            ServerLevel level,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ,
            TerrainSamplingMode mode) {
        if (mode == null || mode == TerrainSamplingMode.LEGACY_DIRECT) {
            return null;
        }
        AutomaticPlanningSamplingActivities.Activity activity = AutomaticPlanningSamplingActivities.begin(
                level,
                new AutomaticPlanningSamplingBounds(minBlockX, minBlockZ, maxBlockX, maxBlockZ));
        publishDynamicPlanningSampling(level);
        return activity;
    }

    private static void finishDynamicPlanningSampling(ServerLevel level,
                                                       AutomaticPlanningSamplingActivities.Activity activity) {
        if (activity == null) {
            return;
        }
        activity.close();
        publishDynamicPlanningSampling(level);
    }

    private static void publishDynamicPlanningSampling(ServerLevel level) {
        if (level == null) {
            return;
        }
        try {
            ServerMapPlanningActivityBridge.broadcast(level);
        } catch (RuntimeException failure) {
            LOGGER.debug("自动规划采样范围同步失败 dimension={}", level.dimension().location(), failure);
        }
    }

    private static void markPlannedTile(ServerLevel level, long tileKey, int centerChunkX, int centerChunkZ) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Set<Long> set = new HashSet<>(provider.getPlannedTileKeys(level));
        set.add(tileKey);
        Map<Long, Long> centers = new HashMap<>(provider.getPlannedTileCenters(level));
        centers.putIfAbsent(tileKey, (((long) centerChunkX) << 32) ^ (centerChunkZ & 0xffffffffL));
        provider.setPlannedTileKeys(level, set);
        provider.setPlannedTileCenters(level, centers);
    }

    private static void ensureTerrainMapTilesAsync(ServerLevel level,
                                                   long tileKey,
                                                   int minBlockX,
                                                   int minBlockZ,
                                                   int maxBlockX,
                                                   int maxBlockZ) {
        TerrainSamplingMode effectiveMode = TerrainSamplingSessions.forLevel(level).effectiveMode();
        if (effectiveMode == TerrainSamplingMode.LEGACY_DIRECT) {
            return;
        }
        MapTileLayer layer = effectiveMode == TerrainSamplingMode.FULL_REGION
                ? MapTileLayer.TERRAIN_ACCURATE
                : MapTileLayer.TERRAIN_COARSE;
        TerrainRepairKey repairKey = new TerrainRepairKey(tileKey, layer);
        if (TERRAIN_REPAIR_TILES.contains(repairKey)) return;
        if (hasCompleteTerrainTiles(level, layer, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            TERRAIN_REPAIR_TILES.add(repairKey);
            return;
        }
        if (!TERRAIN_REPAIR_TILES.add(repairKey)) return;
        ComputeService.runAsync(ThreadPoolManager.TaskRole.MAP, () -> {
            CoarseTerrainRegion region = null;
            boolean completed = false;
            try {
                ModConfig cfg = ConfigService.get();
                int step = cfg.pathfindingCost().effectiveAStarStep();
                if (layer == MapTileLayer.TERRAIN_ACCURATE) {
                    completed = AccurateTerrainTileBackfillService.backfillMissing(
                            level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, step);
                } else {
                    region = CoarseTerrainRegionSampler.sample(
                            level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, step);
                    CoarseTerrainPngWriter.writeTerrainTiles(level, region);
                    completed = true;
                }
                if (completed) {
                    LOGGER.info("已补全规划区域地图瓦片 dimension={} layer={} min=({}, {}) max=({}, {})",
                            level.dimension().location(), layer, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("补全规划区域地图瓦片失败 dimension={} layer={}",
                        level.dimension().location(), layer, e);
            } finally {
                if (region != null) region.dispose();
                if (!completed) {
                    TERRAIN_REPAIR_TILES.remove(repairKey);
                }
            }
        });
    }

    private static boolean hasCompleteTerrainTiles(ServerLevel level,
                                                   MapTileLayer layer,
                                                   int minBlockX,
                                                   int minBlockZ,
                                                   int maxBlockX,
                                                   int maxBlockZ) {
        for (int zoom = MapTileScheme.MIN_ZOOM; zoom <= MapTileScheme.MAX_ZOOM; zoom++) {
            MapTileRect rect = MapTileScheme.tileRectForBlockRect(zoom, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            for (MapTileCoord coord : rect.coords()) {
                int intersectionMinX = Math.max(minBlockX, MapTileScheme.tileMinBlockX(coord));
                int intersectionMinZ = Math.max(minBlockZ, MapTileScheme.tileMinBlockZ(coord));
                int intersectionMaxX = Math.min(maxBlockX, MapTileScheme.tileMaxBlockX(coord));
                int intersectionMaxZ = Math.min(maxBlockZ, MapTileScheme.tileMaxBlockZ(coord));
                if (!ServerMapTileStorage.hasCoverage(
                        level, layer, coord,
                        intersectionMinX, intersectionMinZ, intersectionMaxX, intersectionMaxZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        List<BlockPos> points = new ArrayList<>();
        HashSet<Long> seenPos = new HashSet<>();
        collectStructurePointsInto(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, points, seenPos);
        if (points.size() < 2) return;

        ModConfig cfg0 = ConfigService.get();
        NetworkPlanner planner = NetworkPlannerFactory.create(cfg0.planning().planningAlgorithm());
        List<StructureConnection> primaryEdges = planner.plan(points, RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS);
        if (primaryEdges.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);

        ArrayList<StructureConnection> existingInRect = new ArrayList<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (inRect2d(c.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)
                        && inRect2d(c.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    existingInRect.add(c);
                }
            }
        }

        ArrayList<StructureConnection> base = new ArrayList<>(existingInRect);
        base.addAll(primaryEdges);
        ArrayList<BlockPos> componentPoints = collectComponentPoints(points, existingInRect, primaryEdges);

        List<StructureConnection> bridges = KNNPlanner.connectComponents(
                componentPoints, base,
                RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS,
                RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP
        );

        ArrayList<StructureConnection> incoming = new ArrayList<>(primaryEdges);
        incoming.addAll(bridges);
        List<StructureConnection> merged = mergeConnections(existing, incoming);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setStructureConnections(level, merged);
            publishNewConnections(level, existing, incoming);
        }
        planRoadTerrain(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, incoming);
    }

    private static void collectStructurePointsInto(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, List<BlockPos> out, Set<Long> seenPos) {
        StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<StructureInfo> cached = StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (cached == null || cached.isEmpty()) return;
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            BlockPos p = info.pos();
            int x = p.getX(), z = p.getZ();
            if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) continue;
            BlockPos q = new BlockPos(x, 0, z);
            long key = PlanningUtils.pos2dKey(q);
            if (seenPos.add(key)) out.add(q);
        }
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        return planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    private record PlannedRegionResult(List<StructureConnection> incoming) {}

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        final long epoch = ThreadPoolManager.currentEpoch();
        final List<StructureConnection> existingSnapshot;
        {
            WorldDataProvider prov = WorldDataProvider.getInstance();
            List<StructureConnection> ex = prov.getStructureConnections(level);
            existingSnapshot = ex != null ? new ArrayList<>(ex) : new ArrayList<>();
        }
        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new PlannedRegionResult(List.of());
            if (!ThreadPoolManager.isEpoch(epoch)) return new PlannedRegionResult(List.of());
            ArrayList<BlockPos> points = new ArrayList<>();
            HashSet<Long> seenPos = new HashSet<>();
            collectStructurePointsInto(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, points, seenPos);
            for (StructureConnection c : existingSnapshot) {
                BlockPos f = new BlockPos(c.from().getX(), 0, c.from().getZ());
                BlockPos t = new BlockPos(c.to().getX(), 0, c.to().getZ());
                if (inRect2d(f, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    long kf = PlanningUtils.pos2dKey(f);
                    if (seenPos.add(kf)) points.add(f);
                }
                if (inRect2d(t, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    long kt = PlanningUtils.pos2dKey(t);
                    if (seenPos.add(kt)) points.add(t);
                }
            }
            if (points.size() < 2) return new PlannedRegionResult(List.of());

            ArrayList<StructureConnection> existingInRect = new ArrayList<>();
            HashSet<PlannedPathKey> existingEdgeKeys = new HashSet<>();
            for (StructureConnection c : existingSnapshot) {
                if (inRect2d(c.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ) &&
                        inRect2d(c.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    existingInRect.add(c);
                    existingEdgeKeys.add(PlannedPathKey.of(c));
                }
            }

            ModConfig cfg0 = ConfigService.get();
            NetworkPlanner planner = NetworkPlannerFactory.create(cfg0.planning().planningAlgorithm());
            List<StructureConnection> primaryEdges = planner.plan(points, RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS);
            if (primaryEdges.isEmpty() && existingInRect.isEmpty()) return new PlannedRegionResult(List.of());

            ArrayList<StructureConnection> filteredPrimary = new ArrayList<>();
            for (StructureConnection c : primaryEdges) {
                if (!existingEdgeKeys.contains(PlannedPathKey.of(c))) filteredPrimary.add(c);
            }

            ArrayList<StructureConnection> base = new ArrayList<>(existingInRect);
            base.addAll(filteredPrimary);
            ArrayList<BlockPos> componentPoints = collectComponentPoints(points, existingInRect, filteredPrimary);
            List<StructureConnection> bridges = KNNPlanner.connectComponents(
                    componentPoints, base,
                    RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS,
                    RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                    RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP
            );

            ArrayList<StructureConnection> incoming = new ArrayList<>(filteredPrimary);
            incoming.addAll(bridges);
            planRoadTerrain(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, incoming);
            return new PlannedRegionResult(incoming);
        }).thenAccept((PlannedRegionResult result) -> {
            if (result == null || result.incoming().isEmpty()) return;
            publishIncomingConnections(level, epoch, result.incoming());
        });
    }

    private static void publishIncomingConnections(ServerLevel level, long epoch, List<StructureConnection> incoming) {
        if (level == null || incoming == null || incoming.isEmpty()) return;
        if (!ThreadPoolManager.isEpoch(epoch)) return;
        var server = level.getServer();
        if (server == null) return;
        ArrayList<StructureConnection> snapshot = new ArrayList<>(incoming);
        server.execute(() -> {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            WorldDataProvider provider = WorldDataProvider.getInstance();
            List<StructureConnection> existing = provider.getStructureConnections(level);
            List<StructureConnection> merged = mergeConnections(existing, snapshot);
            if (merged.size() != (existing == null ? 0 : existing.size())) {
                provider.setStructureConnections(level, merged);
                publishNewConnections(level, existing, snapshot);
            }
        });
    }

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing, List<StructureConnection> incoming) {
        HashSet<PlannedPathKey> seen = new HashSet<>();
        ArrayList<StructureConnection> out = new ArrayList<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (seen.add(PlannedPathKey.of(c))) out.add(c);
            }
        }
        for (StructureConnection c : incoming) {
            if (seen.add(PlannedPathKey.of(c))) {
                out.add(new StructureConnection(c.from(), c.to(), ConnectionStatus.PLANNED));
            }
        }
        return out;
    }

    private static void publishNewConnections(ServerLevel level,
                                              List<StructureConnection> existing,
                                              List<StructureConnection> incoming) {
        if (level == null || incoming == null || incoming.isEmpty()) return;
        HashSet<PlannedPathKey> existingKeys = new HashSet<>();
        if (existing != null) {
            for (StructureConnection connection : existing) {
                if (connection != null) {
                    existingKeys.add(PlannedPathKey.of(connection));
                }
            }
        }
        for (StructureConnection connection : incoming) {
            if (connection == null) continue;
            if (existingKeys.add(PlannedPathKey.of(connection))) {
                MapPatchService.publishConnection(level,
                        new StructureConnection(connection.from(), connection.to(), ConnectionStatus.PLANNED));
            }
        }
    }

    private static void planRoadTerrain(ServerLevel level,
                                        int minBlockX,
                                        int minBlockZ,
                                        int maxBlockX,
                                        int maxBlockZ,
                                        List<StructureConnection> connections) {
        if (level == null || connections == null || connections.isEmpty()) return;
        ModConfig config = ConfigService.get();
        TerrainSamplingMode mode = config.planning().terrainSamplingMode();
        if (mode == null || mode == TerrainSamplingMode.LEGACY_DIRECT) {
            return;
        }
        RoadTerrainPlanningPort.Request request = new RoadTerrainPlanningPort.Request(
                level,
                new RoadTerrainPlanningPort.Bounds(minBlockX, minBlockZ, maxBlockX, maxBlockZ),
                connections,
                config.pathfindingCost());
        RoadTerrainPlanningPort.Result result = TERRAIN_PLANNING.plan(request);
        PlannedPathCache.register(level, result.mode(), config.pathfindingCost(), result.paths());
    }

    private static ArrayList<BlockPos> collectComponentPoints(List<BlockPos> seed,
                                                              List<StructureConnection> existingInRect,
                                                              List<StructureConnection> incomingEdges) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        if (seed != null) {
            for (BlockPos p : seed) addUnique2d(out, seen, p);
        }
        if (existingInRect != null) {
            for (StructureConnection c : existingInRect) {
                if (c == null) continue;
                addUnique2d(out, seen, c.from());
                addUnique2d(out, seen, c.to());
            }
        }
        if (incomingEdges != null) {
            for (StructureConnection c : incomingEdges) {
                if (c == null) continue;
                addUnique2d(out, seen, c.from());
                addUnique2d(out, seen, c.to());
            }
        }
        return out;
    }

    private static void addUnique2d(List<BlockPos> out, Set<Long> seen, BlockPos p) {
        if (p == null) return;
        BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
        long k = PlanningUtils.pos2dKey(q);
        if (seen.add(k)) out.add(q);
    }

    private static boolean inRect2d(BlockPos p, int minX, int minZ, int maxX, int maxZ) {
        if (p == null) return false;
        int x = p.getX();
        int z = p.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    public static Set<Long> getPlannedTiles(ServerLevel level) {
        Set<Long> s = WorldDataProvider.getInstance().getPlannedTileKeys(level);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    public static Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Map<Long, Long> m = WorldDataProvider.getInstance().getPlannedTileCenters(level);
        if (m == null || m.isEmpty()) return Map.of();
        return Map.copyOf(m);
    }

    public static int getStrideTileSizeChunks() {
        ModConfig cfg = ConfigService.get();
        int stride = Math.max(1, cfg.planning().dynamicPlanStrideChunks());
        return Math.max(RoadConstants.PLAN_TILE_MIN, Math.min(RoadConstants.PLAN_TILE_MAX, stride));
    }

    public static int getDynamicPlanRadiusChunks() {
        ModConfig cfg = ConfigService.get();
        return Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
    }

    public static void resetAll() {
        TERRAIN_REPAIR_TILES.clear();
        ROAD_BACKFILL_PLAN_TILES.clear();
        PLANNING_TILES.clear();
        AutomaticPlanningSamplingActivities.clearAll();
    }

    private record TerrainRepairKey(long tileKey, MapTileLayer layer) {}
}
