/* 文件职责：将精确 terrain 采样结果写入 accurate terrain 瓦片目录。 */
package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.render.HeightShader;
import net.shiroha233.roadweaver.map.tile.render.TerrainTilePalette;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;
import net.shiroha233.roadweaver.map.tile.storage.TerrainTileWritePlan;
import net.shiroha233.roadweaver.util.StripedLocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 精确地形区域地图瓦片写入器。
 */
public final class AccurateTerrainPngWriter {
    private static final MapTileLayer TARGET_LAYER = MapTileLayer.TERRAIN_ACCURATE;

    private AccurateTerrainPngWriter() {}

    public static void writeTerrainTiles(ServerLevel level, AccurateTerrainRegion region) {
        writeTerrainTiles(level, region, TerrainPngWriteProgress.NONE);
    }

    public static void writeTerrainTiles(ServerLevel level,
                                         AccurateTerrainRegion region,
                                         TerrainPngWriteProgress progress) {
        if (level == null || region == null || region.isDisposed()) {
            return;
        }
        TerrainPngWriteProgress progressSink = progress == null ? TerrainPngWriteProgress.NONE : progress;
        AccurateRegionBounds bounds = region.bounds();
        List<MapTileCoord> missingTiles = TerrainTileWritePlan.missingTiles(
                level,
                TARGET_LAYER,
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ());
        long totalTiles = missingTiles.size();
        long completedTiles = 0L;
        for (MapTileCoord coord : missingTiles) {
            writeTerrainTile(level, region, coord);
            progressSink.onTileCompleted(++completedTiles, totalTiles);
        }
    }

    public static void writeTerrainTile(ServerLevel level,
                                        AccurateTerrainRegion region,
                                        MapTileCoord coord) {
        if (level == null || region == null || region.isDisposed() || coord == null) return;
        synchronized (tileLock(level, coord)) {
            ServerMapTileStorage.writePng(level, TARGET_LAYER, coord,
                    renderTile(level, region, coord));
        }
    }

    private static BufferedImage renderTile(ServerLevel level,
                                            AccurateTerrainRegion region,
                                            MapTileCoord coord) {
        BufferedImage image = loadExistingTile(level, coord);
        int shadeDistance = Math.max(region.step(), MapTileScheme.blocksPerPixel(coord.zoom()));
        for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                if (!region.contains(worldX, worldZ)) {
                    continue;
                }
                int height = region.height(worldX, worldZ);
                int neighborX = worldX - shadeDistance;
                double shade = region.contains(neighborX, worldZ)
                        ? HeightShader.simpleShade(height, region.height(neighborX, worldZ), shadeDistance)
                        : 1.0;
                boolean columnWater = region.isColumnWater(worldX, worldZ);
                boolean nearWater = region.isNearWater(worldX, worldZ, shadeDistance);
                int color = TerrainTilePalette.colorFor(
                        region.biome(worldX, worldZ),
                        height,
                        region.seaLevel(),
                        region.oceanFloor(worldX, worldZ),
                        columnWater,
                        nearWater,
                        shade);
                image.setRGB(pixelX, pixelZ, color);
            }
        }
        return image;
    }

    private static Object tileLock(ServerLevel level, MapTileCoord coord) {
        return StripedLocks.lockForPath(ServerMapTileStorage.path(level, TARGET_LAYER, coord));
    }

    private static BufferedImage loadExistingTile(ServerLevel level, MapTileCoord coord) {
        Path path = ServerMapTileStorage.path(level, TARGET_LAYER, coord);
        if (Files.exists(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) {
                    return image;
                }
            } catch (IOException failure) {
                deleteCorruptTile(path);
            }
        }
        return new BufferedImage(
                MapTileScheme.TILE_SIZE_PX,
                MapTileScheme.TILE_SIZE_PX,
                BufferedImage.TYPE_INT_ARGB);
    }

    private static void deleteCorruptTile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }
}
