package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientMapAccessGuard {
    private static final List<Runnable> REVOKED_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean known;
    private static volatile boolean allowed = true;

    private ClientMapAccessGuard() {
    }

    public static void onRevoked(Runnable listener) {
        if (listener == null) {
            return;
        }
        REVOKED_LISTENERS.add(listener);
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

    public static void applyServerState(boolean nextAllowed) {
        known = true;
        allowed = nextAllowed;
        if (nextAllowed) {
            return;
        }
        for (Runnable listener : REVOKED_LISTENERS) {
            listener.run();
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.screen instanceof RevocableMapScreen screen) {
            client.setScreen(null);
            screen.onMapAccessRevoked();
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
