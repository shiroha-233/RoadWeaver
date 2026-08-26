package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.core.Road;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarsePathCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionRegistry;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileCache;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 道路生成调度器，管理 Path 队列的 tick 驱动异步生成
 */
public final class RoadGenerationService {
    private RoadGenerationService() {}

    private static final ConcurrentLinkedQueue<StructureConnection> QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Long, Boolean> PROCESSED = new ConcurrentHashMap<>();
    private static final AtomicInteger RUNNING_COUNT = new AtomicInteger();
    private static final Set<Future<?>> ALL_RUNNING = ConcurrentHashMap.newKeySet();
    private static final CachedPlayerList PLAYER_LIST_CACHE = new CachedPlayerList();
    private static final int PLAYER_LIST_REFRESH_TICKS = 5;
    private static long CURRENT_TICK = 0L;

    public static long currentTick() {
        return CURRENT_TICK;
    }

    private static final ResourceLocation ROAD_CF_ID = new ResourceLocation("roadweaver", "road_feature");

    public static void onServerStarted() {
        ALL_RUNNING.clear();
        QUEUE.clear();
        PROCESSED.clear();
        RUNNING_COUNT.set(0);
        PLAYER_LIST_CACHE.clear();
        CoarsePathCache.clearAll();
        CoarseTerrainRegionRegistry.clearAll();
        CoarseTerrainTileCache.clearAll();
    }

    public static void onServerStopping() {
        ALL_RUNNING.forEach(f -> f.cancel(true));
        ALL_RUNNING.clear();
        QUEUE.clear();
        PROCESSED.clear();
        RUNNING_COUNT.set(0);
        PLAYER_LIST_CACHE.clear();
        RoadPlanningService.resetAll();
        CoarsePathCache.clearAll();
        CoarseTerrainRegionRegistry.clearAll();
        CoarseTerrainTileCache.clearAll();
        IdleRoadGenerationService.onServerStopping();
    }

    // ==================== tick 调度 ====================

    public static void tick(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        CURRENT_TICK++;
        refreshQueue(level);
        ALL_RUNNING.removeIf(f -> f == null || f.isDone() || f.isCancelled());

        ConcurrentLinkedQueue<StructureConnection> q = QUEUE;
        if (q.isEmpty()) return;

        int limit = Math.max(1, ConfigService.get().performance().maxConcurrentGenerations());
        AtomicInteger cnt = RUNNING_COUNT;

        List<ServerPlayer> players = getCachedPlayers(level);
        if (players == null) return;

        while (cnt.get() < limit) {
            if (q.isEmpty()) break;

            StructureConnection conn = pollNearest(q, players);
            if (conn == null) continue;

            updateConnectionStatus(level, conn, ConnectionStatus.GENERATING);

            final StructureConnection task = conn;
            cnt.incrementAndGet();
            long epoch = ThreadPoolManager.currentEpoch();

            Future<?> fut = ThreadPoolManager.submit(ThreadPoolManager.TaskRole.GENERATION, () -> {
                try {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (!ThreadPoolManager.isEpoch(epoch)) return;
                    safeGenerate(level, task, epoch);
                } finally {
                    cnt.decrementAndGet();
                }
            });
            ALL_RUNNING.add(fut);
        }
    }

    // ==================== 纯生成逻辑（无副作用） ====================

    public static boolean generateTask(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        try {
            if (Thread.currentThread().isInterrupted()) return false;

            var reg = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> cf = reg.get(ROAD_CF_ID);
            PathFeatureConfig cfg = (cf != null && cf.config() instanceof PathFeatureConfig rfc) ? rfc : new PathFeatureConfig();

            if (Thread.currentThread().isInterrupted()) return false;
            ModConfig modCfg = ConfigService.get();
            if (!modCfg.roadAppearance().roadsEnabled()) return true;

            RoadGenerationConfig genCfg = RoadGenerationConfig.from(modCfg);
            return new Road(level, conn, cfg, genCfg)
                    .generateRoad(modCfg.pathfindingCost().aStarMaxSteps()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 安全生成包装 ====================

    private static void safeGenerate(ServerLevel level, StructureConnection conn, long epoch) {
        try {
            if (Thread.currentThread().isInterrupted()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;

            boolean ok = generateTask(level, conn);
            ConnectionStatus st = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;

            executeOnMainThread(level, epoch, () -> {
                updateConnectionStatus(level, conn, st);
                removeProcessed(level, conn);
                if (st == ConnectionStatus.COMPLETED) {
                    RoadSnapService.snapAroundConnectionAsync(level, conn.from(), conn.to());
                }
            });
        } catch (Throwable t) {
            executeOnMainThread(level, epoch, () -> {
                updateConnectionStatus(level, conn, ConnectionStatus.FAILED);
                removeProcessed(level, conn);
            });
        }
    }

    private static void executeOnMainThread(ServerLevel level, long epoch, Runnable action) {
        var server = level.getServer();
        if (server != null) {
            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                action.run();
            });
        } else {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
        }
    }

    // ==================== 状态更新辅助 ====================

    private static void updateConnectionStatus(ServerLevel level, StructureConnection conn, ConnectionStatus status) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getStructureConnections(level);
        List<StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
        StructureConnection updated = null;
        for (int i = 0; i < all.size(); i++) {
            StructureConnection c = all.get(i);
            if (PlanningUtils.sameEdge(c, conn)) {
                updated = new StructureConnection(c.from(), c.to(), status);
                all.set(i, updated);
                break;
            }
        }
        provider.setStructureConnections(level, all);
        if (updated != null) {
            MapPatchService.publishConnection(level, updated);
            if (status == ConnectionStatus.COMPLETED) {
                MapPatchService.publishRoadForConnectionAsync(level, updated);
            }
        }
    }

    private static void removeProcessed(ServerLevel level, StructureConnection conn) {
        long k = PlanningUtils.edgeKey(conn.from(), conn.to());
        PROCESSED.remove(k);
    }

    // ==================== 队列刷新 ====================

    private static void refreshQueue(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> list = provider.getStructureConnections(level);
        if (list == null) return;

        ConcurrentLinkedQueue<StructureConnection> q = QUEUE;
        ConcurrentHashMap<Long, Boolean> proc = PROCESSED;

        for (StructureConnection c : list) {
            if (c.status() != ConnectionStatus.PLANNED && c.status() != ConnectionStatus.GENERATING) continue;
            if (c.status() == ConnectionStatus.PLANNED
                    && IdleRoadGenerationService.shouldReserveForIdle(level, c)) {
                continue;
            }
            if (c.status() == ConnectionStatus.GENERATING
                    && IdleRoadGenerationService.isManagedByIdle(level, c)) {
                continue;
            }
            long key = PlanningUtils.edgeKey(c.from(), c.to());
            if (proc.putIfAbsent(key, Boolean.TRUE) == null) {
                q.add(c);
            }
        }
    }

    // ==================== 优先级轮询 ====================

    private static StructureConnection pollNearest(ConcurrentLinkedQueue<StructureConnection> q, List<ServerPlayer> players) {
        if (q.isEmpty()) return null;
        if (players == null || players.isEmpty()) return q.poll();

        StructureConnection best = null;
        long bestDist = Long.MAX_VALUE;
        for (StructureConnection e : q) {
            long d = playerDistance2(e, players);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best != null && q.remove(best)) return best;
        return q.poll();
    }

    private static long playerDistance2(StructureConnection c, List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) return Long.MAX_VALUE;
        long best = Long.MAX_VALUE;
        int mx = (c.from().getX() + c.to().getX()) >> 1;
        int mz = (c.from().getZ() + c.to().getZ()) >> 1;
        BlockPos mid = new BlockPos(mx, 0, mz);
        for (ServerPlayer p : players) {
            BlockPos pb = p.blockPosition();
            best = Math.min(best, dist2XZ(pb, c.from()));
            best = Math.min(best, dist2XZ(pb, c.to()));
            best = Math.min(best, dist2XZ(pb, mid));
        }
        return best;
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static List<ServerPlayer> getCachedPlayers(ServerLevel level) {
        CachedPlayerList cache = PLAYER_LIST_CACHE;
        if (CURRENT_TICK - cache.lastTick >= PLAYER_LIST_REFRESH_TICKS || cache.players == null) {
            List<ServerPlayer> fresh = new ArrayList<>();
            var server = level.getServer();
            if (server == null) return null;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != null && p.serverLevel() == level) fresh.add(p);
            }
            cache.players = fresh;
            cache.lastTick = CURRENT_TICK;
        }
        return cache.players;
    }

    private static final class CachedPlayerList {
        long lastTick = Long.MIN_VALUE;
        List<ServerPlayer> players = null;

        void clear() {
            lastTick = Long.MIN_VALUE;
            players = null;
        }
    }
}
