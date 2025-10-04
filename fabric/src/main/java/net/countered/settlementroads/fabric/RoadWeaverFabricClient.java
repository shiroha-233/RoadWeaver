package net.countered.settlementroads.fabric;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.client.RoadWeaverClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric客户端入口点
 */
public class RoadWeaverFabricClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // 调用通用客户端初始化
        RoadWeaver.initClient();
        
        // 注册Fabric特定的客户端功能
        RoadWeaverClient.registerKeyBindings();
    }
}
