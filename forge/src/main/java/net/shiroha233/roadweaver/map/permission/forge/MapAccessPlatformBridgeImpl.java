package net.shiroha233.roadweaver.map.permission.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;
import net.shiroha233.roadweaver.network.forge.MapNetworkForge;

public final class MapAccessPlatformBridgeImpl {
    private MapAccessPlatformBridgeImpl() {
    }

    public static MapAccessPolicy getPolicy(MinecraftServer server) {
        return ForgeMapAccessSavedData.get(server).getPolicy();
    }

    public static void setPolicy(MinecraftServer server, MapAccessPolicy policy) {
        ForgeMapAccessSavedData.get(server).setPolicy(policy);
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player != null) {
            MapNetworkForge.syncMapAccess(player);
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MapNetworkForge.syncMapAccess(player);
        }
    }
}
