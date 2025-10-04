package net.countered.settlementroads.events;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.RoadFeature;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.roadlogic.Road;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.platform.services.WorldDataStorage;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 通用事件处理器
 * 包含跨平台的事件处理逻辑
 */
public class CommonEventHandler {
    
    private static final int THREAD_COUNT = 7;
    private static final Logger LOGGER = RoadWeaver.getLogger();
    private static ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * 世界加载事件处理
     */
    public static void onWorldLoad(ServerWorld world) {
        LOGGER.info("RoadWeaver: onWorldLoad called for world: {}", world.getRegistryKey().getValue());
        restartExecutorIfNeeded();
        
        if (!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
            LOGGER.info("RoadWeaver: Skipping non-overworld dimension");
            return;
        }
        
        Records.StructureLocationData structureLocationData = WorldDataStorage.getStructureLocations(world);
        
        // 恢复未完成的道路生成任务
        restoreUnfinishedRoads(world);
        
        // 同步执行初始结构定位（与原项目一致）
        if (structureLocationData.structureLocations().size() < ModConfig.initialLocatingCount) {
            LOGGER.info("RoadWeaver: 开始初始结构定位，当前: {}, 目标: {}", 
                    structureLocationData.structureLocations().size(), ModConfig.initialLocatingCount);
            for (int i = 0; i < ModConfig.initialLocatingCount; i++) {
                StructureConnector.cacheNewConnection(world, false);
                tryGenerateNewRoads(world, true, 5000);
            }
            LOGGER.info("RoadWeaver: 完成初始结构定位");
        } else {
            LOGGER.info("RoadWeaver: 已有 {} 个结构，跳过初始定位", 
                    structureLocationData.structureLocations().size());
        }
    }
    
    /**
     * 世界卸载事件处理
     */
    public static void onWorldUnload(ServerWorld world) {
        if (!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
            return;
        }
        
        String worldKey = world.getRegistryKey().getValue().toString();
        Future<?> task = runningTasks.remove(worldKey);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            LOGGER.debug("Aborted running road task for world: {}", world.getRegistryKey().getValue());
        }
    }
    
    /**
     * 世界Tick事件处理
     */
    public static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
            return;
        }
        
        tryGenerateNewRoads(world, true, 5000);
    }
    
    /**
     * 服务器停止事件处理
     */
    public static void onServerStopping() {
        RoadPathCalculator.heightCache.clear();
        runningTasks.values().forEach(future -> future.cancel(true));
        runningTasks.clear();
        executor.shutdownNow();
        LOGGER.debug("RoadWeaver: ExecutorService shut down.");
    }
    
    /**
     * 尝试生成新道路
     */
    private static void tryGenerateNewRoads(ServerWorld serverWorld, Boolean async, int steps) {
        // 清理已完成的任务
        runningTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
        
        // 检查是否达到并发上限
        if (runningTasks.size() >= ModConfig.maxConcurrentRoadGeneration) {
            return;
        }
        
        if (!StructureConnector.cachedStructureConnections.isEmpty()) {
            Records.StructureConnection structureConnection = StructureConnector.cachedStructureConnections.poll();
            ConfiguredFeature<?, ?> feature = serverWorld.getRegistryManager()
                    .get(RegistryKeys.CONFIGURED_FEATURE)
                    .get(RoadFeature.ROAD_FEATURE_KEY);
            
            if (feature != null && feature.config() instanceof RoadFeatureConfig roadConfig) {
                if (async) {
                    // 使用唯一的任务ID而不是世界ID，允许多个任务并发
                    String taskId = serverWorld.getRegistryKey().getValue().toString() + "_" + System.nanoTime();
                    Future<?> future = executor.submit(() -> {
                        try {
                            new Road(serverWorld, structureConnection, roadConfig).generateRoad(steps);
                        } catch (Exception e) {
                            LOGGER.error("Error generating road", e);
                        } finally {
                            runningTasks.remove(taskId);
                        }
                    });
                    runningTasks.put(taskId, future);
                } else {
                    new Road(serverWorld, structureConnection, roadConfig).generateRoad(steps);
                }
            }
        }
    }
    
    /**
     * 重启执行器（如果需要）
     */
    private static void restartExecutorIfNeeded() {
        if (executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newFixedThreadPool(THREAD_COUNT);
            LOGGER.debug("RoadWeaver: ExecutorService restarted.");
        }
    }
    
    /**
     * 恢复未完成的道路生成任务
     */
    private static void restoreUnfinishedRoads(ServerWorld serverWorld) {
        List<Records.StructureConnection> connections = WorldDataStorage.getStructureConnections(serverWorld);
        
        int restoredCount = 0;
        for (Records.StructureConnection connection : connections) {
            // 只恢复计划中或生成中的连接
            if (connection.status() == Records.ConnectionStatus.PLANNED || 
                connection.status() == Records.ConnectionStatus.GENERATING) {
                
                // 如果是生成中状态，重置为计划中（因为之前的生成被中断了）
                if (connection.status() == Records.ConnectionStatus.GENERATING) {
                    Records.StructureConnection resetConnection = new Records.StructureConnection(
                            connection.from(), 
                            connection.to(), 
                            Records.ConnectionStatus.PLANNED
                    );
                    StructureConnector.cachedStructureConnections.add(resetConnection);
                    
                    // 更新世界数据中的状态
                    List<Records.StructureConnection> updatedConnections = List.copyOf(connections);
                    int index = connections.indexOf(connection);
                    if (index >= 0) {
                        connections.set(index, resetConnection);
                        WorldDataStorage.setStructureConnections(serverWorld, updatedConnections);
                    }
                } else {
                    StructureConnector.cachedStructureConnections.add(connection);
                }
                restoredCount++;
            }
        }
        
        if (restoredCount > 0) {
            LOGGER.info("RoadWeaver: 恢复了 {} 个未完成的道路生成任务", restoredCount);
        }
    }
}
