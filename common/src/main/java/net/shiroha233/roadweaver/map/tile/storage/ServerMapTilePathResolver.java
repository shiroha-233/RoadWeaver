/* 文件职责：统一解析服务端地图瓦片目录、图层目录与旧版 terrain 迁移路径。 */
package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.util.WorldRootPaths;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 服务端地图瓦片路径解析。
 */
public final class ServerMapTilePathResolver {
    private static final String ROOT_DIR = "data/roadweaver/map";
    private static final String LEGACY_TERRAIN_DIR = "terrain_v2";
    private static final String MIGRATION_DIR = "migration";
    private static final String TERRAIN_LAYER_MIGRATION_MARKER = "terrain-layer-v1.done";

    private ServerMapTilePathResolver() {}

    public static Path mapRoot(ServerLevel level) {
        return WorldRootPaths.root(level).resolve(ROOT_DIR);
    }

    public static Path dimensionRoot(ServerLevel level) {
        return mapRoot(level).resolve(dimensionKey(level.dimension().location()));
    }

    public static Path layerRoot(ServerLevel level, MapTileLayer layer) {
        return layerRoot(dimensionRoot(level), layer);
    }

    public static Path layerRoot(Path dimensionRoot, MapTileLayer layer) {
        return requireDimensionRoot(dimensionRoot).resolve(requireLayer(layer).folderName());
    }

    public static Path tilePath(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return tilePath(dimensionRoot(level), layer, coord);
    }

    public static Path tilePath(Path dimensionRoot, MapTileLayer layer, MapTileCoord coord) {
        return tilePath(layerRoot(dimensionRoot, layer), coord);
    }

    public static Path legacyTerrainRoot(ServerLevel level) {
        return legacyTerrainRoot(dimensionRoot(level));
    }

    public static Path legacyTerrainRoot(Path dimensionRoot) {
        return requireDimensionRoot(dimensionRoot).resolve(LEGACY_TERRAIN_DIR);
    }

    public static Path legacyTerrainTilePath(ServerLevel level, MapTileCoord coord) {
        return legacyTerrainTilePath(dimensionRoot(level), coord);
    }

    public static Path legacyTerrainTilePath(Path dimensionRoot, MapTileCoord coord) {
        return tilePath(legacyTerrainRoot(dimensionRoot), coord);
    }

    public static Path terrainLayerMigrationMarker(ServerLevel level) {
        return terrainLayerMigrationMarker(dimensionRoot(level));
    }

    public static Path terrainLayerMigrationMarker(Path dimensionRoot) {
        return requireDimensionRoot(dimensionRoot)
                .resolve(MIGRATION_DIR)
                .resolve(TERRAIN_LAYER_MIGRATION_MARKER);
    }

    public static String dimensionKey(ResourceLocation location) {
        if (location == null) {
            return "unknown";
        }
        return location.getNamespace() + "_" + location.getPath().replace('/', '_');
    }

    private static Path tilePath(Path layerRoot, MapTileCoord coord) {
        Objects.requireNonNull(coord, "coord");
        return layerRoot
                .resolve(coord.zoomFolder())
                .resolve(Integer.toString(coord.tileX()))
                .resolve(coord.tileFileName());
    }

    private static Path requireDimensionRoot(Path dimensionRoot) {
        return Objects.requireNonNull(dimensionRoot, "dimensionRoot");
    }

    private static MapTileLayer requireLayer(MapTileLayer layer) {
        return Objects.requireNonNull(layer, "layer");
    }
}
