/* 文件职责：协调首次世界加载时的道路规划、生成、进度与持久化。 */
package net.shiroha233.roadweaver.generation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressSnapshot;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 初始道路生成管理器，服务器启动时阻塞生成初始规划范围内的道路并提供进度统计
 */
public final class InitialGenManager {
    private InitialGenManager() {}

    private static volatile boolean active;
    private static final AtomicInteger total = new AtomicInteger(0);
    private static final AtomicInteger done = new AtomicInteger(0);
    private static final AtomicInteger generating = new AtomicInteger(0);
    private static final AtomicInteger failed = new AtomicInteger(0);

    public static boolean isActive() { return active; }
    public static int getTotal() { return total.get(); }
    public static int getDone() { return done.get(); }
    public static int getGenerating() { return generating.get(); }
    public static int getFailed() { return failed.get(); }
    public static InitialGenerationProgressSnapshot getProgressSnapshot() {
        return InitialGenerationProgressTracker.snapshot(active);
    }

    public static void begin(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        if (!shouldRunInitialGeneration(level)) return;

        active = true;
        total.set(0);
        done.set(0);
        generating.set(0);
        failed.set(0);
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.updateConnections(0, 0, 0, 0);

        net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats.reset();
        net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats.reset();

        RoadGenerationService.onServerStarted();

        ModConfig cfg = ConfigService.get();
        if (cfg.roadAppearance().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level);
        }

        RoadPlanningService.initialPlan(level);

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> conns = provider.getStructureConnections(level);
        total.set(conns == null ? 0 : conns.size());
        InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.PLANNING, "planning_initial_roads");
        update(level);
    }

    public static boolean shouldRunInitialGeneration(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> conns = provider.getStructureConnections(level);
        if (conns != null && !conns.isEmpty()) return false;
        return !RoadShardStorage.hasAnyRoad(level);
    }

    public static void blockUntilDone(ServerLevel level) {
        if (!active || level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        WorldDataProvider provider = WorldDataProvider.getInstance();

        List<StructureConnection> list = provider.getStructureConnections(level);
        if (list == null || list.isEmpty()) {
            active = false;
            InitialGenerationProgressTracker.finish();
            return;
        }
        List<StructureConnection> roadTasks = filterPlanned(list);
        total.set(roadTasks.size());
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.ROAD_GENERATION, "generating_roads");
        InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());

        if (!roadTasks.isEmpty()) {
            Map<PlannedPathKey, Boolean> results = submitAndCollect(level, roadTasks);
            batchUpdateConnectionStatus(provider, level, results);
        }
        snapInitialRoads(level, list);
        flushAndFinish(level);
    }

    private static void snapInitialRoads(ServerLevel level, List<StructureConnection> conns) {
        if (conns == null || conns.isEmpty()) return;
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.POST_PROCESSING, "snapping_roads");
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureConnection c : conns) {
            minX = Math.min(minX, Math.min(c.from().getX(), c.to().getX()));
            minZ = Math.min(minZ, Math.min(c.from().getZ(), c.to().getZ()));
            maxX = Math.max(maxX, Math.max(c.from().getX(), c.to().getX()));
            maxZ = Math.max(maxZ, Math.max(c.from().getZ(), c.to().getZ()));
        }
        RoadSnapService.snapAllRoads(level, minX, minZ, maxX, maxZ);
    }

    // ==================== 提取的通用辅助方法 ====================

    private static List<StructureConnection> filterPlanned(List<StructureConnection> list) {
        List<StructureConnection> out = new ArrayList<>();
        for (StructureConnection c : list) {
            if (c.status() == ConnectionStatus.PLANNED) out.add(c);
        }
        return out;
    }

    private record GenResult(PlannedPathKey key, boolean success) {}

    private static Map<PlannedPathKey, Boolean> submitAndCollect(ServerLevel level,
                                                       List<StructureConnection> tasks) {
        List<Future<GenResult>> futures = new ArrayList<>();
        for (StructureConnection task : tasks) {
            futures.add(ThreadPoolManager.submit(ThreadPoolManager.TaskRole.INITIAL, () -> {
                generating.incrementAndGet();
                InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());
                try {
                    boolean success = RoadGenerationService.generateTask(level, task);
                    if (success) done.incrementAndGet(); else failed.incrementAndGet();
                    InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());
                    return new GenResult(PlannedPathKey.of(task), success);
                } finally {
                    generating.decrementAndGet();
                    InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());
                }
            }));
        }

        Map<PlannedPathKey, Boolean> results = new HashMap<>();
        for (Future<GenResult> future : futures) {
            try {
                GenResult result = future.get();
                if (result != null) {
                    results.put(result.key(), result.success());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (CancellationException | ExecutionException ignored) {
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            for (Future<GenResult> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
        return results;
    }

    private static void batchUpdateConnectionStatus(WorldDataProvider provider,
                                                    ServerLevel level,
                                                    Map<PlannedPathKey, Boolean> results) {
        if (results.isEmpty()) return;

        List<StructureConnection> current = provider.getStructureConnections(level);
        if (current == null) return;

        List<StructureConnection> updated = new ArrayList<>(current);
        List<StructureConnection> changedConnections = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            StructureConnection original = updated.get(i);
            PlannedPathKey k = PlannedPathKey.of(original);
            Boolean ok = results.get(k);
            if (ok == null) continue;
            ConnectionStatus newStatus = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;
            StructureConnection changedConnection = new StructureConnection(original.from(), original.to(), newStatus);
            updated.set(i, changedConnection);
            changedConnections.add(changedConnection);
            changed = true;
        }
        if (changed) {
            provider.setStructureConnections(level, updated);
            for (StructureConnection changedConnection : changedConnections) {
                MapPatchService.publishConnection(level, changedConnection);
                if (changedConnection.status() == ConnectionStatus.COMPLETED) {
                    MapPatchService.publishRoadForConnectionAsync(level, changedConnection);
                }
            }
        }
    }

    private static void flushAndFinish(ServerLevel level) {
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.POST_PROCESSING, "flushing_road_data");
        RoadShardStorage.flushAll(level);
        net.shiroha233.roadweaver.persistence.files.StructureFileStorage.flush(level);
        RoadPositionQuery.clearCache(level);
        active = false;
        InitialGenerationProgressTracker.updateConnections(total.get(), generating.get(), done.get(), failed.get());
        InitialGenerationProgressTracker.finish();
    }

    public static void update(ServerLevel level) {
        if (active) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> conns = provider.getStructureConnections(level);

        if (conns == null || conns.isEmpty()) {
            total.set(0);
            generating.set(0);
            done.set(0);
            failed.set(0);
            return;
        }

        int g = 0, c = 0, f = 0;
        for (StructureConnection sc : conns) {
            ConnectionStatus s = sc.status();
            if (s == ConnectionStatus.GENERATING) g++;
            else if (s == ConnectionStatus.COMPLETED) c++;
            else if (s == ConnectionStatus.FAILED) f++;
        }
        total.set(conns.size());
        generating.set(g);
        done.set(c);
        failed.set(f);
    }
}
