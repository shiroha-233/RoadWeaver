package net.shiroha233.roadweaver.client.forge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;

public final class ClientEvents {
    private ClientEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        MapSnapshotCache.clearNow();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        MapSnapshotCache.clearNow();
    }
}
