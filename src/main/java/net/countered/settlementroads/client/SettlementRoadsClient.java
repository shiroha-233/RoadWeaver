package net.countered.settlementroads.client;

import net.countered.settlementroads.client.gui.RoadDebugScreen;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.persistence.attachments.WorldDataAttachment;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SettlementRoadsClient implements ClientModInitializer {

    private static KeyBinding debugMapKey;

    @Override
    public void onInitializeClient() {
        // 注册按键绑定 (默认 H 键)
        debugMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.roadweaver.debug_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.roadweaver"
        ));

        // 注册客户端 tick 事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (debugMapKey.wasPressed()) {
                handleDebugMapKey(client);
            }
        });
    }

    private void handleDebugMapKey(MinecraftClient client) {
        // 如果已经打开调试屏幕，关闭它
        if (client.currentScreen instanceof RoadDebugScreen) {
            client.setScreen(null);
            return;
        }

        // 获取服务器世界（仅单人游戏）
        ServerWorld world = client.getServer() == null ? null : client.getServer().getOverworld();
        if (world == null) {
            return;
        }

        // 获取数据
        Records.StructureLocationData structureData = world.getAttached(WorldDataAttachment.STRUCTURE_LOCATIONS);
        List<Records.StructureConnection> connections = world.getAttachedOrCreate(
                WorldDataAttachment.CONNECTED_STRUCTURES, 
                ArrayList::new
        );
        List<Records.RoadData> roads = world.getAttachedOrCreate(
                WorldDataAttachment.ROAD_DATA_LIST, 
                ArrayList::new
        );

        List<BlockPos> structures = structureData != null ? structureData.structureLocations() : new ArrayList<>();

        // 打开调试屏幕
        client.setScreen(new RoadDebugScreen(structures, connections, roads));
    }
}
