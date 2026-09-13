/* 文件职责：在 Forge 服务端向当前维度客户端同步自动规划采样范围。 */
package net.shiroha233.roadweaver.network.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.List;

/**
 * Forge 自动规划采样范围同步实现。
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
                    MapNetworkForge.broadcastAutomaticPlanningSampling(
                            player,
                            level.dimension().location(),
                            snapshot);
                }
            }
        });
    }
}
