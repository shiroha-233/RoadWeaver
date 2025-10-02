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
}
