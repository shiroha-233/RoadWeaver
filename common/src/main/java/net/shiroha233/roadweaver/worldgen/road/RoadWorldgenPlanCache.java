/* 文件职责：按世界会话缓存不可变道路区块计划，隔离持久化与世界生成热路径。 */
package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 世界生成计划的会话入口。磁盘存储只在首次编译计划时被访问，计划本身不可变。
 */
public final class RoadWorldgenPlanCache {
    private static final int QUERY_MARGIN_BLOCKS = 32;
    private static final int MAX_PLANS_PER_LEVEL = 4096;
    private static final int MAX_CHUNK_REVISIONS_PER_LEVEL = 8192;
    /** 编译重试上限；耗尽后接受当前最佳结果，避免工作线程空转。 */
    private static final int MAX_COMPILE_ATTEMPTS = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final ConcurrentHashMap<ServerLevel, Session> SESSIONS = new ConcurrentHashMap<>();

    private RoadWorldgenPlanCache() {
    }

    public static RoadChunkPlan get(ServerLevel level, ChunkPos chunkPos, ModConfig config) {
        if (level == null || chunkPos == null || !Level.OVERWORLD.equals(level.dimension())
                || config == null || !config.roadAppearance().roadsEnabled()) {
            return RoadChunkPlan.empty(chunkPos == null ? new ChunkPos(0, 0) : chunkPos,
                    config == null ? 4 : config.roadAppearance().roadClearHeight(), 0L);
        }
        PlanConfig planConfig = PlanConfig.from(config);
        Session session = session(level);
        return session.get(level, chunkPos, planConfig);
    }

    public static void invalidate(ServerLevel level) {
        if (level == null) return;
        Session session = SESSIONS.get(level);
        if (session != null) session.invalidate();
    }

    public static void invalidate(ServerLevel level, Collection<Long> chunkKeys) {
        if (level == null || chunkKeys == null || chunkKeys.isEmpty()) return;
        Session session = SESSIONS.get(level);
        if (session != null) session.invalidate(chunkKeys);
    }

    public static void clear(ServerLevel level) {
        if (level == null) return;
        SESSIONS.remove(level);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    private static Session session(ServerLevel level) {
        return SESSIONS.computeIfAbsent(level, ignored -> new Session());
    }

    private static RoadChunkPlan compile(ServerLevel level,
                                         ChunkPos chunkPos,
                                         PlanConfig config,
                                         long revision) {
        int minX = chunkPos.getMinBlockX() - QUERY_MARGIN_BLOCKS;
        int minZ = chunkPos.getMinBlockZ() - QUERY_MARGIN_BLOCKS;
        int maxX = chunkPos.getMaxBlockX() + QUERY_MARGIN_BLOCKS;
        int maxZ = chunkPos.getMaxBlockZ() + QUERY_MARGIN_BLOCKS;
        var roads = RoadShardStorage.queryRect(level, minX, minZ, maxX, maxZ);
        return RoadChunkPlanCompiler.compile(chunkPos, roads,
                !config.bridgeEnabled(), config.clearHeight(), revision);
    }

    private static final class Session {
        private final ConcurrentHashMap<Long, CachedPlan> plans = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, Long> chunkRevisions = new ConcurrentHashMap<>();
        private final AtomicLong revisionSequence = new AtomicLong();
        private volatile long globalRevision;

        RoadChunkPlan get(ServerLevel level, ChunkPos chunkPos, PlanConfig requested) {
            long key = chunkPos.toLong();
            // 有界重试：持续失效下 while(true) 会让工作线程空转吃满一个核。
            // 该计划只是密度戳缓存，略微陈旧远优于持续占用 CPU。
            for (int attempt = 1; ; attempt++) {
                CachedPlan cached = plans.get(key);
                long expectedRevision = revision(key);
                if (cached != null && cached.matches(requested, expectedRevision)) {
                    return cached.plan();
                }

                CachedPlan published;
                try {
                    published = plans.compute(key, (ignored, current) -> {
                        long compileRevision = revision(key);
                        if (current != null && current.matches(requested, compileRevision)) {
                            return current;
                        }
                        RoadChunkPlan compiled = compile(level, chunkPos, requested, compileRevision);
                        return revision(key) == compileRevision
                                ? new CachedPlan(requested, compiled)
                                : null;
                    });
                } catch (RuntimeException failure) {
                    // 本次生成保守退化为空计划，但不能将临时 IO 错误永久缓存为无路。
                    LOGGER.warn("编译道路区块计划失败: {}", chunkPos, failure);
                    return RoadChunkPlan.empty(chunkPos, requested.clearHeight(), revision(key));
                }
                long currentRevision = revision(key);
                if (published != null && published.matches(requested, currentRevision)) {
                    trimIfNeeded();
                    return published.plan();
                }
                if (attempt >= MAX_COMPILE_ATTEMPTS) {
                    trimIfNeeded();
                    CachedPlan best = plans.get(key);
                    // 只放宽 revision，不放宽 config：配置不符的计划会把道路生成在错误的 Y 层
                    if (best != null && best.config().equals(requested)) return best.plan();
                    try {
                        return compile(level, chunkPos, requested, currentRevision);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("编译道路区块计划失败: {}", chunkPos, failure);
                        return RoadChunkPlan.empty(chunkPos, requested.clearHeight(), currentRevision);
                    }
                }
                Thread.onSpinWait();
            }
        }

        synchronized void invalidate() {
            globalRevision = revisionSequence.incrementAndGet();
            chunkRevisions.clear();
            plans.clear();
        }

        synchronized void invalidate(Collection<Long> chunkKeys) {
            long revision = revisionSequence.incrementAndGet();
            for (long packed : chunkKeys) {
                int chunkX = ChunkPos.getX(packed);
                int chunkZ = ChunkPos.getZ(packed);
                for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        long affected = ChunkPos.asLong(chunkX + deltaX, chunkZ + deltaZ);
                        chunkRevisions.put(affected, revision);
                        plans.remove(affected);
                    }
                }
                // 修改过的区块不一定会再次查询。限制 tombstone 数量，超过预算时
                // 用一次全局失效替代逐区块 revision，避免长时间运行后无限增长。
                if (chunkRevisions.size() > MAX_CHUNK_REVISIONS_PER_LEVEL) {
                    globalRevision = revision;
                    chunkRevisions.clear();
                    plans.clear();
                    return;
                }
            }
        }

        private long revision(long chunkKey) {
            return Math.max(globalRevision, chunkRevisions.getOrDefault(chunkKey, 0L));
        }

        private void trimIfNeeded() {
            int excess = plans.size() - MAX_PLANS_PER_LEVEL;
            if (excess <= 0) return;
            for (Map.Entry<Long, CachedPlan> entry : plans.entrySet()) {
                plans.remove(entry.getKey(), entry.getValue());
                if (--excess <= 0) return;
            }
        }
    }

    private record CachedPlan(PlanConfig config, RoadChunkPlan plan) {
        boolean matches(PlanConfig requested, long revision) {
            return config.equals(requested) && plan.revision() == revision;
        }
    }

    private record PlanConfig(int clearHeight, boolean bridgeEnabled) {
        static PlanConfig from(ModConfig config) {
            return new PlanConfig(config.roadAppearance().roadClearHeight(), config.bridgeEnabled());
        }
    }
}
