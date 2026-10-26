package net.shiroha233.roadweaver.map.permission.fabric;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;
import net.shiroha233.roadweaver.network.fabric.MapNetworkFabric;
import net.shiroha233.roadweaver.persistence.fabric.WorldDataAttachment;

public final class MapAccessPlatformBridgeImpl {
    private MapAccessPlatformBridgeImpl() {
    }

    public static MapAccessPolicy getPolicy(MinecraftServer server) {
        ServerLevel level = getStorageLevel(server);
        if (level == null) {
            return MapAccessPolicy.DEFAULT;
        }
        MapAccessPolicy policy = ((AttachmentTarget) level).getAttached(WorldDataAttachment.MAP_ACCESS_POLICY);
        return policy != null ? policy : MapAccessPolicy.DEFAULT;
    }

    public static void setPolicy(MinecraftServer server, MapAccessPolicy policy) {
        ServerLevel level = getStorageLevel(server);
        if (level == null) {
            return;
        }
        ((AttachmentTarget) level).setAttached(WorldDataAttachment.MAP_ACCESS_POLICY, policy != null ? policy : MapAccessPolicy.DEFAULT);
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player != null) {
            MapNetworkFabric.syncMapAccess(player);
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MapNetworkFabric.syncMapAccess(player);
        }
    }

    private static ServerLevel getStorageLevel(MinecraftServer server) {
        return server != null ? server.getLevel(Level.OVERWORLD) : null;
    }
}
