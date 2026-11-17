package net.shiroha233.roadweaver.client.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.shiroha233.roadweaver.client.map.screen.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.cache.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.cache.MapDataStorage;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.tile.ClientMapTileTextureCache;
import net.shiroha233.roadweaver.network.fabric.MapNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

/**
 * Fabric 客户端初始化
 */
public class ClientInit implements ClientModInitializer {
    public static KeyMapping OPEN_MAP;
    public static KeyMapping DEBUG_RAW_PATHS;

    @Override
    public void onInitializeClient() {
        MapNetwork.registerClientReceivers();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            RawPathDebugOverlay.reset();
            ClientMapAccessGuard.reset();
            MapSnapshotCache.setCurrentWorldId(MapDataStorage.getWorldId());
            MapSnapshotCache.clearNow();
            ClientMapTileTextureCache.clear(client);
            ClientMapNotes.onWorldJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RawPathDebugOverlay.reset();
            ClientMapAccessGuard.reset();
            MapSnapshotCache.clearNow();
            MapSnapshotCache.setCurrentWorldId(null);
            ClientMapTileTextureCache.clear(client);
            ClientMapNotes.onWorldLeave();
        });

        KeyMapping.Category keyCategory = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("roadweaver", "main"));
        OPEN_MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.roadweaver.open_map",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_H,
                keyCategory
        ));
        DEBUG_RAW_PATHS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.roadweaver.debug_raw_paths", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9,
                keyCategory));
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> RawPathDebugOverlay.emitGizmos());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (client.gui.screen() == null) {
                while (DEBUG_RAW_PATHS.consumeClick()) RawPathDebugOverlay.toggle(client);
            }
            RawPathDebugOverlay.tick(client);
            if (client.gui.screen() instanceof RoadMapScreen) return;
            while (OPEN_MAP.consumeClick()) {
                if (!ClientMapAccessGuard.canOpen(client)) {
                    continue;
                }
                client.gui.setScreen(new RoadMapScreen());
            }
        });
    }
}
