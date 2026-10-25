package net.shiroha233.roadweaver.planning.terrain;

import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 世界级地形采样会话。
 */
public final class TerrainSamplingSession {
    private final TerrainSamplingMode configuredMode;
    private final AtomicReference<TerrainSamplingSessionSnapshot> snapshot;

    public TerrainSamplingSession(TerrainSamplingMode configuredMode) {
        this.configuredMode = Objects.requireNonNullElse(configuredMode, TerrainSamplingMode.LEGACY_DIRECT);
        this.snapshot = new AtomicReference<>(new TerrainSamplingSessionSnapshot(
                this.configuredMode,
                this.configuredMode,
                "",
                "",
                ""));
    }

    public TerrainSamplingMode configuredMode() {
        return snapshot.get().configuredMode();
    }

    public TerrainSamplingMode effectiveMode() {
        return snapshot.get().effectiveMode();
    }

    public String backend() {
        return snapshot.get().backend();
    }

    public String device() {
        return snapshot.get().device();
    }

    public String fallbackReason() {
        return snapshot.get().fallbackReason();
    }

    public TerrainSamplingSessionSnapshot snapshot() {
        return snapshot.get();
    }

    public void recordBackend(String backend, String device) {
        while (true) {
            TerrainSamplingSessionSnapshot current = snapshot.get();
            TerrainSamplingSessionSnapshot updated = new TerrainSamplingSessionSnapshot(
                    current.configuredMode(),
                    current.effectiveMode(),
                    preferNewText(backend, current.backend()),
                    preferNewText(device, current.device()),
                    current.fallbackReason());
            if (updated.equals(current)) {
                return;
            }
            if (snapshot.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    public boolean downgrade(String reason, String backend, String device) {
        if (configuredMode != TerrainSamplingMode.FULL_REGION) {
            return false;
        }
        while (true) {
            TerrainSamplingSessionSnapshot current = snapshot.get();
            if (current.effectiveMode() == TerrainSamplingMode.COARSE_CORRIDOR) {
                return false;
            }
            TerrainSamplingSessionSnapshot downgraded = new TerrainSamplingSessionSnapshot(
                    configuredMode,
                    TerrainSamplingMode.COARSE_CORRIDOR,
                    backend,
                    device,
                    reason);
            if (snapshot.compareAndSet(current, downgraded)) {
                return true;
            }
        }
    }

    private static String preferNewText(String preferred, String existing) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return existing == null ? "" : existing;
    }
}
