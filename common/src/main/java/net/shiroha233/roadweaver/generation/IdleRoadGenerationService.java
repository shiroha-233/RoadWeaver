/* 文件职责：在玩家空闲预算内调度并追踪延迟道路生成任务。 */
package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 闲时道路生成服务：
 * 1) 围绕玩家持续扩张规划半径；
 * 2) 将该半径内的 PLANNED 任务交给闲时线程专属生成；
 * 3) 玩家离开该半径后，任务自动释放给常规生成线程。
 */
public final class IdleRoadGenerationService {
    private IdleRoadGenerationService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAX_IDLE_RADIUS_CHUNKS = 16_384;
    private static final int IDLE_RADIUS_EXPAND_STEP_CHUNKS = 32;
    private static final int IDLE_CENTER_FOLLOW_STEP_CHUNKS = 8;

    private static final ConcurrentHashMap<UUID, IdleWindow> WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PlannedPathKey, Boolean> IDLE_OWNED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PlannedPathKey, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final AtomicInteger RUNNING = new AtomicInteger();
    private static final Set<Future<?>> ALL_RUNNING = ConcurrentHashMap.newKeySet();

    private static final class IdleWindow {
        volatile int centerChunkX;
        volatile int centerChunkZ;
        volatile int radiusChunks;
        final AtomicBoolean planning = new AtomicBoolean(false);

        IdleWindow(int centerChunkX, int centerChunkZ, int radiusChunks) {
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.radiusChunks = radiusChunks;
        }
    }

    public static void onServerStarted() {
        WINDOWS.clear();
        IDLE_OWNED.clear();
        IN_FLIGHT.clear();
        RUNNING.set(0);
        ALL_RUNNING.clear();
    }

    public static void onServerStopping() {
        ALL_RUNNING.forEach(f -> {
            try {
                if (f != null) f.cancel(true);
            } catch (Throwable ignored) {}
        });
        ALL_RUNNING.clear();
        WINDOWS.clear();
        IDLE_OWNED.clear();
        IN_FLIGHT.clear();
        RUNNING.set(0);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player == null) return;

        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (!cfg.roadAppearance().roadsEnabled()) return;

        if (!cfg.performance().idleGenerationEnabled()) return;

        int baseRadius = resolveBaseRadiusChunks(cfg);
        int expandStep = resolveExpandStepChunks(cfg);
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;

        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS;
        UUID playerId = player.getUUID();
        IdleWindow win = perLevel.get(playerId);
        if (win == null) {
            IdleWindow created = new IdleWindow(pcx, pcz, baseRadius);
            IdleWindow prev = perLevel.putIfAbsent(playerId, created);
            win = prev != null ? prev : created;
            if (prev == null) {
                trySchedulePlan(level, win, baseRadius);
                return;
            }
        }

        int dist = chebyshev(pcx, pcz, win.centerChunkX, win.centerChunkZ);
        if (dist > win.radiusChunks || dist >= IDLE_CENTER_FOLLOW_STEP_CHUNKS) {
            int oldCenterX = win.centerChunkX;
            int oldCenterZ = win.centerChunkZ;
            int oldRadius = win.radiusChunks;
            int targetRadius = dist > oldRadius ? baseRadius : oldRadius;

            win.centerChunkX = pcx;
            win.centerChunkZ = pcz;
            if (trySchedulePlan(level, win, targetRadius)) {
                win.radiusChunks = targetRadius;
                return;
            }

            win.centerChunkX = oldCenterX;
            win.centerChunkZ = oldCenterZ;
        }

        if (!isWindowFinished(level, win)) {
            return;
        }

        int oldRadius = win.radiusChunks;
        int nextRadius = Math.min(MAX_IDLE_RADIUS_CHUNKS, oldRadius + expandStep);
        if (nextRadius <= oldRadius) return;
        if (trySchedulePlan(level, win, nextRadius)) {
            win.radiusChunks = nextRadius;
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (!cfg.performance().idleGenerationEnabled()) {
            clearLevel(level);
            return;
        }

        cleanupWindows(level);

        ALL_RUNNING.removeIf(f -> f == null || f.isDone() || f.isCancelled());

        AtomicInteger running = RUNNING;
        List<ServerPlayer> players = collectPlayers(level);
        int duty = cfg.performance().idleThreadDutyCycle();

        int limit = cfg.performance().idleMaxConcurrentGenerations();

        // 连接列表每 tick 只取一次
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        reservePlannedEdges(level, conns);

        List<StructureConnection> candidates = collectOwnedPlannedCandidates(conns, players);
        for (StructureConnection conn : candidates) {
            if (running.get() >= limit) break;

            PlannedPathKey key = PlannedPathKey.of(conn);
            ConcurrentHashMap<PlannedPathKey, Boolean> inFlight = IN_FLIGHT;
            if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) continue;
            if (!updateStatus(level, conn, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED)) {
                inFlight.remove(key);
                continue;
            }

            running.incrementAndGet();
            long epoch = ThreadPoolManager.currentEpoch();
            final StructureConnection task = conn;
            try {
                Future<?> future = ThreadPoolManager.submit(ThreadPoolManager.TaskRole.IDLE,
                        () -> runIdleTask(level, task, key, duty, epoch));
                ALL_RUNNING.add(future);
                if (!ThreadPoolManager.isEpoch(epoch)) future.cancel(true);
            } catch (java.util.concurrent.RejectedExecutionException submissionFailure) {
                if (ThreadPoolManager.isEpoch(epoch)) {
                    running.decrementAndGet();
                    try {
                        updateStatus(level, task, ConnectionStatus.PLANNED, ConnectionStatus.GENERATING);
                    } finally {
                        IN_FLIGHT.remove(key);
                    }
                }
                LOGGER.warn("IdleRoad: generation submission failed {} -> {}", task.from(), task.to(), submissionFailure);
                break;
            }
        }
    }

    public static boolean shouldReserveForIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null || !Level.OVERWORLD.equals(level.dimension())) return false;

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        // 主线程每 tick 逐条调用，先判最廉价条件以短路配置读取与窗口判定
        if (owned.isEmpty()) return false;
        if (!ConfigService.get().performance().idleGenerationEnabled()) return false;

        PlannedPathKey key = PlannedPathKey.of(conn);
        if (!owned.containsKey(key)) return false;
        if (isInsideAnyWindow(conn)) return true;

        owned.remove(key);
        return false;
    }

    /** 供 {@link RoadGenerationService#tick} 遍历全连接时复用 key 与配置读取的版本。 */
    public static boolean shouldReserveForIdleCached(StructureConnection conn,
                                                     PlannedPathKey key,
                                                     boolean idleEnabled) {
        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        if (!idleEnabled || owned.isEmpty()) return false;
        if (!owned.containsKey(key)) return false;
        if (isInsideAnyWindow(conn)) return true;
        owned.remove(key);
        return false;
    }

    public static boolean isManagedByIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        ConcurrentHashMap<PlannedPathKey, Boolean> inFlight = IN_FLIGHT;
        if (owned.isEmpty() && inFlight.isEmpty()) return false;
        PlannedPathKey key = PlannedPathKey.of(conn);
        if (inFlight.containsKey(key)) return true;
        if (!ConfigService.get().performance().idleGenerationEnabled()) return false;
        return owned.containsKey(key);
    }

    public static boolean isManagedByIdleCached(StructureConnection conn,
                                                PlannedPathKey key,
                                                boolean idleEnabled) {
        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        ConcurrentHashMap<PlannedPathKey, Boolean> inFlight = IN_FLIGHT;
        if (inFlight.containsKey(key)) return true;
        if (!idleEnabled) return false;
        if (owned.isEmpty() && inFlight.isEmpty()) return false;
        return owned.containsKey(key);
    }

    private static void runIdleTask(ServerLevel level,
                                    StructureConnection task,
                                    PlannedPathKey key,
                                    int duty,
                                    long epoch) {
        AtomicInteger running = RUNNING;
        ThreadPoolManager.resetThrottle();
        try {
            if (Thread.currentThread().isInterrupted()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;

            boolean ok = RoadGenerationService.generateTask(level, task);
            ConnectionStatus st = ok ? ConnectionStatus.COMPLETED
                    : Thread.currentThread().isInterrupted() ? ConnectionStatus.PLANNED : ConnectionStatus.FAILED;
            executeOnMain(level, epoch, () -> {
                try {
                    boolean updated = updateStatus(level, task, st, ConnectionStatus.GENERATING);
                    if (updated && st == ConnectionStatus.COMPLETED) {
                        RoadSnapService.snapAroundConnectionAsync(level, task.from(), task.to());
                    }
                } finally {
                    removeOwnership(level, key);
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("IdleRoad: generate failed {} -> {}", task.from(), task.to(), t);
            executeOnMain(level, epoch, () -> {
                try {
                    updateStatus(level, task, ConnectionStatus.FAILED, ConnectionStatus.GENERATING);
                } finally {
                    removeOwnership(level, key);
                }
            });
        } finally {
            try {
                ThreadPoolManager.throttle(duty);
            } catch (Throwable ignored) {}
            ThreadPoolManager.clearThrottle();
            if (ThreadPoolManager.isEpoch(epoch)) running.decrementAndGet();
        }
    }

    private static void removeOwnership(ServerLevel level, PlannedPathKey key) {
        IDLE_OWNED.remove(key);
        IN_FLIGHT.remove(key);
    }

    private static void reservePlannedEdges(ServerLevel level, List<StructureConnection> conns) {
        if (conns == null || conns.isEmpty()) return;
        if (!hasWindows()) return;

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        for (StructureConnection c : conns) {
            if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
            if (!isInsideAnyWindow(c)) continue;
            owned.putIfAbsent(PlannedPathKey.of(c), Boolean.TRUE);
        }
    }

    /**
     * 一次性收集本 tick 可用的候选连接，按到玩家的距离升序返回。
     * 调用方顺序消费即可得到与逐次「取最近一条」完全一致的选择顺序。
     */
    private static List<StructureConnection> collectOwnedPlannedCandidates(
            List<StructureConnection> conns, List<ServerPlayer> players) {
        if (conns == null || conns.isEmpty()) return List.of();

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        if (owned.isEmpty()) return List.of();
        if (!hasWindows()) return List.of();
        ConcurrentHashMap<UUID, IdleWindow> windows = WINDOWS;
        ConcurrentHashMap<PlannedPathKey, Boolean> inFlight = IN_FLIGHT;

        // 窗口摊平成数组，避免逐条连接遍历 UUID 哈希表
        int windowCount = 0;
        int[] centersX = new int[windows.size()];
        int[] centersZ = new int[windows.size()];
        int[] radii = new int[windows.size()];
        for (IdleWindow w : windows.values()) {
            if (w == null) continue;
            centersX[windowCount] = w.centerChunkX;
            centersZ[windowCount] = w.centerChunkZ;
            radii[windowCount] = w.radiusChunks;
            windowCount++;
        }
        if (windowCount == 0) return List.of();

        // 配对记录以避免 Map<Integer, ...> 装箱
        List<Candidate> candidates = new ArrayList<>();
        for (StructureConnection c : conns) {
            if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
            PlannedPathKey key = PlannedPathKey.of(c);
            if (!owned.containsKey(key)) continue;

            int mcx = ((c.from().getX() + c.to().getX()) >> 1) >> 4;
            int mcz = ((c.from().getZ() + c.to().getZ()) >> 1) >> 4;

            boolean inside = false;
            for (int i = 0; i < windowCount; i++) {
                if (Math.abs(mcx - centersX[i]) <= radii[i] && Math.abs(mcz - centersZ[i]) <= radii[i]) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                owned.remove(key);
                continue;
            }
            if (inFlight.containsKey(key)) continue;
            candidates.add(new Candidate(playerDistance2(c, players), c));
        }

        if (candidates.isEmpty()) return List.of();
        candidates.sort(Candidate.BY_DISTANCE);
        List<StructureConnection> ordered = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            ordered.add(candidate.connection());
        }
        return ordered;
    }

    /** 候选连接及其到玩家的距离。 */
    private record Candidate(long distance, StructureConnection connection) {
        static final Comparator<Candidate> BY_DISTANCE = Comparator.comparingLong(Candidate::distance);
    }

    private static boolean updateStatus(ServerLevel level, StructureConnection conn, ConnectionStatus to, ConnectionStatus... allowedFrom) {
        StructureConnection updated = ConnectionListUpdater.transition(level, conn, to, allowedFrom);
        if (updated == null) return false;

        MapPatchService.publishConnection(level, updated);
        if (to == ConnectionStatus.COMPLETED) {
            MapPatchService.publishRoadForConnectionAsync(level, updated);
        }
        return true;
    }

    private static void cleanupWindows(ServerLevel level) {
        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS;
        if (perLevel.isEmpty()) return;

        Set<UUID> alive = new HashSet<>();
        for (ServerPlayer p : collectPlayers(level)) {
            alive.add(p.getUUID());
        }
        perLevel.keySet().removeIf(id -> !alive.contains(id));

        if (perLevel.isEmpty()) {
            IDLE_OWNED.clear();
            // 已提交的任务仍可能运行；由完成回调释放 in-flight 和计数。
        }
    }

    private static void clearLevel(ServerLevel level) {
        WINDOWS.clear();
        IDLE_OWNED.clear();
    }

    /**
     * 闲时窗口只为主世界建立（{@link #tick} 入口已限定 OVERWORLD），
     * 故此处无需再按维度过滤。
     */
    private static boolean hasWindows() {
        return !WINDOWS.isEmpty();
    }

    private static boolean isInsideAnyWindow(StructureConnection conn) {
        ConcurrentHashMap<UUID, IdleWindow> windows = WINDOWS;
        if (windows.isEmpty() || conn == null) return false;

        int mx = (conn.from().getX() + conn.to().getX()) >> 1;
        int mz = (conn.from().getZ() + conn.to().getZ()) >> 1;
        int mcx = mx >> 4;
        int mcz = mz >> 4;

        for (IdleWindow w : windows.values()) {
            if (w == null) continue;
            if (Math.abs(mcx - w.centerChunkX) <= w.radiusChunks
                    && Math.abs(mcz - w.centerChunkZ) <= w.radiusChunks) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideWindow(IdleWindow window, StructureConnection conn) {
        if (window == null || conn == null) return false;
        int mx = (conn.from().getX() + conn.to().getX()) >> 1;
        int mz = (conn.from().getZ() + conn.to().getZ()) >> 1;
        int mcx = mx >> 4;
        int mcz = mz >> 4;
        return Math.abs(mcx - window.centerChunkX) <= window.radiusChunks
                && Math.abs(mcz - window.centerChunkZ) <= window.radiusChunks;
    }

    private static boolean isWindowFinished(ServerLevel level, IdleWindow window) {
        if (level == null || window == null) return false;
        if (window.planning.get()) return false;

        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return true;

        for (StructureConnection c : conns) {
            if (c == null) continue;
            if (!isInsideWindow(window, c)) continue;
            if (c.status() == ConnectionStatus.PLANNED || c.status() == ConnectionStatus.GENERATING) {
                return false;
            }
        }
        return true;
    }

    private static boolean trySchedulePlan(ServerLevel level, IdleWindow window, int radiusChunks) {
        if (level == null || window == null) return false;
        if (!window.planning.compareAndSet(false, true)) return false;

        int cx = window.centerChunkX;
        int cz = window.centerChunkZ;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;

        CompletableFuture<Void> future = RoadPlanningService.planRectAsync(level, minX, minZ, maxX, maxZ);
        future.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("IdleRoad: plan rect failed center=({},{}), radius={}", cx, cz, radiusChunks, throwable);
            }
            window.planning.set(false);
        });
        return true;
    }

    private static int resolveBaseRadiusChunks(ModConfig cfg) {
        if (cfg == null) return 1;
        if (cfg.planning().dynamicPlanEnabled()) {
            return Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
        }
        return Math.max(1, cfg.planning().initialPlanRadiusChunks());
    }

    private static int resolveExpandStepChunks(ModConfig cfg) {
        return IDLE_RADIUS_EXPAND_STEP_CHUNKS;
    }

    private static int chebyshev(int x0, int z0, int x1, int z1) {
        return Math.max(Math.abs(x0 - x1), Math.abs(z0 - z1));
    }

    private static List<ServerPlayer> collectPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        var server = level.getServer();
        if (server == null) return out;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() == level) out.add(p);
        }
        return out;
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

    private static void executeOnMain(ServerLevel level, long epoch, Runnable action) {
        var server = level.getServer();
        if (server == null) {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
            return;
        }
        server.execute(() -> {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
        });
    }
}
