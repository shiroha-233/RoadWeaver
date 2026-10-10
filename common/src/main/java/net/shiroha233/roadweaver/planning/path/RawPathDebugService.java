package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.files.FileStoragePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** World-scoped, opt-in capture of actual pathfinding nodes, including paths already converted to roads. */
public final class RawPathDebugService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    public static final int VIEW_RADIUS = 128;
    public static final int MAX_RESPONSE_POINTS = 1_800;
    private static final Map<ServerLevel, DebugRawPathStore> STORES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private RawPathDebugService() {}

    public static boolean enabled() {
        return ConfigService.get().planning().debugRawPaths();
    }

    public static void captureSearch(ServerLevel level, StructureConnection connection,
                                     TerrainSamplingMode mode, List<BlockPos> raw) {
        if (!enabled() || mode == TerrainSamplingMode.LEGACY_DIRECT || raw == null || raw.size() < 2) return;
        try {
            store(level).recordSearch(PlannedPathKey.of(connection), mode, raw);
        } catch (IOException error) {
            LOGGER.warn("Could not save raw search path", error);
        }
    }

    public static void captureResolved(ServerLevel level, StructureConnection connection,
                                       TerrainSamplingMode mode, List<BlockPos> raw, List<BlockPos> resolved) {
        if (!enabled() || mode == TerrainSamplingMode.LEGACY_DIRECT || connection == null) return;
        try {
            store(level).recordResolved(PlannedPathKey.of(connection), mode, raw, resolved);
        } catch (IOException error) {
            LOGGER.warn("Could not save refined path nodes", error);
        }
    }

    public static List<DebugRawPath> nearby(ServerLevel level, int x, int z) {
        try {
            return store(level).query(x - VIEW_RADIUS, z - VIEW_RADIUS,
                    x + VIEW_RADIUS, z + VIEW_RADIUS, MAX_RESPONSE_POINTS);
        } catch (IOException error) {
            LOGGER.warn("Could not query nearby raw paths", error);
            return List.of();
        }
    }

    public static void clear(ServerLevel level) {
        STORES.remove(level);
    }

    public static void clearAll() {
        STORES.clear();
    }

    private static DebugRawPathStore store(ServerLevel level) {
        synchronized (STORES) {
            return STORES.computeIfAbsent(level, key ->
                    new DebugRawPathStore(FileStoragePathResolver.categoryRoot(key, "raw_path_debug")));
        }
    }
}
