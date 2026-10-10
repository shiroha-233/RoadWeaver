/* 文件职责：将粗粒度 terrain 采样结果写入 coarse terrain 瓦片目录。 */
package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
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
 * 将区域粗采样结果输出为 coarse terrain PNG。
 */
public final class CoarseTerrainPngWriter {
    private static final MapTileLayer TARGET_LAYER = MapTileLayer.TERRAIN_COARSE;

    private CoarseTerrainPngWriter() {}

    public static void writeTerrainTiles(ServerLevel level, CoarseTerrainRegion region) {
        writeTerrainTiles(level, region, TerrainPngWriteProgress.NONE);
    }

    public static void writeTerrainTiles(ServerLevel level,
                                         CoarseTerrainRegion region,
                                         TerrainPngWriteProgress progress) {
        if (level == null || region == null) return;
        TerrainPngWriteProgress progressSink = progress == null ? TerrainPngWriteProgress.NONE : progress;
        List<MapTileCoord> missingTiles = TerrainTileWritePlan.missingTiles(
                level,
                TARGET_LAYER,
                region.bounds().minX(),
                region.bounds().minZ(),
                region.bounds().maxX(),
                region.bounds().maxZ());
        long totalTiles = missingTiles.size();
        long completedTiles = 0L;
        for (MapTileCoord coord : missingTiles) {
            synchronized (tileLock(level, coord)) {
                ServerMapTileStorage.writePng(level, TARGET_LAYER, coord, renderTile(level, region, coord));
            }
            progressSink.onTileCompleted(++completedTiles, totalTiles);
        }
    }

    private static Object tileLock(ServerLevel level, MapTileCoord coord) {
        return StripedLocks.lockForPath(ServerMapTileStorage.path(level, TARGET_LAYER, coord));
    }

    private static BufferedImage renderTile(ServerLevel level, CoarseTerrainRegion region, MapTileCoord coord) {
        BufferedImage image = loadExistingTile(level, coord);
        for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                if (!region.contains(worldX, worldZ)) {
                    continue;
                }
                int index = region.bounds().indexOfNearest(worldX, worldZ);
                image.setRGB(pixelX, pixelZ, region.terrainArgbAtIndex(index));
            }
        }
        return image;
    }

    private static BufferedImage loadExistingTile(ServerLevel level, MapTileCoord coord) {
        Path path = ServerMapTileStorage.path(level, TARGET_LAYER, coord);
        if (Files.exists(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) return image;
            } catch (IOException e) {
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
