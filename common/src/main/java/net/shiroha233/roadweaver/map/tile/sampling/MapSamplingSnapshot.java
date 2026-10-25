package net.shiroha233.roadweaver.map.tile.sampling;

import java.util.Objects;

/**
 * 地图采样任务供界面轮询的状态。
 */
public record MapSamplingSnapshot(Stage stage, MapSamplingBounds bounds, int percent) {
    public MapSamplingSnapshot {
        stage = Objects.requireNonNull(stage, "stage");
        percent = Math.max(0, Math.min(100, percent));
        if (stage != Stage.IDLE && bounds == null) {
            throw new IllegalArgumentException("active or terminal sampling state requires bounds");
        }
    }

    public static MapSamplingSnapshot idle() {
        return new MapSamplingSnapshot(Stage.IDLE, null, 0);
    }

    public boolean active() {
        return stage == Stage.SAMPLING_TERRAIN
                || stage == Stage.WRITING_PNG
                || stage == Stage.QUERYING_STRUCTURES;
    }

    public enum Stage {
        IDLE,
        SAMPLING_TERRAIN,
        WRITING_PNG,
        QUERYING_STRUCTURES,
        COMPLETED,
        FAILED
    }
}
