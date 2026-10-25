package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 地形采样会话注册表。
 */
public final class TerrainSamplingSessions {
    private static final Object LOCK = new Object();
    private static final Map<Object, TerrainSamplingSession> SESSIONS = new IdentityHashMap<>();

    private TerrainSamplingSessions() {}

    public static TerrainSamplingSession forLevel(ServerLevel level) {
        return forKey(Objects.requireNonNull(level, "level"), configuredModeFromConfig());
    }

    public static void clear(ServerLevel level) {
        if (level == null) {
            return;
        }
        clearKey(level);
    }

    public static void clearAll() {
        synchronized (LOCK) {
            SESSIONS.clear();
        }
    }

    static TerrainSamplingSession forKey(Object key, TerrainSamplingMode configuredMode) {
        Objects.requireNonNull(key, "key");
        TerrainSamplingMode safeMode = Objects.requireNonNullElse(configuredMode, TerrainSamplingMode.LEGACY_DIRECT);
        synchronized (LOCK) {
            return SESSIONS.computeIfAbsent(key, ignored -> new TerrainSamplingSession(safeMode));
        }
    }

    static void clearKey(Object key) {
        if (key == null) {
            return;
        }
        synchronized (LOCK) {
            SESSIONS.remove(key);
        }
    }

    static int sizeForTests() {
        synchronized (LOCK) {
            return SESSIONS.size();
        }
    }

    private static TerrainSamplingMode configuredModeFromConfig() {
        try {
            TerrainSamplingMode configured = ConfigService.get().planning().terrainSamplingMode();
            return configured == null ? TerrainSamplingMode.LEGACY_DIRECT : configured;
        } catch (Throwable ignored) {
            return TerrainSamplingMode.LEGACY_DIRECT;
        }
    }
}
