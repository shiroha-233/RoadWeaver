package net.shiroha233.roadweaver.planning.terrain;

import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.Objects;

/**
 * 地形采样会话的只读快照。
 */
public record TerrainSamplingSessionSnapshot(TerrainSamplingMode configuredMode,
                                             TerrainSamplingMode effectiveMode,
                                             String backend,
                                             String device,
                                             String fallbackReason) {

    public TerrainSamplingSessionSnapshot {
        configuredMode = Objects.requireNonNullElse(configuredMode, TerrainSamplingMode.LEGACY_DIRECT);
        effectiveMode = Objects.requireNonNullElse(effectiveMode, configuredMode);
        backend = normalizeText(backend);
        device = normalizeText(device);
        fallbackReason = normalizeText(fallbackReason);
    }

    public boolean downgraded() {
        return configuredMode != effectiveMode;
    }

    public boolean hasFallbackReason() {
        return !fallbackReason.isBlank();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
