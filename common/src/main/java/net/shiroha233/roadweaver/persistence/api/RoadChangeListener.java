package net.shiroha233.roadweaver.persistence.api;

import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/**
 * 道路持久化变更监听器，供上层缓存订阅写入事件。
 */
public interface RoadChangeListener {

    /**
     * 若干区块内的道路数据发生变更。
     *
     * @param level     发生变更的维度
     * @param chunkKeys 受影响的区块坐标（{@code ChunkPos#toLong()} 形式）
     */
    void onChunksChanged(ServerLevel level, Collection<Long> chunkKeys);

    /**
     * 指定维度的全部道路数据被整体失效。
     *
     * @param level 发生变更的维度
     */
    void onPlansInvalidated(ServerLevel level);

    /**
     * 指定维度的会话已关闭。
     *
     * @param level 被关闭的维度
     */
    void onLevelCleared(ServerLevel level);

    /**
     * 全部维度的道路数据被清空。
     */
    void onAllCleared();
}
