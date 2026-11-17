package net.shiroha233.roadweaver.map.permission;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.UUID;

public final class MapAccessService {
    private MapAccessService() {
    }

    public static MapAccessPolicy getPolicy(MinecraftServer server) {
        if (server == null) {
            return MapAccessPolicy.DEFAULT;
        }
        MapAccessPolicy policy = MapAccessPlatformBridge.getPolicy(server);
        return policy != null ? policy : MapAccessPolicy.DEFAULT;
    }

    public static boolean canOpenMap(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        MapAccessPolicy policy = getPolicy(player.level().getServer());
        if (policy.mode() == MapAccessMode.ALL_PLAYERS) {
            return true;
        }
        return policy.isExplicitlyAllowed(player.getUUID());
    }

    public static boolean setMode(MinecraftServer server, MapAccessMode mode) {
        MapAccessPolicy current = getPolicy(server);
        MapAccessPolicy updated = current.withMode(mode);
        if (updated.equals(current)) {
            return false;
        }
        MapAccessPlatformBridge.setPolicy(server, updated);
        MapAccessPlatformBridge.syncAll(server);
        return true;
    }

    public static boolean grant(MinecraftServer server, UUID playerId) {
        MapAccessPolicy current = getPolicy(server);
        MapAccessPolicy updated = current.withGranted(playerId);
        if (updated.equals(current)) {
            return false;
        }
        MapAccessPlatformBridge.setPolicy(server, updated);
        MapAccessPlatformBridge.syncAll(server);
        return true;
    }

    public static boolean revoke(MinecraftServer server, UUID playerId) {
        MapAccessPolicy current = getPolicy(server);
        MapAccessPolicy updated = current.withRevoked(playerId);
        if (updated.equals(current)) {
            return false;
        }
        MapAccessPlatformBridge.setPolicy(server, updated);
        MapAccessPlatformBridge.syncAll(server);
        return true;
    }
}
