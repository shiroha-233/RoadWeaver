package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * 服务端基础地图瓦片 PNG 存储。
 */
public final class ServerMapTileStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private ServerMapTileStorage() {}

    public static Path path(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return ServerMapTilePathResolver.tilePath(level, layer, coord);
    }

    public static Path path(Path dimensionRoot, MapTileLayer layer, MapTileCoord coord) {
        return ServerMapTilePathResolver.tilePath(dimensionRoot, layer, coord);
    }

    public static boolean exists(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return Files.exists(path(level, layer, coord));
    }

    public static boolean exists(Path dimensionRoot, MapTileLayer layer, MapTileCoord coord) {
        return Files.exists(path(dimensionRoot, layer, coord));
    }

    public static boolean hasCoverage(ServerLevel level,
                                      MapTileLayer layer,
                                      MapTileCoord coord,
                                      int minBlockX,
                                      int minBlockZ,
                                      int maxBlockX,
                                      int maxBlockZ) {
        return hasCoverage(ServerMapTilePathResolver.dimensionRoot(level), layer, coord,
                minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static boolean hasCoverage(Path dimensionRoot,
                                      MapTileLayer layer,
                                      MapTileCoord coord,
                                      int minBlockX,
                                      int minBlockZ,
                                      int maxBlockX,
                                      int maxBlockZ) {
        Path path = path(dimensionRoot, layer, coord);
        if (!Files.exists(path)) return false;
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null
                    || image.getWidth() != MapTileScheme.TILE_SIZE_PX
                    || image.getHeight() != MapTileScheme.TILE_SIZE_PX) {
                return false;
            }
            for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
                int blockZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
                if (blockZ < minBlockZ || blockZ > maxBlockZ) continue;
                for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                    int blockX = MapTileScheme.sampleBlockX(coord, pixelX);
                    if (blockX < minBlockX || blockX > maxBlockX) continue;
                    if ((image.getRGB(pixelX, pixelZ) >>> 24) == 0) return false;
                }
            }
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    public static Path writePng(ServerLevel level, MapTileLayer layer, MapTileCoord coord, BufferedImage image) {
        Path path = path(level, layer, coord);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ByteArrayOutputStream out = bytes) {
                ImageIO.write(image, "PNG", out);
            }
            FileStorageIO.writeBytesAtomic(path, bytes.toByteArray());
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("failed to write tile png: " + path, e);
        }
    }

    public static TerrainLayerMigrationResult migrateLegacyTerrainTiles(ServerLevel level) {
        return TerrainTileLayerMigration.migrate(ServerMapTilePathResolver.dimensionRoot(level), LOGGER);
    }

    public static TerrainLayerMigrationResult migrateLegacyTerrainTiles(Path dimensionRoot) {
        return TerrainTileLayerMigration.migrate(dimensionRoot, LOGGER);
    }

    public static void clearAll(ServerLevel level) {
        Path root = ServerMapTilePathResolver.dimensionRoot(level);
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("删除瓦片缓存失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("清理瓦片目录失败: {}", root, e);
        }
    }

    public record TerrainLayerMigrationResult(boolean completed,
                                              int copiedTiles,
                                              int skippedExistingTiles) {}
}
