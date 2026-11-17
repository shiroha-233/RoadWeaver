package net.shiroha233.roadweaver.client.map.cache;

import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.core.model.MapSnapshotPatch;
import net.shiroha233.roadweaver.map.data.MapSnapshot;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MapSnapshotCache {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Identifier, MapSnapshot>> BY_WORLD = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Identifier, MapSnapshotStore>> STORES_BY_WORLD = new ConcurrentHashMap<>();
    private static final AtomicInteger CLEAR_SEQ = new AtomicInteger();
    private static volatile String currentWorldId = null;

    static {
        ClientMapAccessGuard.onRevoked(MapSnapshotCache::clearNow);
    }

    private MapSnapshotCache() {
    }

    public static void setCurrentWorldId(String worldId) {
        String previous = currentWorldId;
        if (previous != null && !previous.equals(worldId)) {
            BY_WORLD.remove(previous);
            STORES_BY_WORLD.remove(previous);
        }
        currentWorldId = worldId;
    }

    public static MapSnapshot peek(Identifier dimensionId) {
        if (dimensionId == null) {
            return null;
        }
        String wid = currentWorldId;
        if (wid == null) {
            return null;
        }
        ConcurrentHashMap<Identifier, MapSnapshot> bucket = BY_WORLD.get(wid);
        if (bucket == null) {
            return null;
        }
        return bucket.get(dimensionId);
    }

    public static void put(Identifier dimensionId, MapSnapshot snapshot) {
        if (dimensionId == null) {
            return;
        }
        String wid = currentWorldId;
        if (wid == null) {
            return;
        }
        ConcurrentHashMap<Identifier, MapSnapshot> bucket = BY_WORLD.computeIfAbsent(wid, key -> new ConcurrentHashMap<>());
        if (snapshot == null) {
            bucket.remove(dimensionId);
        } else {
            bucket.put(dimensionId, snapshot);
        }
    }

    public static MapSnapshotStore store(Identifier dimensionId) {
        if (dimensionId == null) {
            return new MapSnapshotStore();
        }
        String wid = currentWorldId;
        if (wid == null) {
            return new MapSnapshotStore();
        }
        ConcurrentHashMap<Identifier, MapSnapshotStore> stores = STORES_BY_WORLD.computeIfAbsent(wid, key -> new ConcurrentHashMap<>());
        return stores.computeIfAbsent(dimensionId, id -> {
            MapSnapshot cached = peek(id);
            return cached != null ? MapSnapshotStore.fromSnapshot(cached) : new MapSnapshotStore();
        });
    }

    public static void putStoreSnapshot(Identifier dimensionId, MapSnapshotStore store) {
        if (dimensionId == null || store == null) {
            return;
        }
        put(dimensionId, store.snapshot());
    }

    public static void applyPatch(Identifier dimensionId, MapSnapshotPatch patch) {
        if (dimensionId == null || patch == null) {
            return;
        }
        MapSnapshotStore store = store(dimensionId);
        store.apply(patch);
        putStoreSnapshot(dimensionId, store);
    }

    public static void remove(Identifier dimensionId) {
        if (dimensionId == null) {
            return;
        }
        String wid = currentWorldId;
        if (wid == null) {
            return;
        }
        ConcurrentHashMap<Identifier, MapSnapshot> bucket = BY_WORLD.get(wid);
        if (bucket != null) {
            bucket.remove(dimensionId);
        }
        ConcurrentHashMap<Identifier, MapSnapshotStore> stores = STORES_BY_WORLD.get(wid);
        if (stores != null) {
            stores.remove(dimensionId);
        }
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

    private static void clearCurrentWorld() {
        String wid = currentWorldId;
        if (wid != null) {
            BY_WORLD.remove(wid);
            STORES_BY_WORLD.remove(wid);
        }
        MapAutomaticPlanningSamplingCache.clear();
    }
}
