package net.shiroha233.roadweaver.persistence;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.PlanningUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/*
    连接列表的原子读改写。
 */
public final class ConnectionListUpdater {
    private static final Map<ServerLevel, Object> LOCKS = Collections.synchronizedMap(new IdentityHashMap<>());

    private ConnectionListUpdater() {}
    public static <T> T update(ServerLevel level, Function<List<StructureConnection>, Optional<T>> mutator) {
        if (level == null || mutator == null) return null;
        WorldDataProvider provider = WorldDataProvider.getInstance();

        Object lock;
        synchronized (LOCKS) {
            lock = LOCKS.computeIfAbsent(level, ignored -> new Object());
        }
        synchronized (lock) {
            List<StructureConnection> snapshot = provider.getStructureConnections(level);
            if (snapshot == null) {
                snapshot = new ArrayList<>();
            }
            Optional<T> result = mutator.apply(snapshot);
            if (result == null || result.isEmpty()) {
                return null;
            }
            provider.setStructureConnections(level, snapshot);
            return result.get();
        }
    }


    public static StructureConnection transition(ServerLevel level, StructureConnection connection,
                                                 ConnectionStatus target, ConnectionStatus... allowedFrom) {
        if (level == null || connection == null || target == null) return null;
        return update(level, connections -> transitionIn(connections, connection, target, allowedFrom));
    }

    static Optional<StructureConnection> transitionIn(List<StructureConnection> connections,
                                                       StructureConnection connection,
                                                       ConnectionStatus target,
                                                       ConnectionStatus... allowedFrom) {
        if (connections == null || connection == null || target == null || allowedFrom == null) {
            return Optional.empty();
        }
        for (int i = 0; i < connections.size(); i++) {
            StructureConnection current = connections.get(i);
            if (current == null || !PlanningUtils.sameEdge(current, connection)) continue;
            for (ConnectionStatus allowed : allowedFrom) {
                if (current.status() == allowed) {
                    if (current.status() == target) return Optional.of(current);
                    StructureConnection updated = new StructureConnection(current.from(), current.to(), target);
                    connections.set(i, updated);
                    return Optional.of(updated);
                }
            }
        }
        return Optional.empty();
    }

    public static void clear(ServerLevel level) {
        if (level == null) return;
        synchronized (LOCKS) {
            LOCKS.remove(level);
        }
    }
}
