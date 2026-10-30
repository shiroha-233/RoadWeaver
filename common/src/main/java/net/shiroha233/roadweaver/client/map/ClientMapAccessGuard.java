package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;

public final class ClientMapAccessGuard {
    private static volatile boolean known;
    private static volatile boolean allowed = true;

    private ClientMapAccessGuard() {
    }

    public static boolean isAllowedOrUnknown() {
        return !known || allowed;
    }

    public static boolean canOpen(Minecraft client) {
        if (isAllowedOrUnknown()) {
            return true;
        }
        notifyDenied(client);
        return false;
    }

    public static void applyServerState(Minecraft client, boolean nextAllowed) {
        known = true;
        allowed = nextAllowed;
        if (nextAllowed) {
            return;
        }
        MapSnapshotCache.clearNow();
        if (client != null && client.screen instanceof RoadMapScreen) {
            client.setScreen(null);
            notifyDenied(client);
        }
    }

    public static void reset() {
        known = false;
        allowed = true;
    }

    public static void notifyDenied(Minecraft client) {
        if (client != null && client.player != null) {
            client.player.displayClientMessage(Component.translatable("gui.roadweaver.map.open.denied"), true);
        }
    }
}
