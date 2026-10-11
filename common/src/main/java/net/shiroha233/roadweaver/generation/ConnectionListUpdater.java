/* 文件职责：把「读连接列表 -> 就地修改 -> 整体写回」收敛为按维度原子的操作。 */
package net.shiroha233.roadweaver.generation;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 连接列表的原子读改写。
 *
 * <p>连接状态由普通生成、闲时生成、初始生成收尾、区域规划收尾、公开 API 五条路径
 * 并发修改，而存储层只对单次调用加锁。若不加此层串行化，后写者会用更旧的快照
 * 覆盖先写者的结果。修改连接列表的所有位置都必须经由此类，否则该保证不成立。
 */
public final class ConnectionListUpdater {
    private static final Map<ServerLevel, Object> LOCKS = Collections.synchronizedMap(new IdentityHashMap<>());

    private ConnectionListUpdater() {}

    /**
     * @param mutator 就地修改传入的可变列表；返回 {@link Optional#empty()} 表示无改动
     *                （此时不触发写回），有值时该值即变更结果且列表会被写回
     * @return 变更结果，无改动时为 {@code null}
     */
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

    /** 仅在当前状态仍符合预期时更新，避免迟到的工作任务覆写已完成或已删除的连接。 */
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
                    // 重启后持久化的 GENERATING 连接需要重新入队；同状态的领取也算成功。
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
