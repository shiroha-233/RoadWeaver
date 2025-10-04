package net.countered.settlementroads.events;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.RoadFeature;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.roadlogic.Road;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureConnectionsData;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureLocationsData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static net.countered.settlementroads.SettlementRoads.MOD_ID;

@Mod.EventBusSubscriber(modid = MOD_ID)
public class ModEventHandler {

	private static final int THREAD_COUNT = 7;
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
	private static final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

	public static void register() {
		// Fabric 版本中的事件注册在 Forge 由注解驱动，无需额外操作
	}

	@SubscribeEvent
	public static void onLevelLoad(LevelEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
			return;
		}

		restartExecutorIfNeeded();
		StructureLocationsData locationsData = WorldDataHelper.structureLocations(serverLevel);

		restoreUnfinishedRoads(serverLevel);

		if (locationsData.getLocations().size() < ModConfig.initialLocatingCount) {
			for (int i = 0; i < ModConfig.initialLocatingCount; i++) {
				StructureConnector.cacheNewConnection(serverLevel, false);
				tryGenerateNewRoads(serverLevel, true, 5000);
			}
		}
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
			return;
		}

		Future<?> task = runningTasks.remove(serverLevel.dimension().location().toString());
		if (task != null && !task.isDone()) {
			task.cancel(true);
			LOGGER.debug("RoadWeaver: Aborted running road task for world {}", serverLevel.dimension().location());
		}
	}

	@SubscribeEvent
	public static void onWorldTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.START) {
			return;
		}
		if (!(event.level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
			return;
		}

		tryGenerateNewRoads(serverLevel, true, 5000);
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		RoadPathCalculator.heightCache.clear();
		runningTasks.values().forEach(future -> future.cancel(true));
		runningTasks.clear();
		executor.shutdownNow();
		LOGGER.debug("RoadWeaver: ExecutorService shut down.");
	}

	private static void tryGenerateNewRoads(ServerLevel serverWorld, boolean async, int steps) {
		runningTasks.entrySet().removeIf(entry -> entry.getValue().isDone());

		if (runningTasks.size() >= ModConfig.maxConcurrentRoadGeneration) {
			LOGGER.debug("RoadWeaver: Maximum concurrent road generation tasks reached. Skipping generation.");
			return;
		}

		if (StructureConnector.cachedStructureConnections.isEmpty()) {
			return;
		}

		Records.StructureConnection structureConnection = StructureConnector.cachedStructureConnections.poll();
		if (structureConnection == null) {
			return;
		}

		Holder<ConfiguredFeature<?, ?>> featureHolder = serverWorld.registryAccess()
				.registryOrThrow(Registries.CONFIGURED_FEATURE)
				.getHolder(RoadFeature.ROAD_FEATURE_KEY)
				.orElse(null);

		if (featureHolder == null) {
			LOGGER.debug("RoadWeaver: road feature is not registered in this world.");
			return;
		}

		if (!(featureHolder.value().config() instanceof RoadFeatureConfig roadConfig)) {
			LOGGER.debug("RoadWeaver: road feature config type mismatch, skip generation.");
			return;
		}

		if (async) {
			String taskId = serverWorld.dimension().location().toString() + "_" + System.nanoTime();
			Future<?> future = executor.submit(() -> {
				try {
					new Road(serverWorld, structureConnection, roadConfig).generateRoad(steps);
				} catch (Exception e) {
					LOGGER.error("RoadWeaver: Error generating road asynchronously", e);
				} finally {
					runningTasks.remove(taskId);
				}
			});
			runningTasks.put(taskId, future);
		} else {
			new Road(serverWorld, structureConnection, roadConfig).generateRoad(steps);
		}
	}

	private static void restartExecutorIfNeeded() {
		if (executor.isShutdown() || executor.isTerminated()) {
			executor = Executors.newFixedThreadPool(THREAD_COUNT);
			LOGGER.debug("RoadWeaver: ExecutorService restarted.");
		}
	}

	/**
	 * 恢复未完成的道路生成任务，在世界加载时将未完成连接重新入队。
	 */
	private static void restoreUnfinishedRoads(ServerLevel serverWorld) {
		StructureConnectionsData connectionsData = WorldDataHelper.structureConnections(serverWorld);
		List<Records.StructureConnection> connections = new ArrayList<>(connectionsData.getConnections());

		int restoredCount = 0;
		for (int i = 0; i < connections.size(); i++) {
			Records.StructureConnection connection = connections.get(i);
			if (connection.status() == Records.ConnectionStatus.PLANNED ||
				connection.status() == Records.ConnectionStatus.GENERATING) {
				Records.StructureConnection queuedConnection = connection;

				if (connection.status() == Records.ConnectionStatus.GENERATING) {
					queuedConnection = new Records.StructureConnection(
							connection.from(),
							connection.to(),
							Records.ConnectionStatus.PLANNED
					);
					connections.set(i, queuedConnection);
				}

				StructureConnector.cachedStructureConnections.add(queuedConnection);
				restoredCount++;
			}
		}

		connectionsData.setConnections(connections);

		if (restoredCount > 0) {
			LOGGER.info("RoadWeaver: 恢复了 {} 个未完成的道路生成任务", restoredCount);
		}
	}

}
