package net.shiroha233.roadweaver.network.platform.neoforge;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.core.model.MapSnapshotPatch;
import net.shiroha233.roadweaver.network.neoforge.MapNetwork;

/**
 * NeoForge 服务端地图 patch 推送桥接实现。
 */
public final class ServerMapPatchBridgeImpl {
    private ServerMapPatchBridgeImpl() {}

    public static void broadcast(ServerLevel level, Identifier dimensionId, MapSnapshotPatch patch) {
        if (level == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        var server = level.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.level() == level) {
                MapNetwork.broadcastPatch(player, dimensionId, patch);
            }
        }
    }
}
