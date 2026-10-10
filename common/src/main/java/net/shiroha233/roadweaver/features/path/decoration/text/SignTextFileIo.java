/* 文件职责：把路牌待写文本的文件 IO 从主线程迁出，并提供有界的分片读取缓存。 */
package net.shiroha233.roadweaver.features.path.decoration.text;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路牌待写文本的异步 IO 管道。
 *
 * <p>缓存中的分片列表是该区块待写数据的权威副本：消费成功后由后台线程同步移除
 * 并落盘，失败则保持原样以供下 tick 重试。
 *
 * <p>主线程在分片未就绪时不等待而是原地返回，并完全依赖后台回调
 * {@link #requeue} 唤醒。因此凡是「任务没跑成」的情况都必须兜底重排队，
 * 否则该区块的待写数据会永久失联。
 */
final class SignTextFileIo {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final int CACHE_MAX_CHUNKS = 32;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "roadweaver-signtext-io");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Long, List<SignTextFileStorage.PendingSignText>> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<SignTextFileStorage.PendingSignText>> eldest) {
                    return size() > CACHE_MAX_CHUNKS;
                }
            };
    /** 已提交加载但未完成的分片，防止主线程重复提交。 */
    private static final Set<Long> LOADING = new HashSet<>();

    /**
     * 生命周期代号。{@link #clearMemory} 会使其自增，使已入队但尚未执行的任务作废：
     * 维度卸载或换存档后，旧 {@link ServerLevel} 已不可再用，其结果也不得写回缓存。
     */
    private static final AtomicLong EPOCH = new AtomicLong();

    private SignTextFileIo() {}

    /** 缓存未命中时返回 {@code null} 并异步加载，调用方应保持区块为待处理状态。 */
    static List<SignTextFileStorage.PendingSignText> poll(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (limit <= 0) return List.of();
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (LOCK) {
            List<SignTextFileStorage.PendingSignText> cached = CACHE.get(chunkKey);
            if (cached != null) {
                return cached.size() <= limit
                        ? new ArrayList<>(cached)
                        : new ArrayList<>(cached.subList(0, limit));
            }
            if (LOADING.add(chunkKey)) {
                submitLoad(level, chunkKey, chunkX, chunkZ);
            }
        }
        return null;
    }

    /**
     * 由后台线程从缓存与磁盘中一并移除已写入的条目。
     *
     * <p>失败不重排队：磁盘上的这些行本就是已写入内容的冗余副本，重新处理只会反复
     * 写同一条，缓存的 LRU 淘汰足以回收它们。
     */
    static void reportDone(ServerLevel level, int chunkX, int chunkZ, List<Long> doneIds) {
        if (doneIds == null || doneIds.isEmpty()) return;
        List<Long> ids = List.copyOf(doneIds);
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        long epoch = EPOCH.get();
        submit(() -> {
            try {
                if (epoch != EPOCH.get()) return;
                SignTextFileStorage.deleteByIds(level, ids);
                synchronized (LOCK) {
                    List<SignTextFileStorage.PendingSignText> cached = CACHE.get(chunkKey);
                    if (cached != null) {
                        cached.removeIf(row -> row != null && ids.contains(row.id()));
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("回收已写入路牌文本失败 chunk=({}, {})", chunkX, chunkZ, t);
            }
        });
    }

    /**
     * 把内存重试耗尽的行落盘。
     *
     * <p>失败不重排队：该行已从内存队列中丢弃，没有可重试的副本，只能记日志。
     */
    static void upsert(ServerLevel level, SignTextFileStorage.PendingSignWrite write) {
        long chunkKey = ChunkPos.asLong(write.pos().getX() >> 4, write.pos().getZ() >> 4);
        long epoch = EPOCH.get();
        submit(() -> {
            try {
                if (epoch != EPOCH.get()) return;
                SignTextFileStorage.upsertBatch(level, List.of(write));
                // 磁盘已最新，丢弃可能过期的缓存副本
                synchronized (LOCK) {
                    CACHE.remove(chunkKey);
                }
            } catch (Throwable t) {
                LOGGER.warn("持久化路牌文本失败 pos={}", write.pos(), t);
            }
        });
    }

    private static void submitLoad(ServerLevel level, long chunkKey, int chunkX, int chunkZ) {
        long epoch = EPOCH.get();
        boolean submitted = submit(() -> {
            try {
                if (epoch != EPOCH.get()) return;
                List<SignTextFileStorage.PendingSignText> rows =
                        SignTextFileStorage.queryByChunk(level, chunkX, chunkZ, Integer.MAX_VALUE);
                synchronized (LOCK) {
                    CACHE.put(chunkKey, new ArrayList<>(rows));
                }
                // 加载完成后重新标记，主线程无需轮询等待
                requeueIfLive(epoch, level, chunkKey);
            } catch (Throwable t) {
                LOGGER.warn("加载路牌文本分片失败 chunk=({}, {})", chunkX, chunkZ, t);
                // 主线程已因 pending == null 而提前返回，不唤醒就会永久丢掉这一分片
                requeueIfLive(epoch, level, chunkKey);
            } finally {
                synchronized (LOCK) {
                    LOADING.remove(chunkKey);
                }
            }
        });
        if (!submitted) {
            synchronized (LOCK) {
                LOADING.remove(chunkKey);
            }
            requeueIfLive(epoch, level, chunkKey);
        }
    }

    /** @return 任务是否成功入队 */
    private static boolean submit(Runnable task) {
        try {
            WORKER.execute(task);
            return true;
        } catch (Throwable rejected) {
            LOGGER.warn("路牌文本 IO 队列已关闭，丢弃任务", rejected);
            return false;
        }
    }

    private static void requeueIfLive(long epoch, ServerLevel level, long chunkKey) {
        if (epoch != EPOCH.get()) return;
        SignTextService.onChunkReady(level, new ChunkPos(chunkKey));
    }

    static void clearMemory() {
        // 先作废在途任务再清缓存：顺序反了会让已入队的旧任务把结果写回新缓存
        EPOCH.incrementAndGet();
        synchronized (LOCK) {
            CACHE.clear();
            LOADING.clear();
        }
    }
}