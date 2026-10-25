package net.shiroha233.roadweaver.persistence.chunk;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 道路的二维实际覆盖范围。
 *
 * <p>每个区块使用 256 bit 位图。中心点的宽度圆盘用于覆盖缺少 positions 的旧/中间数据，
 * positions 则提供生成器真正光栅化出的精确列。查询矩形时先命中区块，再用位图过滤，
 * 因此不会因为整条道路的 AABB 而误报。</p>
 */
public final class RoadFootprint {
    private static final int WORD_COUNT = 4;
    private static final int MAX_WIDTH_RADIUS = 256;
    private static final RoadFootprint EMPTY = new RoadFootprint(Map.of(),
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    private final Map<Long, ChunkMask> chunks;
    private final Set<Long> chunkKeys;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    private RoadFootprint(Map<Long, ChunkMask> chunks, int minX, int minZ, int maxX, int maxZ) {
        this.chunks = Collections.unmodifiableMap(chunks);
        this.chunkKeys = Collections.unmodifiableSet(new LinkedHashSet<>(chunks.keySet()));
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public static RoadFootprint empty() {
        return EMPTY;
    }

    public static RoadFootprint from(RoadData road) {
        if (road == null) return EMPTY;
        Builder builder = new Builder();
        int width = Math.max(1, road.width());
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        if (segments != null) {
            for (RoadSegmentPlacement segment : segments) {
                if (segment == null) continue;
                BlockPos middle = segment.middlePos();
                if (middle != null) {
                    builder.addDisk(middle.getX(), middle.getZ(), width);
                }
                List<BlockPos> positions = segment.positions();
                if (positions != null) {
                    for (BlockPos position : positions) builder.add(position);
                }
            }
        }
        // 跨度端点有时来自桥梁后处理，补入端点可以避免桥头跨区块时漏索引。
        List<RoadSpan> spans = road.spans();
        if (spans != null) {
            for (RoadSpan span : spans) {
                if (span == null) continue;
                if (span.start() != null) builder.addDisk(span.start().getX(), span.start().getZ(), width);
                if (span.end() != null) builder.addDisk(span.end().getX(), span.end().getZ(), width);
            }
        }
        return builder.build();
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public Set<Long> chunkKeys() {
        return chunkKeys;
    }

    public Map<Long, ChunkMask> chunks() {
        return chunks;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean touchesChunk(int chunkX, int chunkZ) {
        return chunks.containsKey(RoadChunkKey.pack(chunkX, chunkZ));
    }

    public boolean intersectsRect(int queryMinX, int queryMinZ, int queryMaxX, int queryMaxZ) {
        if (isEmpty()) return false;
        int minX = Math.min(queryMinX, queryMaxX);
        int maxX = Math.max(queryMinX, queryMaxX);
        int minZ = Math.min(queryMinZ, queryMaxZ);
        int maxZ = Math.max(queryMinZ, queryMaxZ);
        if (maxX < this.minX || minX > this.maxX || maxZ < this.minZ || minZ > this.maxZ) return false;

        int firstChunkX = Math.floorDiv(minX, 16);
        int lastChunkX = Math.floorDiv(maxX, 16);
        int firstChunkZ = Math.floorDiv(minZ, 16);
        int lastChunkZ = Math.floorDiv(maxZ, 16);
        for (long cx = firstChunkX; cx <= (long) lastChunkX; cx++) {
            for (long cz = firstChunkZ; cz <= (long) lastChunkZ; cz++) {
                ChunkMask mask = chunks.get(RoadChunkKey.pack((int) cx, (int) cz));
                if (mask != null && mask.intersects((int) cx, (int) cz, minX, minZ, maxX, maxZ)) return true;
            }
        }
        return false;
    }

    public ChunkMask mask(long packedChunk) {
        return chunks.get(packedChunk);
    }

    /** 区块内 16x16 列位图，公开为只读值对象以便未来 stamp 编译复用。 */
    public static final class ChunkMask {
        private final long[] words;

        private ChunkMask(long[] words) {
            this.words = words.clone();
        }

        public boolean isEmpty() {
            for (long word : words) if (word != 0L) return false;
            return true;
        }

        public boolean containsLocal(int localX, int localZ) {
            if ((localX | localZ) < 0 || localX > 15 || localZ > 15) return false;
            int bit = (localZ << 4) | localX;
            return (words[bit >>> 6] & (1L << (bit & 63))) != 0L;
        }

        public boolean intersects(int chunkX, int chunkZ,
                                  int minX, int minZ, int maxX, int maxZ) {
            long originX = (long) chunkX * 16L;
            long originZ = (long) chunkZ * 16L;
            int fromX = (int) Math.max(0L, (long) minX - originX);
            int toX = (int) Math.min(15L, (long) maxX - originX);
            int fromZ = (int) Math.max(0L, (long) minZ - originZ);
            int toZ = (int) Math.min(15L, (long) maxZ - originZ);
            if (fromX > toX || fromZ > toZ) return false;
            if (fromX == 0 && toX == 15 && fromZ == 0 && toZ == 15) return !isEmpty();
            for (int z = fromZ; z <= toZ; z++) {
                for (int x = fromX; x <= toX; x++) {
                    if (containsLocal(x, z)) return true;
                }
            }
            return false;
        }

        public long[] words() {
            return words.clone();
        }
    }

    private static final class Builder {
        private final Map<Long, MutableMask> chunks = new LinkedHashMap<>();
        private int minX = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void add(BlockPos position) {
            if (position != null) add(position.getX(), position.getZ());
        }

        void add(int x, int z) {
            long chunkKey = RoadChunkKey.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            MutableMask mask = chunks.computeIfAbsent(chunkKey, ignored -> new MutableMask());
            int localX = x & 15;
            int localZ = z & 15;
            int bit = (localZ << 4) | localX;
            mask.words[bit >>> 6] |= 1L << (bit & 63);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }

        void addDisk(int centerX, int centerZ, int width) {
            double halfWidth = Math.max(0.5D, Math.max(1, width) / 2.0D);
            int radius = Math.min(MAX_WIDTH_RADIUS, (int) Math.ceil(halfWidth));
            double radiusSquared = halfWidth * halfWidth + 1.0E-9D;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if ((double) dx * dx + (double) dz * dz <= radiusSquared) {
                        long x = (long) centerX + dx;
                        long z = (long) centerZ + dz;
                        if (x >= Integer.MIN_VALUE && x <= Integer.MAX_VALUE
                                && z >= Integer.MIN_VALUE && z <= Integer.MAX_VALUE) {
                            add((int) x, (int) z);
                        }
                    }
                }
            }
        }

        RoadFootprint build() {
            if (chunks.isEmpty()) return EMPTY;
            LinkedHashMap<Long, ChunkMask> immutable = new LinkedHashMap<>(chunks.size());
            for (Map.Entry<Long, MutableMask> entry : chunks.entrySet()) {
                if (!entry.getValue().isEmpty()) immutable.put(entry.getKey(), new ChunkMask(entry.getValue().words));
            }
            if (immutable.isEmpty()) return EMPTY;
            return new RoadFootprint(immutable, minX, minZ, maxX, maxZ);
        }
    }

    private static final class MutableMask {
        private final long[] words = new long[WORD_COUNT];

        boolean isEmpty() {
            for (long word : words) if (word != 0L) return false;
            return true;
        }
    }
}
