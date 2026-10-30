/* 文件职责：在 NeoForge 服务端向当前维度客户端同步自动规划采样范围。 */
package net.shiroha233.roadweaver.network.bridge.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.planning.pipeline.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.pipeline.AutomaticPlanningSamplingBounds;

import java.util.List;
import net.shiroha233.roadweaver.network.neoforge.MapNetwork;

/**
 * NeoForge 自动规划采样范围同步实现。
 */
public final class ServerMapPlanningActivityBridgeImpl {
    private ServerMapPlanningActivityBridgeImpl() {}

    public static void broadcast(ServerLevel level) {
        if (level == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (level.getServer() != server) {
                return;
            }
            List<AutomaticPlanningSamplingBounds> snapshot = AutomaticPlanningSamplingActivities.snapshot(level);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null && player.serverLevel() == level) {
                    MapNetwork.broadcastAutomaticPlanningSampling(
                            player,
                            level.dimension().location(),
                            snapshot);
                }
            }
        });
    }
}
