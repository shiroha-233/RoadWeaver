package net.countered.settlementroads.neoforge;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.client.RoadWeaverClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * NeoForge客户端入口点
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = RoadWeaver.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class RoadWeaverNeoForgeClient {
    
    /**
     * 客户端设置事件 - 在 MOD 总线上触发
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 调用通用客户端初始化
        RoadWeaver.initClient();
        
        // 注册按键绑定（需要在主线程执行）
        event.enqueueWork(() -> {
            RoadWeaverClient.registerKeyBindings();
            RoadWeaver.getLogger().info("NeoForge client initialized - key bindings registered");
        });
    }
}
