package net.shiroha233.roadweaver.client.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientKeyMappings {
    public static KeyMapping OPEN_MAP;
    public static KeyMapping DEBUG_RAW_PATHS;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_MAP = new KeyMapping("key.roadweaver.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.roadweaver");
        event.register(OPEN_MAP);
        DEBUG_RAW_PATHS = new KeyMapping("key.roadweaver.debug_raw_paths", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F9, "key.categories.roadweaver");
        event.register(DEBUG_RAW_PATHS);
    }

    @EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class NeoForgeBusHandlers {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (OPEN_MAP == null) return;
            if (mc.screen == null) {
                while (DEBUG_RAW_PATHS.consumeClick()) RawPathDebugOverlay.toggle(mc);
            }
            RawPathDebugOverlay.tick(mc);
            if (mc.screen instanceof RoadMapScreen) return;
            while (OPEN_MAP.consumeClick()) {
                if (!ClientMapAccessGuard.canOpen(mc)) {
                    continue;
                }
                mc.setScreen(new RoadMapScreen());
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
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                RawPathDebugOverlay.render(event.getPoseStack(), event.getCamera());
            }
        }
    }
}
