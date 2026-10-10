/* 文件职责：在世界生成指纹变化时失效精确地形地图图层。 */
package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import net.shiroha233.roadweaver.persistence.WorldgenFingerprintService;
import net.shiroha233.roadweaver.util.StripedLocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 精确地图派生物的世界生成身份守卫。
 */
public final class AccurateTerrainMapFingerprintGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String MARKER = "accurate-worldgen-v1.txt";

    private AccurateTerrainMapFingerprintGuard() {}

    public static void ensure(ServerLevel level) {
        if (level == null) return;
        try {
            ensure(level, WorldgenFingerprintService.forLevel(level).namespace());
        } catch (RuntimeException failure) {
            LOGGER.warn("计算精确地形地图指纹失败 dimension={}", level.dimension().location(), failure);
        }
    }

    public static void ensure(ServerLevel level, String fingerprint) {
        Objects.requireNonNull(level, "level");
        String expected = Objects.requireNonNull(fingerprint, "fingerprint");
        Path dimensionRoot = ServerMapTilePathResolver.dimensionRoot(level);
        Path marker = dimensionRoot.resolve("migration").resolve(MARKER);
        synchronized (StripedLocks.lockForPath(marker)) {
            try {
                String current = Files.exists(marker)
                        ? Files.readString(marker, StandardCharsets.UTF_8).trim()
                        : "";
                if (expected.equals(current)) return;
                FileStorageIO.deleteTree(
                        ServerMapTilePathResolver.layerRoot(level, MapTileLayer.TERRAIN_ACCURATE),
                        LOGGER,
                        "清理过期精确地形地图失败");
                FileStorageIO.writeStringAtomic(marker, expected);
            } catch (IOException failure) {
                LOGGER.warn("校验精确地形地图指纹失败 dimension={}", level.dimension().location(), failure);
            }
        }
    }
}
