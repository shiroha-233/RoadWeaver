package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形 PNG 派生物的缺口查询。
 */
public final class TerrainTileWritePlan {
    private TerrainTileWritePlan() {}

    public static List<MapTileCoord> missingTiles(ServerLevel level,
                                                  MapTileLayer layer,
                                                  int minBlockX,
                                                  int minBlockZ,
                                                  int maxBlockX,
                                                  int maxBlockZ) {
        if (level == null || layer == null || !layer.isTerrainLayer()) {
            return List.of();
        }
        ArrayList<MapTileCoord> missing = new ArrayList<>();
        for (int zoom = MapTileScheme.MIN_ZOOM; zoom <= MapTileScheme.MAX_ZOOM; zoom++) {
            MapTileRect rect = MapTileScheme.tileRectForBlockRect(
                    zoom, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            for (int tileZ = rect.minTileZ(); tileZ <= rect.maxTileZ(); tileZ++) {
                for (int tileX = rect.minTileX(); tileX <= rect.maxTileX(); tileX++) {
                    MapTileCoord coord = new MapTileCoord(zoom, tileX, tileZ);
                    int intersectionMinX = Math.max(minBlockX, MapTileScheme.tileMinBlockX(coord));
                    int intersectionMinZ = Math.max(minBlockZ, MapTileScheme.tileMinBlockZ(coord));
                    int intersectionMaxX = Math.min(maxBlockX, MapTileScheme.tileMaxBlockX(coord));
                    int intersectionMaxZ = Math.min(maxBlockZ, MapTileScheme.tileMaxBlockZ(coord));
                    if (!ServerMapTileStorage.hasCoverage(
                            level,
                            layer,
                            coord,
                            intersectionMinX,
                            intersectionMinZ,
                            intersectionMaxX,
                            intersectionMaxZ)) {
                        missing.add(coord);
                    }
                }
            }
        }
        return List.copyOf(missing);
    }
}
