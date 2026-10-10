/* 文件职责：按存档隔离缓存地图快照，并统一清理地图运行态缓存。 */
package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地图快照缓存 - 按存档隔离的线程安全缓存管理
 */
public final class MapSnapshotCache {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<ResourceLocation, MapSnapshot>> BY_WORLD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<ResourceLocation, MapSnapshotStore>> STORES_BY_WORLD = new ConcurrentHashMap<>();
    private static volatile String currentWorldId = null;
    private static final AtomicInteger CLEAR_SEQ = new AtomicInteger();

    private MapSnapshotCache() {}

    public static void setCurrentWorldId(String worldId) {
        String previous = currentWorldId;
        if (previous != null && !previous.equals(worldId)) {
            // 释放上一个存档的桶：调用方随后的 clearNow() 清的是新存档，
            // 不在此释放会随每次进出存档累积一份完整路网副本
            BY_WORLD.remove(previous);
            STORES_BY_WORLD.remove(previous);
        }
        currentWorldId = worldId;
    }

    public static String getCurrentWorldId() {
        return currentWorldId;
    }

    public static MapSnapshot peek(ResourceLocation dimensionId) {
        if (dimensionId == null) return null;
        String wid = currentWorldId;
        if (wid == null) return null;
        ConcurrentHashMap<ResourceLocation, MapSnapshot> bucket = BY_WORLD.get(wid);
        if (bucket == null) return null;
        return bucket.get(dimensionId);
    }

    public static void put(ResourceLocation dimensionId, MapSnapshot s) {
        if (dimensionId == null) return;
        String wid = currentWorldId;
        if (wid == null) return;
        ConcurrentHashMap<ResourceLocation, MapSnapshot> bucket = BY_WORLD.computeIfAbsent(wid, k -> new ConcurrentHashMap<>());
        if (s == null) bucket.remove(dimensionId);
        else bucket.put(dimensionId, s);
    }

    public static MapSnapshotStore store(ResourceLocation dimensionId) {
        if (dimensionId == null) return new MapSnapshotStore();
        String wid = currentWorldId;
        if (wid == null) return new MapSnapshotStore();
        ConcurrentHashMap<ResourceLocation, MapSnapshotStore> stores = STORES_BY_WORLD.computeIfAbsent(wid, k -> new ConcurrentHashMap<>());
        return stores.computeIfAbsent(dimensionId, id -> {
            MapSnapshot cached = peek(id);
            return cached != null ? MapSnapshotStore.fromSnapshot(cached) : new MapSnapshotStore();
        });
    }

    public static void putStoreSnapshot(ResourceLocation dimensionId, MapSnapshotStore store) {
        if (dimensionId == null || store == null) return;
        put(dimensionId, store.snapshot());
    }

    public static void applyPatch(ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (dimensionId == null || patch == null) return;
        MapSnapshotStore store = store(dimensionId);
        store.apply(patch);
        putStoreSnapshot(dimensionId, store);
    }

    public static void remove(ResourceLocation dimensionId) {
        if (dimensionId == null) return;
        String wid = currentWorldId;
        if (wid == null) return;
        ConcurrentHashMap<ResourceLocation, MapSnapshot> bucket = BY_WORLD.get(wid);
        if (bucket != null) bucket.remove(dimensionId);
        ConcurrentHashMap<ResourceLocation, MapSnapshotStore> stores = STORES_BY_WORLD.get(wid);
        if (stores != null) stores.remove(dimensionId);
    }

    public static void scheduleClear(long delayMs) {
        int token = CLEAR_SEQ.incrementAndGet();
        long d = Math.max(0L, delayMs);
        Executor delayed = CompletableFuture.delayedExecutor(d, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> {
            if (CLEAR_SEQ.get() == token) {
                clearCurrentWorld();
            }
        }, delayed);
    }

    public static void cancelClear() {
        CLEAR_SEQ.incrementAndGet();
    }

    public static void clearNow() {
        CLEAR_SEQ.incrementAndGet();
        clearCurrentWorld();
    }

    public static void clearAll() {
        CLEAR_SEQ.incrementAndGet();
        BY_WORLD.clear();
        STORES_BY_WORLD.clear();
        MapAutomaticPlanningSamplingCache.clear();
    }

    public static void clearWorld(String worldId) {
        if (worldId == null) return;
        BY_WORLD.remove(worldId);
        STORES_BY_WORLD.remove(worldId);
        if (worldId.equals(currentWorldId)) {
            MapAutomaticPlanningSamplingCache.clear();
        }
    }

    private static void clearCurrentWorld() {
        String wid = currentWorldId;
        if (wid != null) {
            BY_WORLD.remove(wid);
            STORES_BY_WORLD.remove(wid);
        }
        MapAutomaticPlanningSamplingCache.clear();
    }
}


