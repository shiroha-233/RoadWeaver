package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路牌文本延迟写入存储门面
 */
public final class SignTextShardStorage {
    private SignTextShardStorage() {}

    public static final int TYPE_DISTANCE = SignTextFileStorage.TYPE_DISTANCE;
    public static final int TYPE_SEA_QUESTION = SignTextFileStorage.TYPE_SEA_QUESTION;
    private static final int MAX_PENDING_PER_CHUNK = 256;
    private static final int MAX_PENDING = 10_000;
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> MEMORY_PENDING = new ConcurrentHashMap<>();

    public record PendingSignWrite(BlockPos pos, int signType, String payload, int retryCount, long firstQueuedAtMs) {
        public PendingSignWrite withRetry() {
            return new PendingSignWrite(pos, signType, payload, retryCount + 1, firstQueuedAtMs);
        }

        private SignTextFileStorage.PendingSignWrite toPersistentWrite() {
            return new SignTextFileStorage.PendingSignWrite(pos, signType, payload);
        }
    }

    public static void upsert(ServerLevel level, BlockPos pos, int signType, String payload) {
        if (!isOverworld(level) || pos == null) return;
        putMemory(level, new PendingSignWrite(pos, signType, payload, 0, System.currentTimeMillis()));
    }

    public static void upsertBatch(ServerLevel level, Collection<PendingSignWrite> writes) {
        if (!isOverworld(level) || writes == null || writes.isEmpty()) return;
        ArrayList<SignTextFileStorage.PendingSignWrite> batch = new ArrayList<>(writes.size());
        for (PendingSignWrite write : writes) {
            if (write == null || write.pos() == null) continue;
            batch.add(write.toPersistentWrite());
        }
        SignTextFileStorage.upsertBatch(level, batch);
    }

    public static List<SignTextFileStorage.PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        return isOverworld(level) ? SignTextFileStorage.queryByChunk(level, chunkX, chunkZ, limit) : List.of();
    }

    public static void deleteByIds(ServerLevel level, List<Long> ids) {
        if (!isOverworld(level)) return;
        SignTextFileStorage.deleteByIds(level, ids);
    }

    public static List<PendingSignWrite> takeMemoryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (!isOverworld(level) || limit <= 0) return List.of();
        long chunkKey = ChunkPos.pack(chunkX, chunkZ);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = MEMORY_PENDING.get(chunkKey);
        if (chunkBucket == null || chunkBucket.isEmpty()) return List.of();

        ArrayList<PendingSignWrite> out = new ArrayList<>(Math.min(limit, chunkBucket.size()));
        for (var entry : chunkBucket.entrySet()) {
            if (out.size() >= limit) break;
            if (chunkBucket.remove(entry.getKey(), entry.getValue())) {
                out.add(entry.getValue());
            }
        }
        if (chunkBucket.isEmpty()) {
            MEMORY_PENDING.remove(chunkKey, chunkBucket);
        }
        return out;
    }

    public static void requeue(ServerLevel level, PendingSignWrite write) {
        if (!isOverworld(level) || write == null || write.pos() == null) return;
        long chunkKey = ChunkPos.pack(write.pos().getX() >> 4, write.pos().getZ() >> 4);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket =
                MEMORY_PENDING.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
        chunkBucket.putIfAbsent(write.pos().asLong(), write.withRetry());
    }

    public static boolean hasMemoryPending(ServerLevel level, int chunkX, int chunkZ) {
        if (!isOverworld(level)) return false;
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = MEMORY_PENDING.get(ChunkPos.pack(chunkX, chunkZ));
        return chunkBucket != null && !chunkBucket.isEmpty();
    }

    public static void flushPending(ServerLevel level) {
        if (!isOverworld(level)) return;
        if (MEMORY_PENDING.isEmpty()) return;
        ArrayList<PendingSignWrite> batch = new ArrayList<>();
        ArrayList<MemoryEntry> entries = new ArrayList<>();
        for (var chunkEntry : MEMORY_PENDING.entrySet()) {
            ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = chunkEntry.getValue();
            if (chunkBucket == null || chunkBucket.isEmpty()) continue;
            for (var writeEntry : chunkBucket.entrySet()) {
                PendingSignWrite write = writeEntry.getValue();
                if (write == null) continue;
                batch.add(write);
                entries.add(new MemoryEntry(chunkEntry.getKey(), writeEntry.getKey(), write));
            }
        }
        upsertBatch(level, batch);
        for (MemoryEntry entry : entries) {
            ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = MEMORY_PENDING.get(entry.chunkKey());
            if (chunkBucket == null) continue;
            chunkBucket.remove(entry.posKey(), entry.write());
            if (chunkBucket.isEmpty()) MEMORY_PENDING.remove(entry.chunkKey(), chunkBucket);
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (!isOverworld(level)) return;
        MEMORY_PENDING.clear();
    }

    public static void clearAllMemory() {
        MEMORY_PENDING.clear();
    }

    private static void putMemory(ServerLevel level, PendingSignWrite write) {
        long chunkKey = ChunkPos.pack(write.pos().getX() >> 4, write.pos().getZ() >> 4);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket =
                MEMORY_PENDING.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
        chunkBucket.put(write.pos().asLong(), write);
        if (chunkBucket.size() > MAX_PENDING_PER_CHUNK || pendingCount() > MAX_PENDING) {
            flushPending(level);
        }
    }

    private static int pendingCount() {
        int total = 0;
        for (ConcurrentHashMap<Long, PendingSignWrite> bucket : MEMORY_PENDING.values()) {
            if (bucket != null) total += bucket.size();
            if (total > MAX_PENDING) return total;
        }
        return total;
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private record MemoryEntry(long chunkKey, long posKey, PendingSignWrite write) {}
}
