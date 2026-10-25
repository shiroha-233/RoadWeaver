package net.shiroha233.roadweaver.map.tile.backfill;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegionSampler;

import java.util.Optional;

/**
 * 精确地图派生物补全用例，不允许触发任何采样后端。
 */
public final class AccurateTerrainTileBackfillService {
    private AccurateTerrainTileBackfillService() {}

    public static boolean backfillMissing(ServerLevel level,
                                          int minBlockX,
                                          int minBlockZ,
                                          int maxBlockX,
                                          int maxBlockZ,
                                          int step) {
        TerrainSamplingCache cache = new TerrainSamplingCache();
        boolean complete = true;
        try {
            for (int zoom = MapTileScheme.MIN_ZOOM; zoom <= MapTileScheme.MAX_ZOOM; zoom++) {
                MapTileRect rect = MapTileScheme.tileRectForBlockRect(
                        zoom, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
                for (MapTileCoord coord : rect.coords()) {
                    int intersectionMinX = Math.max(minBlockX, MapTileScheme.tileMinBlockX(coord));
                    int intersectionMinZ = Math.max(minBlockZ, MapTileScheme.tileMinBlockZ(coord));
                    int intersectionMaxX = Math.min(maxBlockX, MapTileScheme.tileMaxBlockX(coord));
                    int intersectionMaxZ = Math.min(maxBlockZ, MapTileScheme.tileMaxBlockZ(coord));
                    if (ServerMapTileStorage.hasCoverage(
                            level, MapTileLayer.TERRAIN_ACCURATE, coord,
                            intersectionMinX, intersectionMinZ, intersectionMaxX, intersectionMaxZ)) {
                        continue;
                    }
                    Optional<AccurateTerrainRegion> restored = AccurateTerrainRegionSampler.restoreStored(
                            level, cache,
                            intersectionMinX, intersectionMinZ, intersectionMaxX, intersectionMaxZ,
                            step);
                    if (restored.isEmpty()) {
                        complete = false;
                        continue;
                    }
                    AccurateTerrainRegion region = restored.get();
                    try {
                        AccurateTerrainPngWriter.writeTerrainTile(level, region, coord);
                    } finally {
                        region.dispose();
                    }
                }
            }
            return complete;
        } finally {
            cache.clear();
        }
    }
}
