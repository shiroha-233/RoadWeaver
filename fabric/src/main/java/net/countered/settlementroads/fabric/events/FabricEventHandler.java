package net.countered.settlementroads.fabric.events;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.client.RoadWeaverClient;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.events.CommonEventHandler;
import net.countered.settlementroads.features.RoadFeature;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.roadlogic.Road;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.platform.services.WorldDataStorage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric事件处理器
 */
public class FabricEventHandler {
    
    public static void register() {
        // 世界加载事件
        ServerWorldEvents.LOAD.register((server, serverWorld) -> {
            CommonEventHandler.onWorldLoad(serverWorld);
        });
        
        // 世界卸载事件
        ServerWorldEvents.UNLOAD.register((server, serverWorld) -> {
            CommonEventHandler.onWorldUnload(serverWorld);
        });
        
        // Tick事件
        ServerTickEvents.START_WORLD_TICK.register((serverWorld) -> {
            CommonEventHandler.onWorldTick(serverWorld);
        });
        
        // 服务器停止事件
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CommonEventHandler.onServerStopping();
        });
    }
    
    public static void registerClient() {
        // 客户端Tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RoadWeaverClient.onClientTick(client);
        });
    }
}
