package net.countered.settlementroads.neoforge.events;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.client.RoadWeaverClient;
import net.countered.settlementroads.events.CommonEventHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * NeoForge事件处理器 - 服务器端事件
 */
@EventBusSubscriber(modid = RoadWeaver.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public class NeoForgeEventHandler {
    
    public static void register() {
        // NeoForge使用注解自动注册事件
        RoadWeaver.getLogger().info("NeoForge server event handlers registered via annotations");
    }
    
    public static void registerClient() {
        // 客户端事件也使用注解注册
        RoadWeaver.getLogger().info("NeoForge client event handlers registered via annotations");
    }
    
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CommonEventHandler.onWorldLoad(level);
        }
    }
    
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CommonEventHandler.onWorldUnload(level);
        }
    }
    
    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CommonEventHandler.onWorldTick(level);
        }
    }
    
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CommonEventHandler.onServerStopping();
    }
    
    /**
     * 客户端事件处理器 - 独立的内部类，只在客户端加载
     */
    @EventBusSubscriber(value = Dist.CLIENT, modid = RoadWeaver.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {
        
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            RoadWeaverClient.onClientTick(Minecraft.getInstance());
        }
    }
}
