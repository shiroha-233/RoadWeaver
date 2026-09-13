/* 文件职责：缓存已加载区块的结构边界，让路面铺设 worker 线程无需同步触发主线程 chunk 加载。 */
package net.shiroha233.roadweaver.features.path.pathlogic.core;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class StructureAvoidanceService {
    private StructureAvoidanceService() {}

    private static final int SEA_LEVEL = 63;
    private static final int CACHE_CAPACITY = 256;
    private static final AtomicLong PENDING_REQUESTS = new AtomicLong(0);

    private static final Map<Long, ChunkEntry> CHUNK_BBOX_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<Long, ChunkEntry>(CACHE_CAPACITY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ChunkEntry> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            });

    private static final class ChunkEntry {
        final Set<BoundingBox> boxes;
        final boolean ready;
        ChunkEntry(Set<BoundingBox> boxes, boolean ready) {
            this.boxes = boxes;
            this.ready = ready;
        }
        static final ChunkEntry UNREADY = new ChunkEntry(Collections.emptySet(), false);
    }

    public static boolean shouldAvoid(WorldGenLevel world, BlockPos pos) {
        if (!ConfigService.get().structureAvoidanceEnabled()) {
            return false;
        }
        if (world == null || pos == null) return false;

        ServerLevel level = world.getLevel();
        if (level == null) return false;

        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.asLong(cx, cz);
        ChunkEntry entry = CHUNK_BBOX_CACHE.get(key);

        if (entry == null) {
            requestMainThreadPreload(level, cx, cz, key);
            return false;
        }
        if (!entry.ready) {
            return false;
        }
        if (entry.boxes.isEmpty()) return false;

        for (BoundingBox bb : entry.boxes) {
            if (bb.minY() < SEA_LEVEL) continue;
            if (pos.getX() >= bb.minX() && pos.getX() <= bb.maxX()
                    && pos.getZ() >= bb.minZ() && pos.getZ() <= bb.maxZ()) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldAvoidAny(WorldGenLevel world, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (shouldAvoid(world, pos)) {
                return true;
            }
        }
        return false;
    }

    public static void invalidate(ServerLevel level, int cx, int cz) {
        if (level == null) return;
        CHUNK_BBOX_CACHE.remove(ChunkPos.asLong(cx, cz));
    }

    public static void clearAll() {
        CHUNK_BBOX_CACHE.clear();
    }

    private static void requestMainThreadPreload(ServerLevel level, int cx, int cz, long key) {
        CHUNK_BBOX_CACHE.putIfAbsent(key, ChunkEntry.UNREADY);
        if (PENDING_REQUESTS.incrementAndGet() > 32) {
            PENDING_REQUESTS.decrementAndGet();
            return;
        }
        var server = level.getServer();
        if (server == null) {
            PENDING_REQUESTS.decrementAndGet();
            return;
        }
        long epoch = ThreadPoolManager.currentEpoch();
        server.execute(() -> {
            try {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                BlockPos probe = new BlockPos(cx << 4, SEA_LEVEL, cz << 4);
                StructureManager sm = level.structureManager();
                if (!sm.hasAnyStructureAt(probe)) {
                    CHUNK_BBOX_CACHE.put(key, new ChunkEntry(Collections.emptySet(), true));
                    return;
                }
                Map<Structure, LongSet> allStructures = sm.getAllStructuresAt(probe);
                if (allStructures.isEmpty()) {
                    CHUNK_BBOX_CACHE.put(key, new ChunkEntry(Collections.emptySet(), true));
                    return;
                }
                Set<BoundingBox> boxes = new HashSet<>();
                for (Map.Entry<Structure, LongSet> e : allStructures.entrySet()) {
                    Structure structure = e.getKey();
                    for (long chunkLong : e.getValue()) {
                        ChunkPos cp = new ChunkPos(chunkLong);
                        SectionPos sp = SectionPos.of(cp, level.getMinSection());
                        StructureStart start = sm.getStartForStructure(
                                sp,
                                structure,
                                level.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS));
                        if (start == null || !start.isValid()) continue;
                        for (StructurePiece piece : start.getPieces()) {
                            BoundingBox bb = piece.getBoundingBox();
                            if (bb.minY() >= SEA_LEVEL) boxes.add(bb);
                        }
                    }
                }
                CHUNK_BBOX_CACHE.put(key, new ChunkEntry(boxes, true));
            } catch (Throwable ignored) {
                CHUNK_BBOX_CACHE.remove(key);
            } finally {
                PENDING_REQUESTS.decrementAndGet();
            }
        });
    }
}
