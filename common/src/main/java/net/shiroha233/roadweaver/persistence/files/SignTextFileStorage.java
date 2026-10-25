package net.shiroha233.roadweaver.persistence.files;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路牌待写文本文件存储：按 chunk 分片。
 */
public final class SignTextFileStorage {
    private SignTextFileStorage() {}

    private static final String CATEGORY = "sign_texts";
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String FILE_SUFFIX = ".json";
    public static final int TYPE_DISTANCE = 0;
    public static final int TYPE_SEA_QUESTION = 1;

    public static record PendingSignText(long id, BlockPos pos, int signType, String payload, long updatedAt) {}
    public static record PendingSignWrite(BlockPos pos, int signType, String payload) {}

    public static synchronized void upsertBatch(ServerLevel level, Collection<PendingSignWrite> writes) {
        if (!isOverworld(level) || writes == null || writes.isEmpty()) return;
        Map<Long, List<PendingSignText>> byChunk = new HashMap<>();
        for (PendingSignWrite write : writes) {
            if (write == null || write.pos() == null) continue;
            long id = write.pos().asLong();
            long chunkKey = ChunkPos.asLong(write.pos().getX() >> 4, write.pos().getZ() >> 4);
            List<PendingSignText> chunk = byChunk.computeIfAbsent(chunkKey, k -> loadChunk(level, k));
            chunk.removeIf(existing -> existing.id() == id);
            chunk.add(new PendingSignText(id, write.pos(), write.signType(), write.payload() == null ? "" : write.payload(), System.currentTimeMillis() / 1000L));
        }
        for (var entry : byChunk.entrySet()) {
            saveChunk(level, entry.getKey(), entry.getValue());
        }
    }

    public static synchronized List<PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (!isOverworld(level) || limit <= 0) return List.of();
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        List<PendingSignText> chunk = loadChunk(level, chunkKey);
        if (chunk.size() <= limit) return new ArrayList<>(chunk);
        return new ArrayList<>(chunk.subList(0, limit));
    }

    public static synchronized void deleteByIds(ServerLevel level, List<Long> ids) {
        if (!isOverworld(level) || ids == null || ids.isEmpty()) return;
        Map<Long, List<Long>> byChunk = new HashMap<>();
        for (Long id : ids) {
            if (id == null) continue;
            BlockPos pos = BlockPos.of(id);
            long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            byChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(id);
        }
        for (var entry : byChunk.entrySet()) {
            List<PendingSignText> chunk = loadChunk(level, entry.getKey());
            chunk.removeIf(existing -> entry.getValue().contains(existing.id()));
            saveChunk(level, entry.getKey(), chunk);
        }
    }

    public static synchronized void clearLevel(ServerLevel level) {
        if (!isOverworld(level)) return;
        FileStorageIO.deleteTree(FileStoragePathResolver.categoryRoot(level, CATEGORY), null, "清理路牌文本文件失败");
    }

    public static synchronized void clearAllMemory() {}

    private static List<PendingSignText> loadChunk(ServerLevel level, long chunkKey) {
        try {
            Path file = chunkFile(level, chunkKey);
            if (!Files.exists(file)) return new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                SignChunkData data = SignTextShardStorageCompat.GSON.fromJson(reader, SignChunkData.class);
                return data != null && data.items != null ? new ArrayList<>(data.items) : new ArrayList<>();
            }
        } catch (Exception e) {
            FileStorageIO.quarantineCorrupt(chunkFile(level, chunkKey), LOGGER, "路牌文本分片损坏，已隔离");
            return new ArrayList<>();
        }
    }

    private static void saveChunk(ServerLevel level, long chunkKey, List<PendingSignText> items) {
        try {
            Path file = chunkFile(level, chunkKey);
            FileStorageIO.writeStringAtomic(file, SignTextShardStorageCompat.GSON.toJson(new SignChunkData(items)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to save sign chunk", e);
        }
    }

    private static Path chunkFile(ServerLevel level, long chunkKey) {
        Path root = FileStoragePathResolver.categoryRoot(level, CATEGORY);
        long x = (int) (chunkKey >> 32);
        long z = (int) chunkKey;
        return root.resolve(Long.toString(x)).resolve(Long.toString(z) + FILE_SUFFIX);
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static final class SignChunkData {
        List<PendingSignText> items = new ArrayList<>();

        SignChunkData() {}
        SignChunkData(List<PendingSignText> items) { this.items = items == null ? new ArrayList<>() : new ArrayList<>(items); }
    }

    private static final class SignTextShardStorageCompat {
        private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    }
}
