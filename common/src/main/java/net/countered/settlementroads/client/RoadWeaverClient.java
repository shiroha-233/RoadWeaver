package net.countered.settlementroads.client;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.countered.settlementroads.client.gui.RoadDebugScreen;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.platform.PlatformHelper;
import net.countered.settlementroads.platform.services.WorldDataStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用客户端功能
 */
public class RoadWeaverClient {
    
    private static KeyBinding debugMapKey;
    
    /**
     * 注册按键绑定
     */
    public static void registerKeyBindings() {
        debugMapKey = new KeyBinding(
                "key.roadweaver.debug_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.roadweaver"
        );
        
        KeyMappingRegistry.register(debugMapKey);
    }
    
    /**
     * 客户端Tick处理
     */
    public static void onClientTick(MinecraftClient client) {
        if (debugMapKey == null) {
            return;
        }
        
        while (debugMapKey.wasPressed()) {
            System.out.println("[RoadWeaver] 调试地图按键被按下");
            handleDebugMapKey(client);
        }
    }
    
    /**
     * 处理调试地图按键
     */
    private static void handleDebugMapKey(MinecraftClient client) {
        try {
            // 如果已经打开调试屏幕，关闭它
            if (client.currentScreen instanceof RoadDebugScreen) {
                client.setScreen(null);
                return;
            }
            
            // 获取服务器世界（仅单人游戏）
            ServerWorld world = PlatformHelper.getServerInClient() == null ? null : 
                    PlatformHelper.getServerInClient().getOverworld();
            
            if (world == null) {
                System.out.println("[RoadWeaver] 无法获取服务器世界 - 可能不在单人游戏中");
                return;
            }
            
            // 获取数据
            Records.StructureLocationData structureData = WorldDataStorage.getStructureLocations(world);
            List<Records.StructureConnection> connections = WorldDataStorage.getStructureConnections(world);
            List<Records.RoadData> roads = WorldDataStorage.getRoadDataList(world);
            
            List<BlockPos> structures = structureData != null ? 
                    structureData.structureLocations() : new ArrayList<>();
            
            System.out.println("[RoadWeaver] 打开调试地图 - 结构数: " + structures.size() + 
                    ", 连接数: " + connections.size() + ", 道路数: " + roads.size());
            
            // 打开调试屏幕
            client.setScreen(new RoadDebugScreen(structures, connections, roads));
        } catch (Exception e) {
            System.err.println("[RoadWeaver] 打开调试地图失败:");
            e.printStackTrace();
        }
    }
}
