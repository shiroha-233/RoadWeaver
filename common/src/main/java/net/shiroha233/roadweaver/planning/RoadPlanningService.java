package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

public final class RoadPlanningService {
    private RoadPlanningService() {}

    private static final ConcurrentHashMap<Level, Set<Long>> PLANNED_TILES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Level, java.util.concurrent.ConcurrentHashMap<Long, Long>> PLANNED_TILE_CENTERS = new ConcurrentHashMap<>();
    private static final int MAX_PLANNED_KEYS = 200_000;

    private static void prunePlannedIfTooLarge(Level level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        java.util.Set<Long> keys = new java.util.HashSet<>(provider.getPlannedTileKeys((ServerLevel) level));
        if (keys.size() > MAX_PLANNED_KEYS) {
            int remove = keys.size() - MAX_PLANNED_KEYS;
            java.util.Iterator<Long> it = keys.iterator();
            while (remove > 0 && it.hasNext()) { it.next(); it.remove(); remove--; }
            provider.setPlannedTileKeys((ServerLevel) level, keys);
        }
        java.util.Map<Long, Long> centers = new java.util.HashMap<>(provider.getPlannedTileCenters((ServerLevel) level));
        if (centers.size() > MAX_PLANNED_KEYS) {
            int remove2 = centers.size() - MAX_PLANNED_KEYS;
            java.util.Iterator<Long> it2 = centers.keySet().iterator();
            while (remove2 > 0 && it2.hasNext()) { it2.next(); it2.remove(); remove2--; }
            provider.setPlannedTileCenters((ServerLevel) level, centers);
        }
    }

    public static void initialPlan(ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        planRect(level, minX, minZ, maxX, maxZ);
    }

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (!cfg.dynamicPlanEnabled()) return;
        int radiusChunks = Math.max(1, cfg.dynamicPlanRadiusChunks());
        int stride = Math.max(1, cfg.dynamicPlanStrideChunks());
        int tile = Math.max(8, Math.min(256, stride));
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        int kx = floorDiv(pcx, tile);
        int kz = floorDiv(pcz, tile);
        long key = (((long) kx) << 32) ^ (kz & 0xffffffffL);
        WorldDataProvider provider0 = WorldDataProvider.getInstance();
        java.util.Set<Long> set = new java.util.HashSet<>(provider0.getPlannedTileKeys(level));
        boolean isNewTile = set.add(key);
        java.util.Map<Long, Long> centers = new java.util.HashMap<>(provider0.getPlannedTileCenters(level));
        centers.putIfAbsent(key, (((long) pcx) << 32) ^ (pcz & 0xffffffffL));
        provider0.setPlannedTileKeys(level, set);
        provider0.setPlannedTileCenters(level, centers);
        if (!isNewTile) return; 

        int minX = (pcx - radiusChunks) * 16;
        int maxX = (pcx + radiusChunks) * 16;
        int minZ = (pcz - radiusChunks) * 16;
        int maxZ = (pcz + radiusChunks) * 16;
        prunePlannedIfTooLarge(level);
        planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        MapSnapshot snap = MapDataCollector.build(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<BlockPos> points = new ArrayList<>();
        HashSet<Long> seenPos = new HashSet<>();
        for (BlockPos p : snap.structures()) {
            BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
            long key = PlanningUtils.pos2dKey(q);
            if (seenPos.add(key)) points.add(q);
        }
        if (points.size() < 2) return;

        List<Records.StructureConnection> primaryEdges;
        ModConfig cfg0 = ConfigService.get();
        if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.DELAUNAY) {
            primaryEdges = DelaunayPlanner.planDelaunay(points, 2048);
        } else if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.RNG) {
            primaryEdges = RNGPlanner.planRNG(points, 2048);
        } else {
            primaryEdges = KNNPlanner.planKNN(points, 2, 2048, 1.8, 40.0, 2);
        }
        if (primaryEdges.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);

        HashSet<BlockPos> inRect = new HashSet<>(points);
        ArrayList<Records.StructureConnection> existingInRect = new ArrayList<>();
        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                if (inRect.contains(c.from()) && inRect.contains(c.to())) existingInRect.add(c);
            }
        }

        ArrayList<Records.StructureConnection> base = new ArrayList<>(existingInRect);
        base.addAll(primaryEdges);

        List<Records.StructureConnection> bridges = KNNPlanner.connectComponents(points, base, 1536, 35.0, 3);

        ArrayList<Records.StructureConnection> incoming = new ArrayList<>(primaryEdges);
        incoming.addAll(bridges);
        List<Records.StructureConnection> merged = mergeConnections(existing, incoming);
        if (merged.size() != existing.size()) {
            provider.setStructureConnections(level, merged);
        }
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        return planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        final long epoch = ThreadPoolManager.currentEpoch();
        // 预先获取已有连接，用于增量规划时保持一致性
        final List<Records.StructureConnection> existingSnapshot;
        {
            WorldDataProvider prov = WorldDataProvider.getInstance();
            List<Records.StructureConnection> ex = prov.getStructureConnections(level);
            existingSnapshot = ex != null ? new ArrayList<>(ex) : new ArrayList<>();
        }
        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<Records.StructureConnection>();
            if (!ThreadPoolManager.isEpoch(epoch)) return new ArrayList<Records.StructureConnection>();
            MapSnapshot snap = MapDataCollector.build(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            ArrayList<BlockPos> points = new ArrayList<>();
            HashSet<Long> seenPos = new HashSet<>();
            for (BlockPos p : snap.structures()) {
                BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
                long key = PlanningUtils.pos2dKey(q);
                if (seenPos.add(key)) points.add(q);
            }
            // 将已有边的端点也纳入点集，确保 RNG/Delaunay 能看到全局拓扑
            for (Records.StructureConnection c : existingSnapshot) {
                BlockPos f = new BlockPos(c.from().getX(), 0, c.from().getZ());
                BlockPos t = new BlockPos(c.to().getX(), 0, c.to().getZ());
                long kf = PlanningUtils.pos2dKey(f);
                long kt = PlanningUtils.pos2dKey(t);
                if (seenPos.add(kf)) points.add(f);
                if (seenPos.add(kt)) points.add(t);
            }
            if (points.size() < 2) return new ArrayList<Records.StructureConnection>();
            
            // 收集矩形内的已有边
            HashSet<BlockPos> inRect = new HashSet<>(points);
            ArrayList<Records.StructureConnection> existingInRect = new ArrayList<>();
            HashSet<Long> existingEdgeKeys = new HashSet<>();
            for (Records.StructureConnection c : existingSnapshot) {
                if (inRect.contains(new BlockPos(c.from().getX(), 0, c.from().getZ())) &&
                    inRect.contains(new BlockPos(c.to().getX(), 0, c.to().getZ()))) {
                    existingInRect.add(c);
                    existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                }
            }
            
            List<Records.StructureConnection> primaryEdges;
            ModConfig cfg0 = ConfigService.get();
            if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.DELAUNAY) {
                primaryEdges = DelaunayPlanner.planDelaunay(points, 2048);
            } else if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.RNG) {
                primaryEdges = RNGPlanner.planRNG(points, 2048);
            } else {
                primaryEdges = KNNPlanner.planKNN(points, 2, 2048, 1.8, 40.0, 2);
            }
            if (primaryEdges.isEmpty() && existingInRect.isEmpty()) return new ArrayList<Records.StructureConnection>();
            
            // 过滤掉与已有边冲突的新边（保持规划一致性）
            ArrayList<Records.StructureConnection> filteredPrimary = new ArrayList<>();
            for (Records.StructureConnection c : primaryEdges) {
                long ek = PlanningUtils.edgeKey(c.from(), c.to());
                // 只添加已有边中不存在的新边，避免重复
                if (!existingEdgeKeys.contains(ek)) {
                    filteredPrimary.add(c);
                }
            }
            
            // 将已有边和新边合并作为 base 进行连通性修复
            ArrayList<Records.StructureConnection> base = new ArrayList<>(existingInRect);
            base.addAll(filteredPrimary);
            List<Records.StructureConnection> bridges = KNNPlanner.connectComponents(points, base, 1536, 35.0, 3);
            
            ArrayList<Records.StructureConnection> incoming = new ArrayList<>(filteredPrimary);
            incoming.addAll(bridges);
            return incoming;
        }).thenAccept(incoming -> {
            if (incoming == null || incoming.isEmpty()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<Records.StructureConnection> existing = provider.getStructureConnections(level);
                List<Records.StructureConnection> merged = mergeConnections(existing, incoming);
                if (merged.size() != (existing == null ? 0 : existing.size())) {
                    provider.setStructureConnections(level, merged);
                }
            });
        });
    }

    private static List<Records.StructureConnection> mergeConnections(List<Records.StructureConnection> existing,
                                                                      List<Records.StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<Records.StructureConnection> out = new ArrayList<>();
        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k)) out.add(c);
            }
        }
        for (Records.StructureConnection c : incoming) {
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) out.add(new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.PLANNED));
        }
        return out;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    public static Set<Long> getPlannedTiles(ServerLevel level) {
        java.util.Set<Long> s = WorldDataProvider.getInstance().getPlannedTileKeys(level);
        return s == null ? java.util.Set.of() : java.util.Set.copyOf(s);
    }

    public static java.util.Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        java.util.Map<Long, Long> m = WorldDataProvider.getInstance().getPlannedTileCenters(level);
        if (m == null || m.isEmpty()) return java.util.Map.of();
        return java.util.Map.copyOf(m);
    }

    public static int getStrideTileSizeChunks() {
        ModConfig cfg = ConfigService.get();
        int stride = Math.max(1, cfg.dynamicPlanStrideChunks());
        return Math.max(8, Math.min(256, stride));
    }

    public static int getDynamicPlanRadiusChunks() {
        ModConfig cfg = ConfigService.get();
        return Math.max(1, cfg.dynamicPlanRadiusChunks());
    }

    public static void resetAll() {
        PLANNED_TILES.clear();
        PLANNED_TILE_CENTERS.clear();
    }
}
