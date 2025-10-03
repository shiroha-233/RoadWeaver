package net.countered.settlementroads.events;


import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.RoadFeature;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.roadlogic.Road;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.persistence.attachments.WorldDataAttachment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.countered.settlementroads.SettlementRoads.MOD_ID;

public class ModEventHandler {

    private static final int THREAD_COUNT= 7;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public static void register() {

        ServerWorldEvents.LOAD.register((server, serverWorld) -> {
            restartExecutorIfNeeded();
            if (!serverWorld.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;
            Records.StructureLocationData structureLocationData = serverWorld.getAttachedOrCreate(WorldDataAttachment.STRUCTURE_LOCATIONS, () -> new Records.StructureLocationData(new ArrayList<>()));

            // 🆕 恢复未完成的道路生成任务
            restoreUnfinishedRoads(serverWorld);

            if (structureLocationData.structureLocations().size() < ModConfig.initialLocatingCount) {
                for (int i = 0; i < ModConfig.initialLocatingCount; i++) {
                    StructureConnector.cacheNewConnection(serverWorld, false);
                    tryGenerateNewRoads(serverWorld, true, 5000);
                }
            }
        });

        ServerWorldEvents.UNLOAD.register((server, serverWorld) -> {
            if (!serverWorld.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;
            Future<?> task = runningTasks.remove(serverWorld.getRegistryKey().getValue().toString());
            if (task != null && !task.isDone()) {
                task.cancel(true);
                LOGGER.debug("Aborted running road task for world: {}", serverWorld.getRegistryKey().getValue());
            }
        });

        ServerTickEvents.START_WORLD_TICK.register((serverWorld) -> {
            if (!serverWorld.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;
            tryGenerateNewRoads(serverWorld, true, 5000);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RoadPathCalculator.heightCache.clear();
            runningTasks.values().forEach(future -> future.cancel(true));
            runningTasks.clear();
            executor.shutdownNow();
            LOGGER.debug("RoadWeaver: ExecutorService shut down.");
        });
    }

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
                }
                else {
                    new Road(serverWorld, structureConnection, roadConfig).generateRoad(steps);
                }
            }
        }
    }

    private static void restartExecutorIfNeeded() {
        if (executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newFixedThreadPool(THREAD_COUNT);
            LOGGER.debug("RoadWeaver: ExecutorService restarted.");
        }
    }

    /**
     * 恢复未完成的道路生成任务
     * 在世界加载时调用，将所有 PLANNED 和 GENERATING 状态的连接重新加入队列
     */
    private static void restoreUnfinishedRoads(ServerWorld serverWorld) {
        List<Records.StructureConnection> connections = serverWorld.getAttachedOrCreate(
                WorldDataAttachment.CONNECTED_STRUCTURES, 
                ArrayList::new
        );
        
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
                    List<Records.StructureConnection> updatedConnections = new ArrayList<>(connections);
                    int index = updatedConnections.indexOf(connection);
                    if (index >= 0) {
                        updatedConnections.set(index, resetConnection);
                        serverWorld.setAttached(WorldDataAttachment.CONNECTED_STRUCTURES, updatedConnections);
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
