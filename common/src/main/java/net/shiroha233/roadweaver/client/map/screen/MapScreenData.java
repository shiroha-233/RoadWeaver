package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.client.map.MapLoadSession;
import net.shiroha233.roadweaver.client.map.MapState;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.client.map.cache.MapFilterState;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotStore;
import net.shiroha233.roadweaver.client.map.load.MapSnapshotRequests;
import net.shiroha233.roadweaver.client.map.view.MapViewportController;
import net.shiroha233.roadweaver.core.model.MapLoadPhase;
import net.shiroha233.roadweaver.core.model.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.RequestRect;
import net.shiroha233.roadweaver.map.data.MapSnapshot;

import java.util.List;

/**
 * 地图屏幕的数据编排：维护当前快照、装载会话、维度绑定与过滤视图缓存。
 */
final class MapScreenData {

    private final MapState state;

    private final MapView view;

    private final MapFilterState filterState;

    private MapSnapshot snapshot = MapSnapshot.empty();

    private Identifier dimensionId;

    private MapSnapshotStore store = new MapSnapshotStore();

    private MapLoadSession loadSession;

    private MapSnapshot filteredSnapshot;

    private int filteredRevision = -1;

    private MapSnapshot filteredSource;

    MapScreenData(MapState state, MapView view, MapFilterState filterState) {
        this.state = state;
        this.view = view;
        this.filterState = filterState;
    }

    MapSnapshot snapshot() {
        return snapshot;
    }

    Identifier dimensionId() {
        return dimensionId;
    }

    MapSnapshotStore store() {
        return store;
    }

    MapLoadSession loadSession() {
        return loadSession;
    }

    void bindFromLevel(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        bind(minecraft.level.dimension().identifier());
    }

    void bind(Identifier nextDimensionId) {
        this.dimensionId = nextDimensionId;
        this.store = MapSnapshotCache.store(nextDimensionId);
        this.snapshot = store.snapshot();
        invalidateFiltered();
    }

    void resetView() {
        view.resetFromSnapshot(snapshot);
    }

    void clearSession() {
        loadSession = null;
    }

    void persistStore() {
        MapSnapshotCache.putStoreSnapshot(dimensionId, store);
    }

    void refreshView() {
        RequestRect rect = MapViewportController.currentRequestRect(view);
        for (MapLoadPhase phase : MapLoadPhase.values()) {
            store.clearRect(phase, rect);
        }
        snapshot = store.snapshot();
        invalidateFiltered();
        MapSnapshotCache.put(dimensionId, snapshot);
        loadSession = null;
    }

    void requestView(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        RequestRect requestRect = MapViewportController.currentRequestRect(view);
        Identifier synced = MapViewportController.syncDimensionAndRestoreCache(minecraft, dimensionId,
                cached -> {
                    store = MapSnapshotCache.store(minecraft.level.dimension().identifier());
                    snapshot = store.snapshot();
                });
        dimensionId = synced;
        Identifier levelId = minecraft.level != null ? minecraft.level.dimension().identifier() : dimensionId;
        if (levelId == null) {
            levelId = Identifier.fromNamespaceAndPath("minecraft", "overworld");
        }
        if (dimensionId == null || !levelId.equals(dimensionId)) {
            bind(levelId);
        }
        dimensionId = levelId;

        List<RequestRect> structureRects = MapViewportController.prioritizeMissingRects(
                requestRect, store.loadedRects(MapLoadPhase.STRUCTURES));
        List<RequestRect> roadRects = MapViewportController.prioritizeMissingRects(
                requestRect, store.loadedRects(MapLoadPhase.ROADS));
        List<RequestRect> connectionRects = MapViewportController.prioritizeMissingRects(
                requestRect, store.loadedRects(MapLoadPhase.CONNECTIONS));
        List<MapLoadSession.ResponseRequest> requests;
        if (!structureRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(structureRects, List.of(), List.of());
        } else if (!roadRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), roadRects, List.of());
        } else if (!connectionRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), List.of(), connectionRects);
        } else {
            loadSession = null;
            snapshot = store.snapshot();
            MapSnapshotCache.put(dimensionId, snapshot);
            return;
        }
        int requestSeq = state.incrementAndGetRequestSeq();
        loadSession = new MapLoadSession(requestSeq, dimensionId, requests);
        MapSnapshotRequests.dispatchRemote(requestSeq, dimensionId, requests);
    }

    MapSnapshot visibleSnapshot() {
        if (filteredSnapshot == null
                || filteredSource != snapshot
                || filteredRevision != filterState.revision()) {
            filteredSnapshot = filterState.apply(snapshot);
            filteredSource = snapshot;
            filteredRevision = filterState.revision();
        }
        return filteredSnapshot;
    }

    void invalidateFiltered() {
        filteredSnapshot = null;
        filteredSource = null;
        filteredRevision = -1;
    }

    void applyPatch(Identifier patchDimensionId, MapSnapshotPatch patch) {
        if (patchDimensionId == null || patch == null) {
            return;
        }
        if (dimensionId != null && !patchDimensionId.equals(dimensionId)) {
            return;
        }
        store.apply(patch);
        snapshot = store.snapshot();
        MapSnapshotCache.put(patchDimensionId, snapshot);
    }

    boolean acceptSnapshotPart(int requestSeq,
                               Identifier partDimensionId,
                               MapLoadPhase phase,
                               int responseIndex,
                               MapSnapshot part) {
        if (partDimensionId == null || part == null) {
            return false;
        }
        if (requestSeq != state.getCurrentRequestSeq()) {
            return false;
        }
        if (dimensionId != null && !partDimensionId.equals(dimensionId)) {
            return false;
        }
        if (loadSession == null || !loadSession.accepts(requestSeq, partDimensionId)) {
            return false;
        }
        MapLoadSession.ResponseRequest request = loadSession.requestAt(responseIndex);
        RequestRect loadedRect = request != null ? request.rect() : null;
        MapLoadPhase loadedPhase = request != null ? request.phase() : phase;
        store.merge(loadedPhase, loadedRect, part);
        loadSession.markReceived(loadedPhase, responseIndex, part);
        snapshot = store.snapshot();
        MapSnapshotCache.put(partDimensionId, snapshot);
        if (loadSession.isComplete()) {
            loadSession = null;
            return true;
        }
        return false;
    }

    void clearStructures(RequestRect rect) {
        if (rect == null || dimensionId == null) {
            return;
        }
        store.clearRect(MapLoadPhase.STRUCTURES, rect);
        snapshot = store.snapshot();
        invalidateFiltered();
        MapSnapshotCache.put(dimensionId, snapshot);
    }
}
