package net.shiroha233.roadweaver.features.path.decoration.text;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.SignTextShardStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 路牌文本服务
 */
public final class SignTextService {
    private SignTextService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final int CHUNK_BUDGET_PER_TICK = 8;
    private static final int WRITE_BUDGET_PER_CHUNK = 32;
    private static final int MAX_DISTANCE_TEXT_LEN = 32;
    private static final int MAX_MEMORY_RETRIES = 2;

    private static final ConcurrentLinkedQueue<Long> DIRTY_CHUNKS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Long, Boolean> DIRTY_MARKS = new ConcurrentHashMap<>();
    /**
     * 保护「出队 + 清标记」与「打标记 + 入队」。
     * 二者不互斥时，tick 在 poll 之后、清除标记之前到达的打标请求会被误判为
     * 已入队而丢弃，随后标记又被清除，该区块的路牌将永久丢失。
     */
    private static final Object DIRTY_LOCK = new Object();

    public static void tick(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;

        if (DIRTY_CHUNKS.isEmpty()) return;

        int budget = CHUNK_BUDGET_PER_TICK;
        while (budget-- > 0) {
            Long chunkKey;
            synchronized (DIRTY_LOCK) {
                chunkKey = DIRTY_CHUNKS.poll();
                if (chunkKey != null) {
                    DIRTY_MARKS.remove(chunkKey);
                }
            }
            if (chunkKey == null) return;

            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            processChunk(level, chunkX, chunkZ);
        }
    }

    public static void clearPending() {
        synchronized (DIRTY_LOCK) {
            DIRTY_CHUNKS.clear();
            DIRTY_MARKS.clear();
        }
        SignTextFileIo.clearMemory();
        SignTextShardStorage.clearAllMemory();
    }

    public static void onChunkReady(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null || !Level.OVERWORLD.equals(level.dimension())) return;
        markChunkDirty(level, chunkPos.x, chunkPos.z);
    }

    public static void onDimensionUnload(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        synchronized (DIRTY_LOCK) {
            DIRTY_CHUNKS.clear();
            DIRTY_MARKS.clear();
        }
        SignTextFileIo.clearMemory();
        SignTextShardStorage.clearLevel(level);
    }

    public static void writeDistanceSign(WorldGenLevel level, BlockPos pos, String text) {
        net.minecraft.world.level.Level l = level.getLevel();
        if (!(l instanceof net.minecraft.server.level.ServerLevel sLevel) || !Level.OVERWORLD.equals(sLevel.dimension())) return;

        try {
            String safeText = sanitizeDistanceText(text);
            SignTextShardStorage.upsert(sLevel, pos, SignTextShardStorage.TYPE_DISTANCE, safeText);
            markChunkDirty(sLevel, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            LOGGER.warn("Queue distance sign text failed at {}", pos, t);
        }
    }

    private static boolean tryWriteDistanceSign(ServerLevel sLevel, BlockPos pos, String text) {
        LevelChunk chunk = sLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockEntity be = chunk.getBlockEntity(pos);
        if (!(be instanceof HangingSignBlockEntity sign)) return false;

        SignText front = sign.getText(true);
        front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.next_location"));
        front = front.setMessage(1, Component.literal(text + " m"));
        front = front.setMessage(2, Component.literal(""));
        front = front.setMessage(3, Component.literal(""));
        sign.setText(front, true);

        SignText back = sign.getText(false);
        back = back.setMessage(0, Component.literal("----------"));
        back = back.setMessage(1, Component.translatable("gui.roadweaver.sign.welcome"));
        back = back.setMessage(2, Component.translatable("gui.roadweaver.sign.traveller"));
        back = back.setMessage(3, Component.literal("----------"));
        sign.setText(back, false);

        sign.setChanged();
        return true;
    }

    public static void writeSeaQuestionSign(WorldGenLevel level, BlockPos pos) {
        net.minecraft.world.level.Level l = level.getLevel();
        if (!(l instanceof net.minecraft.server.level.ServerLevel sLevel) || !Level.OVERWORLD.equals(sLevel.dimension())) return;

        try {
            SignTextShardStorage.upsert(sLevel, pos, SignTextShardStorage.TYPE_SEA_QUESTION, "");
            markChunkDirty(sLevel, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            LOGGER.warn("Queue sea-question sign text failed at {}", pos, t);
        }
    }

    private static boolean tryWriteSeaQuestionSign(ServerLevel sLevel, BlockPos pos) {
        LevelChunk chunk = sLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockEntity be = chunk.getBlockEntity(pos);
        if (!(be instanceof HangingSignBlockEntity sign)) return false;

        SignText front = sign.getText(true);
        front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.sea_question.line1"));
        front = front.setMessage(1, Component.translatable("gui.roadweaver.sign.sea_question.line2"));
        front = front.setMessage(2, Component.literal(""));
        front = front.setMessage(3, Component.literal(""));
        sign.setText(front, true);

        SignText back = sign.getText(false);
        back = back.setMessage(0, Component.literal(""));
        back = back.setMessage(1, Component.literal(""));
        back = back.setMessage(2, Component.literal(""));
        back = back.setMessage(3, Component.literal(""));
        sign.setText(back, false);

        sign.setChanged();
        return true;
    }

    private static void processChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return;
        boolean needRetry = false;

        // 内存区与文件区共用一份预算，避免主线程方块实体写入量翻倍
        List<SignTextShardStorage.PendingSignWrite> memoryPending =
                SignTextShardStorage.takeMemoryByChunk(level, chunkX, chunkZ, WRITE_BUDGET_PER_CHUNK);
        for (SignTextShardStorage.PendingSignWrite row : memoryPending) {
            if (row == null || row.pos() == null) continue;
            boolean ok = tryWrite(level, row.pos(), row.signType(), row.payload());
            if (!ok && !shouldDrop(level, row.pos())) {
                if (row.retryCount() >= MAX_MEMORY_RETRIES) {
                    SignTextFileIo.upsert(level, new SignTextFileStorage.PendingSignWrite(row.pos(), row.signType(), row.payload()));
                } else {
                    SignTextShardStorage.requeue(level, row);
                }
                needRetry = true;
            }
        }

        int remainingBudget = WRITE_BUDGET_PER_CHUNK - memoryPending.size();
        if (remainingBudget <= 0) {
            if (needRetry || SignTextShardStorage.hasMemoryPending(level, chunkX, chunkZ)) {
                markChunkDirty(level, chunkX, chunkZ);
            }
            return;
        }

        // 返回 null 表示缓存未就绪；后台线程加载完成后会重新标记本区块
        List<SignTextFileStorage.PendingSignText> pending =
                SignTextFileIo.poll(level, chunkX, chunkZ, remainingBudget);
        if (pending == null) {
            return;
        }
        if (pending.isEmpty()) {
            if (needRetry || SignTextShardStorage.hasMemoryPending(level, chunkX, chunkZ)) {
                markChunkDirty(level, chunkX, chunkZ);
            }
            return;
        }

        ArrayList<Long> doneIds = new ArrayList<>(pending.size());

        for (SignTextFileStorage.PendingSignText row : pending) {
            boolean ok = tryWrite(level, row.pos(), row.signType(), row.payload());

            if (ok || shouldDrop(level, row.pos())) {
                doneIds.add(row.id());
            } else {
                needRetry = true;
            }
        }

        if (!doneIds.isEmpty()) {
            SignTextFileIo.reportDone(level, chunkX, chunkZ, doneIds);
        }
        if (needRetry || SignTextShardStorage.hasMemoryPending(level, chunkX, chunkZ)) {
            markChunkDirty(level, chunkX, chunkZ);
        }
    }

    private static void markChunkDirty(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (DIRTY_LOCK) {
            if (DIRTY_MARKS.putIfAbsent(key, Boolean.TRUE) != null) return;
            DIRTY_CHUNKS.add(key);
        }
    }

    private static boolean shouldDrop(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).is(BlockTags.ALL_HANGING_SIGNS);
    }

    public static void flushPersistentFallback(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        SignTextShardStorage.flushPending(level);
    }

    private static String sanitizeDistanceText(String text) {
        if (text == null || text.isEmpty()) return "0";
        if (text.length() <= MAX_DISTANCE_TEXT_LEN) return text;
        return text.substring(0, MAX_DISTANCE_TEXT_LEN);
    }

    private static boolean tryWrite(ServerLevel level, BlockPos pos, int signType, String payload) {
        return switch (signType) {
            case SignTextShardStorage.TYPE_DISTANCE -> tryWriteDistanceSign(level, pos, payload);
            case SignTextShardStorage.TYPE_SEA_QUESTION -> tryWriteSeaQuestionSign(level, pos);
            default -> true;
        };
    }
}
