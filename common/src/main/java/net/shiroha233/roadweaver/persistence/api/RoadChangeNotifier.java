package net.shiroha233.roadweaver.persistence.api;

import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 道路持久化变更的广播器，解耦存储层与缓存层。
 */
public final class RoadChangeNotifier {

    private static final CopyOnWriteArrayList<RoadChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

    private RoadChangeNotifier() {
    }

    public static void addListener(RoadChangeListener listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void chunksChanged(ServerLevel level, Collection<Long> chunkKeys) {
        for (RoadChangeListener listener : LISTENERS) {
            listener.onChunksChanged(level, chunkKeys);
        }
    }

    public static void plansInvalidated(ServerLevel level) {
        for (RoadChangeListener listener : LISTENERS) {
            listener.onPlansInvalidated(level);
        }
    }

    public static void levelCleared(ServerLevel level) {
        for (RoadChangeListener listener : LISTENERS) {
            listener.onLevelCleared(level);
        }
    }

    public static void allCleared() {
        for (RoadChangeListener listener : LISTENERS) {
            listener.onAllCleared();
        }
    }
}
