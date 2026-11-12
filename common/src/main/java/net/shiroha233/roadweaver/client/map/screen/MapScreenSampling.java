package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RequestRect;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingBounds;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;
import net.shiroha233.roadweaver.map.tile.sampling.MapTerrainSamplingService;

/**
 * 地图屏幕的地形采样编排：发起采样、跟踪阶段变化并维护临时提示。
 */
final class MapScreenSampling {

    private static final long NOTICE_DURATION_MS = 4_000L;

    private final MapTerrainSamplingService service = new MapTerrainSamplingService();

    private MapSamplingSnapshot.Stage observedStage = MapSamplingSnapshot.Stage.IDLE;

    private Component notice;

    private long noticeExpiresAtMs;

    /**
     * 采样阶段迁移结果，供屏幕决定需要联动刷新哪些视图。
     */
    enum Transition {
        NONE,
        COMPLETED,
        FAILED
    }

    MapTerrainSamplingService service() {
        return service;
    }

    MapSamplingSnapshot snapshot() {
        return service.snapshot();
    }

    boolean isAvailable(ServerLevel level) {
        return service.isAvailable(level);
    }

    MapTerrainSamplingService.StartResult start(ServerLevel level, RequestRect rect) {
        return service.start(level, new MapSamplingBounds(rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ()));
    }

    Transition poll(MapSamplingSnapshot current) {
        if (current == null || current.stage() == observedStage) {
            return Transition.NONE;
        }
        observedStage = current.stage();
        if (current.stage() == MapSamplingSnapshot.Stage.COMPLETED) {
            return Transition.COMPLETED;
        }
        if (current.stage() == MapSamplingSnapshot.Stage.FAILED) {
            return Transition.FAILED;
        }
        return Transition.NONE;
    }

    void notify(Minecraft minecraft, Component message) {
        if (message == null) {
            return;
        }
        notice = message;
        noticeExpiresAtMs = System.currentTimeMillis() + NOTICE_DURATION_MS;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    void startAndNotify(Minecraft minecraft, ServerLevel level, RequestRect rect) {
        notify(minecraft, messageFor(start(level, rect)));
    }

    void pollAndNotify(Minecraft minecraft, MapSamplingSnapshot current, Runnable onCompleted) {
        Transition transition = poll(current);
        if (transition == Transition.COMPLETED) {
            if (onCompleted != null) {
                onCompleted.run();
            }
            notify(minecraft, Component.translatable("gui.roadweaver.map.sample.completed"));
        } else if (transition == Transition.FAILED) {
            notify(minecraft, Component.translatable("gui.roadweaver.map.sample.failed"));
        }
    }

    private static Component messageFor(MapTerrainSamplingService.StartResult result) {
        return switch (result) {
            case STARTED -> Component.translatable("gui.roadweaver.map.sample.started");
            case ALREADY_RUNNING -> Component.translatable("gui.roadweaver.map.sample.running");
            case WORLD_UNAVAILABLE -> Component.translatable("gui.roadweaver.map.sample.singleplayer_only");
            case MODE_UNAVAILABLE -> Component.translatable("gui.roadweaver.map.sample.mode_unavailable");
            case RANGE_TOO_LARGE -> Component.translatable("gui.roadweaver.map.sample.range_too_large");
            case SUBMISSION_FAILED -> Component.translatable("gui.roadweaver.map.sample.failed");
        };
    }

    Component currentNotice() {
        if (notice == null) {
            return null;
        }
        if (System.currentTimeMillis() < noticeExpiresAtMs) {
            return notice;
        }
        notice = null;
        return null;
    }
}
