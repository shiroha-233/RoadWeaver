package net.shiroha233.roadweaver.client.map.view;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.tile.SingleplayerTerrainTileManager;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.core.model.RequestRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoi;
import net.shiroha233.roadweaver.map.tile.core.MapTileAoiLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 地图视口请求协调。
 */
public final class MapViewportController {
    private static final int SNAPSHOT_PADDING_BLOCKS = 0;

    private MapViewportController() {}

    public static RequestRect currentRequestRect(MapView view) {
        return new RequestRect(
                (int) Math.floor(Math.min(view.getMinX(), view.getMaxX())) - SNAPSHOT_PADDING_BLOCKS,
                (int) Math.floor(Math.min(view.getMinZ(), view.getMaxZ())) - SNAPSHOT_PADDING_BLOCKS,
                (int) Math.ceil(Math.max(view.getMinX(), view.getMaxX())) + SNAPSHOT_PADDING_BLOCKS,
                (int) Math.ceil(Math.max(view.getMinZ(), view.getMaxZ())) + SNAPSHOT_PADDING_BLOCKS);
    }

    public static List<RequestRect> splitIncrementalRequests(RequestRect requestRect) {
        if (requestRect == null) return List.of();
        int minX = requestRect.minX();
        int minZ = requestRect.minZ();
        int maxX = requestRect.maxX();
        int maxZ = requestRect.maxZ();
        int width = Math.max(1, maxX - minX + 1);
        int height = Math.max(1, maxZ - minZ + 1);
        int innerMinX = minX + width / 4;
        int innerMaxX = maxX - width / 4;
        int innerMinZ = minZ + height / 4;
        int innerMaxZ = maxZ - height / 4;
        if (innerMinX >= innerMaxX || innerMinZ >= innerMaxZ) {
            return List.of(requestRect);
        }
        return List.of(
                new RequestRect(innerMinX, innerMinZ, innerMaxX, innerMaxZ),
                requestRect
        );
    }

    public static List<RequestRect> missingRects(RequestRect target, List<RequestRect> loadedRects) {
        if (target == null) return List.of();
        if (loadedRects == null || loadedRects.isEmpty()) return List.of(target);
        ArrayList<RequestRect> missing = new ArrayList<>();
        missing.add(target);
        for (RequestRect loaded : loadedRects) {
            if (loaded == null || missing.isEmpty()) continue;
            ArrayList<RequestRect> next = new ArrayList<>();
            for (RequestRect rect : missing) {
                next.addAll(rect.subtract(loaded));
            }
            missing = next;
        }
        return missing;
    }

    public static List<RequestRect> prioritizeMissingRects(RequestRect target, List<RequestRect> loadedRects) {
        List<RequestRect> missing = missingRects(target, loadedRects);
        if (missing.isEmpty()) return List.of();
        if (missing.size() == 1) return missing;
        ArrayList<RequestRect> ordered = new ArrayList<>();
        for (RequestRect seed : splitIncrementalRequests(target)) {
            for (RequestRect candidate : missingRects(seed, loadedRects)) {
                List<RequestRect> fragments = List.of(candidate);
                for (RequestRect already : ordered) {
                    ArrayList<RequestRect> next = new ArrayList<>();
                    for (RequestRect fragment : fragments) {
                        next.addAll(fragment.subtract(already));
                    }
                    fragments = next;
                    if (fragments.isEmpty()) break;
                }
                ordered.addAll(fragments);
            }
        }
        for (RequestRect rect : missing) {
            List<RequestRect> fragments = List.of(rect);
            for (RequestRect already : ordered) {
                ArrayList<RequestRect> next = new ArrayList<>();
                for (RequestRect fragment : fragments) {
                    next.addAll(fragment.subtract(already));
                }
                fragments = next;
                if (fragments.isEmpty()) break;
            }
            ordered.addAll(fragments);
        }
        return ordered;
    }

    public static ResourceLocation syncDimensionAndRestoreCache(Minecraft mc,
                                                                ResourceLocation currentDimensionId,
                                                                Consumer<MapSnapshot> onCachedSnapshot) {
        ResourceLocation nextDimensionId = mc != null && mc.level != null ? mc.level.dimension().location() : null;
        if (nextDimensionId == null) {
            return currentDimensionId;
        }
        if (currentDimensionId == null || !nextDimensionId.equals(currentDimensionId)) {
            MapSnapshot cached = MapSnapshotCache.peek(nextDimensionId);
            if (cached != null && onCachedSnapshot != null) {
                onCachedSnapshot.accept(cached);
            }
            return nextDimensionId;
        }
        return currentDimensionId;
    }

    public static ServerLevel resolveSingleplayerLevel(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return null;
        return server.getLevel(mc.level.dimension());
    }

    public static void requestTerrainTiles(Minecraft mc,
                                           SingleplayerTerrainTileManager terrainTiles,
                                           MapView view,
                                           int contentW,
                                           int contentH) {
        if (mc == null || terrainTiles == null || view == null) return;
        ServerLevel level = resolveSingleplayerLevel(mc);
        if (level == null) return;

        int cx = mc.player != null ? (int) Math.round(mc.player.getX()) : 0;
        int cz = mc.player != null ? (int) Math.round(mc.player.getZ()) : 0;
        int radiusBlocks = MapTileAoiLocator.dynamicPlanningRadiusBlocks();
        MapTileAoi aoi = new MapTileAoi(level.dimension().location(), cx, cz, radiusBlocks);
        terrainTiles.request(level, aoi, view, contentW, contentH);
    }
}
