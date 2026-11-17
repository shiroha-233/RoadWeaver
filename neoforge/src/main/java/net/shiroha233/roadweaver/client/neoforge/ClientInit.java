package net.shiroha233.roadweaver.client.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.debug.RawPathDebugOverlay;
import net.shiroha233.roadweaver.client.map.screen.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.cache.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;

@EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT)
public class ClientInit {
    public static KeyMapping OPEN_MAP;
    public static KeyMapping DEBUG_RAW_PATHS;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "main"));
        event.registerCategory(category);
        OPEN_MAP = new KeyMapping("key.roadweaver.open_map",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_H, category);
        event.register(OPEN_MAP);
        DEBUG_RAW_PATHS = new KeyMapping("key.roadweaver.debug_raw_paths",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, category);
        event.register(DEBUG_RAW_PATHS);
    }

    @EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT)
    public static class NeoForgeBusHandlers {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (OPEN_MAP == null || DEBUG_RAW_PATHS == null) return;
            if (mc.gui.screen() == null) {
                while (DEBUG_RAW_PATHS.consumeClick()) RawPathDebugOverlay.toggle(mc);
            }
            RawPathDebugOverlay.tick(mc);
            if (mc.gui.screen() instanceof RoadMapScreen) return;
            while (OPEN_MAP.consumeClick()) {
                if (!ClientMapAccessGuard.canOpen(mc)) {
                    continue;
                }
                mc.gui.setScreen(new RoadMapScreen());
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            RawPathDebugOverlay.reset();
            ClientMapAccessGuard.reset();
            MapSnapshotCache.clearNow();
            ClientMapNotes.onWorldJoin();
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RawPathDebugOverlay.reset();
            ClientMapAccessGuard.reset();
            MapSnapshotCache.clearNow();
            ClientMapNotes.onWorldLeave();
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentParticles event) {
            try {
                RawPathDebugOverlay.emitGizmos();
            } catch (Throwable ignored) {}
        }
    }
}
