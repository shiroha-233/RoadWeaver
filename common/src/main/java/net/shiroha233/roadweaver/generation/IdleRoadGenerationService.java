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
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.path.PlannedPathKey;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        reservePlannedEdges(level);

        AtomicInteger running = RUNNING;
        List<ServerPlayer> players = collectPlayers(level);
        int duty = cfg.performance().idleThreadDutyCycle();

        int limit = cfg.performance().idleMaxConcurrentGenerations();

        while (running.get() < limit) {
            StructureConnection conn = pollNearestOwnedPlanned(level, players);
            if (conn == null) break;

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
            Future<?> future = ThreadPoolManager.submit(ThreadPoolManager.TaskRole.IDLE, () -> runIdleTask(level, task, key, duty, epoch));
            ALL_RUNNING.add(future);
        }
    }

    public static boolean shouldReserveForIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        if (!ConfigService.get().performance().idleGenerationEnabled()) return false;

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        if (owned.isEmpty()) return false;

        PlannedPathKey key = PlannedPathKey.of(conn);
        if (!owned.containsKey(key)) return false;
        if (isInsideAnyWindow(level, conn)) return true;

        owned.remove(key);
        return false;
    }

    public static boolean isManagedByIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null || !Level.OVERWORLD.equals(level.dimension())) return false;
        if (!ConfigService.get().performance().idleGenerationEnabled()) return false;
        PlannedPathKey key = PlannedPathKey.of(conn);
        if (IN_FLIGHT.containsKey(key)) return true;
        return IDLE_OWNED.containsKey(key);
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
            ConnectionStatus st = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;
            executeOnMain(level, epoch, () -> {
                updateStatus(level, task, st, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED);
                removeOwnership(level, key);
                if (st == ConnectionStatus.COMPLETED) {
                    RoadSnapService.snapAroundConnectionAsync(level, task.from(), task.to());
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("IdleRoad: generate failed {} -> {}", task.from(), task.to(), t);
            executeOnMain(level, epoch, () -> {
                updateStatus(level, task, ConnectionStatus.FAILED, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED);
                removeOwnership(level, key);
            });
        } finally {
            try {
                ThreadPoolManager.throttle(duty);
            } catch (Throwable ignored) {}
            ThreadPoolManager.clearThrottle();
            running.decrementAndGet();
        }
    }

    private static void removeOwnership(ServerLevel level, PlannedPathKey key) {
        IDLE_OWNED.remove(key);
        IN_FLIGHT.remove(key);
    }

    private static void reservePlannedEdges(ServerLevel level) {
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return;
        if (!hasWindows(level)) return;

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        for (StructureConnection c : conns) {
            if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
            if (!isInsideAnyWindow(level, c)) continue;
            owned.putIfAbsent(PlannedPathKey.of(c), Boolean.TRUE);
        }
    }

    private static StructureConnection pollNearestOwnedPlanned(ServerLevel level, List<ServerPlayer> players) {
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return null;

        ConcurrentHashMap<PlannedPathKey, Boolean> owned = IDLE_OWNED;
        if (owned.isEmpty()) return null;
        ConcurrentHashMap<PlannedPathKey, Boolean> inFlight = IN_FLIGHT;

        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS;
        if (perLevel.isEmpty()) return null;

        StructureConnection best = null;
        long bestDist = Long.MAX_VALUE;

        for (Map.Entry<UUID, IdleWindow> windowEntry : perLevel.entrySet()) {
            IdleWindow w = windowEntry.getValue();
            if (w == null) continue;
            int wcCx = w.centerChunkX;
            int wcCz = w.centerChunkZ;
            int wRadius = w.radiusChunks;

            for (StructureConnection c : conns) {
                if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
                PlannedPathKey key = PlannedPathKey.of(c);
                if (!owned.containsKey(key)) continue;
                int mx = (c.from().getX() + c.to().getX()) >> 1;
                int mz = (c.from().getZ() + c.to().getZ()) >> 1;
                int mcx = mx >> 4;
                int mcz = mz >> 4;
                if (Math.abs(mcx - wcCx) > wRadius || Math.abs(mcz - wcCz) > wRadius) {
                    owned.remove(key);
                    continue;
                }
                if (inFlight.containsKey(key)) continue;
                long d = playerDistance2(c, players);
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }
        }
        return best;
    }

    private static boolean updateStatus(ServerLevel level, StructureConnection conn, ConnectionStatus to, ConnectionStatus... allowedFrom) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getStructureConnections(level);
        if (origin == null || origin.isEmpty()) return false;

        Set<ConnectionStatus> allowed = new HashSet<>();
        if (allowedFrom != null) {
            for (ConnectionStatus st : allowedFrom) {
                if (st != null) allowed.add(st);
            }
        }

        List<StructureConnection> all = new ArrayList<>(origin);
        for (int i = 0; i < all.size(); i++) {
            StructureConnection c = all.get(i);
            if (!PlanningUtils.sameEdge(c, conn)) continue;
            if (!allowed.isEmpty() && !allowed.contains(c.status())) return false;
            StructureConnection updated = new StructureConnection(c.from(), c.to(), to);
            all.set(i, updated);
            provider.setStructureConnections(level, all);
            MapPatchService.publishConnection(level, updated);
            if (to == ConnectionStatus.COMPLETED) {
                MapPatchService.publishRoadForConnectionAsync(level, updated);
            }
            return true;
        }
        return false;
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
            IN_FLIGHT.clear();
            RUNNING.set(0);
        }
    }

    private static void clearLevel(ServerLevel level) {
        WINDOWS.clear();
        IDLE_OWNED.clear();
        IN_FLIGHT.clear();
        RUNNING.set(0);
    }

    private static boolean hasWindows(ServerLevel level) {
        return !WINDOWS.isEmpty();
    }

    private static boolean isInsideAnyWindow(ServerLevel level, StructureConnection conn) {
        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS;
        if (perLevel.isEmpty() || conn == null) return false;

        int mx = (conn.from().getX() + conn.to().getX()) >> 1;
        int mz = (conn.from().getZ() + conn.to().getZ()) >> 1;
        int mcx = mx >> 4;
        int mcz = mz >> 4;

        for (IdleWindow w : perLevel.values()) {
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
