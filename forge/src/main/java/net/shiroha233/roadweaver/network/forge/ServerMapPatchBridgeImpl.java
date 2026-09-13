package net.shiroha233.roadweaver.network.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;

/**
 * Forge 服务端地图 patch 推送桥接实现。
 */
public final class ServerMapPatchBridgeImpl {
    private ServerMapPatchBridgeImpl() {}

    public static void broadcast(ServerLevel level, ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (level == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        var server = level.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.serverLevel() == level) {
                MapNetworkForge.broadcastPatch(player, dimensionId, patch);
            }
        }
    }
}
