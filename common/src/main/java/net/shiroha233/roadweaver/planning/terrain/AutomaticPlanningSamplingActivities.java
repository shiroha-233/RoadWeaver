package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.server.level.ServerLevel;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自动规划采样活动的临时注册表。
 *
 * <p>活动句柄必须在规划完成或提交失败时关闭，避免向地图暴露过期范围。</p>
 */
public final class AutomaticPlanningSamplingActivities {
    private static final Object LOCK = new Object();
    private static final AtomicLong NEXT_ACTIVITY_ID = new AtomicLong();
    private static final Map<Object, LevelActivities> ACTIVE_BY_LEVEL =
            new IdentityHashMap<>();

    private AutomaticPlanningSamplingActivities() {}

    public static Activity begin(ServerLevel level, AutomaticPlanningSamplingBounds bounds) {
        return beginForKey(Objects.requireNonNull(level, "level"), bounds);
    }

    public static List<AutomaticPlanningSamplingBounds> snapshot(ServerLevel level) {
        return snapshotForKey(level);
    }

    public static void clear(ServerLevel level) {
        clearForKey(level);
    }

    public static void clearAll() {
        synchronized (LOCK) {
            ACTIVE_BY_LEVEL.clear();
        }
    }

    static Activity beginForKey(Object levelKey, AutomaticPlanningSamplingBounds bounds) {
        Objects.requireNonNull(levelKey, "levelKey");
        Objects.requireNonNull(bounds, "bounds");
        long activityId = NEXT_ACTIVITY_ID.incrementAndGet();
        synchronized (LOCK) {
            ACTIVE_BY_LEVEL.computeIfAbsent(levelKey, ignored -> new LevelActivities())
                    .add(activityId, bounds);
        }
        return new Activity(levelKey, activityId);
    }

    static List<AutomaticPlanningSamplingBounds> snapshotForKey(Object levelKey) {
        if (levelKey == null) {
            return List.of();
        }
        synchronized (LOCK) {
            LevelActivities activities = ACTIVE_BY_LEVEL.get(levelKey);
            if (activities == null) {
                return List.of();
            }
            return activities.snapshot();
        }
    }

    static void clearForKey(Object levelKey) {
        if (levelKey == null) {
            return;
        }
        synchronized (LOCK) {
            ACTIVE_BY_LEVEL.remove(levelKey);
        }
    }

    private static void finish(Object levelKey, long activityId) {
        synchronized (LOCK) {
            LevelActivities activities = ACTIVE_BY_LEVEL.get(levelKey);
            if (activities == null) {
                return;
            }
            if (activities.remove(activityId)) {
                ACTIVE_BY_LEVEL.remove(levelKey);
            }
        }
    }

    private static final class LevelActivities {
        private final LinkedHashMap<Long, AutomaticPlanningSamplingBounds> byId = new LinkedHashMap<>();
        private List<AutomaticPlanningSamplingBounds> snapshot = List.of();

        private void add(long activityId, AutomaticPlanningSamplingBounds bounds) {
            byId.put(activityId, bounds);
            snapshot = List.copyOf(byId.values());
        }

        private boolean remove(long activityId) {
            if (byId.remove(activityId) == null) {
                return false;
            }
            if (byId.isEmpty()) {
                snapshot = List.of();
                return true;
            }
            snapshot = List.copyOf(byId.values());
            return false;
        }

        private List<AutomaticPlanningSamplingBounds> snapshot() {
            return snapshot;
        }
    }

    public static final class Activity implements AutoCloseable {
        private final Object levelKey;
        private final long activityId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Activity(Object levelKey, long activityId) {
            this.levelKey = levelKey;
            this.activityId = activityId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                finish(levelKey, activityId);
            }
        }
    }
}
