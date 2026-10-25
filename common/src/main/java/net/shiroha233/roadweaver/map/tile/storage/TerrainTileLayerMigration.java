package net.shiroha233.roadweaver.map.tile.storage;

import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * terrain 图层目录迁移。
 */
final class TerrainTileLayerMigration {
    private TerrainTileLayerMigration() {}

    static ServerMapTileStorage.TerrainLayerMigrationResult migrate(Path dimensionRoot, Logger logger) {
        Path marker = ServerMapTilePathResolver.terrainLayerMigrationMarker(dimensionRoot);
        if (Files.exists(marker)) {
            return new ServerMapTileStorage.TerrainLayerMigrationResult(true, 0, 0);
        }

        Path legacyRoot = ServerMapTilePathResolver.legacyTerrainRoot(dimensionRoot);
        if (!Files.exists(legacyRoot)) {
            return finishWithoutLegacyTiles(marker, logger);
        }

        CopyStats stats = copyLegacyTiles(legacyRoot,
                ServerMapTilePathResolver.layerRoot(dimensionRoot, MapTileLayer.TERRAIN_COARSE),
                logger);
        if (!stats.complete()) {
            return new ServerMapTileStorage.TerrainLayerMigrationResult(false, stats.copiedTiles(), stats.skippedExistingTiles());
        }
        if (!writeMarker(marker, logger)) {
            return new ServerMapTileStorage.TerrainLayerMigrationResult(false, stats.copiedTiles(), stats.skippedExistingTiles());
        }
        FileStorageIO.deleteTree(legacyRoot, logger, "清理旧 terrain_v2 目录失败");
        return new ServerMapTileStorage.TerrainLayerMigrationResult(true, stats.copiedTiles(), stats.skippedExistingTiles());
    }

    private static ServerMapTileStorage.TerrainLayerMigrationResult finishWithoutLegacyTiles(Path marker, Logger logger) {
        if (!writeMarker(marker, logger)) {
            return new ServerMapTileStorage.TerrainLayerMigrationResult(false, 0, 0);
        }
        return new ServerMapTileStorage.TerrainLayerMigrationResult(true, 0, 0);
    }

    private static CopyStats copyLegacyTiles(Path legacyRoot, Path coarseRoot, Logger logger) {
        int copiedTiles = 0;
        int skippedExistingTiles = 0;
        boolean complete = true;
        try (var stream = Files.walk(legacyRoot)) {
            for (Path source : (Iterable<Path>) stream::iterator) {
                Path relative = legacyRoot.relativize(source);
                if (relative.toString().isEmpty()) {
                    continue;
                }
                Path target = coarseRoot.resolve(relative);
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                        continue;
                    }
                    if (!Files.isRegularFile(source)) {
                        continue;
                    }
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    if (Files.exists(target)) {
                        skippedExistingTiles++;
                        continue;
                    }
                    Files.copy(source, target);
                    copiedTiles++;
                } catch (IOException failure) {
                    complete = false;
                    if (logger != null) {
                        logger.warn("迁移旧 terrain_v2 瓦片失败: {} -> {}", source, target, failure);
                    }
                }
            }
        } catch (IOException failure) {
            complete = false;
            if (logger != null) {
                logger.warn("遍历旧 terrain_v2 目录失败: {}", legacyRoot, failure);
            }
        }
        return new CopyStats(complete, copiedTiles, skippedExistingTiles);
    }

    private static boolean writeMarker(Path marker, Logger logger) {
        try {
            FileStorageIO.writeStringAtomic(marker, "version=1\n");
            return true;
        } catch (IOException failure) {
            if (logger != null) {
                logger.warn("写入 terrain 图层迁移标记失败: {}", marker, failure);
            }
            return false;
        }
    }

    private record CopyStats(boolean complete, int copiedTiles, int skippedExistingTiles) {}
}
