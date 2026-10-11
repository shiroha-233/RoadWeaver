package net.shiroha233.roadweaver.pathfinding.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 以 4x4 群系格为单位的容量受限 LRU。
 *
 * <p>群系噪声在 4x4 水平格内恒定，故缓存键按格而非按方块，
 * 让同一格内的相邻方块共享一次噪声采样。分片实现让并行道路生成
 * 不会互相阻塞于同一张表。</p>
 */
abstract class BiomeLookupCache<T> {
    private static final int SHARD_COUNT = 64;

    private final Shard<T>[] shards;

    @SuppressWarnings("unchecked")
    BiomeLookupCache(int capacity) {
        if (capacity < SHARD_COUNT) {
            throw new IllegalArgumentException("capacity must be at least " + SHARD_COUNT);
        }
        shards = (Shard<T>[]) new Shard<?>[SHARD_COUNT];
        int shardCapacity = capacity / SHARD_COUNT;
        for (int i = 0; i < SHARD_COUNT; i++) {
            shards[i] = new Shard<>(shardCapacity);
        }
    }

    T get(int x, int z) {
        int quartX = x >> 2;
        int quartZ = z >> 2;
        long key = ((long) quartX << 32) | (quartZ & 0xffffffffL);
        int hash = 31 * quartX + quartZ;
        hash ^= hash >>> 16;
        return shards[(hash & Integer.MAX_VALUE) % SHARD_COUNT].get(key, quartX, quartZ, this);
    }

    /**
     * 未命中时的群系噪声采样。实现必须对同一格返回等价结果。
     */
    protected abstract T sample(int quartX, int quartZ);

    private static final class Shard<T> {
        private final int capacity;
        private final Map<Long, T> entries = new LinkedHashMap<>(256, 0.75f, true);

        Shard(int capacity) {
            this.capacity = capacity;
        }

        synchronized T get(long key, int quartX, int quartZ, BiomeLookupCache<T> owner) {
            T value = entries.get(key);
            if (value != null) {
                TerrainSamplingStats.recordCacheHit();
                return value;
            }
            TerrainSamplingStats.recordCacheMiss();
            value = Objects.requireNonNull(owner.sample(quartX, quartZ), "biome lookup returned null");
            entries.put(key, value);
            if (entries.size() > capacity) {
                entries.remove(entries.keySet().iterator().next());
            }
            return value;
        }
    }
}