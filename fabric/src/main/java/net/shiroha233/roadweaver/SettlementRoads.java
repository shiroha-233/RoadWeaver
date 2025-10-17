package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.config.fabric.FabricModConfig;
import net.shiroha233.roadweaver.events.ModEventHandler;
import net.shiroha233.roadweaver.events.RedstoneLampOfTheRoadEffectHandler;
import net.shiroha233.roadweaver.features.config.RoadFeatureRegistry;
import net.shiroha233.roadweaver.features.config.FabricBiomeInjection;
import net.shiroha233.roadweaver.network.RoadWeaverNetworkManager;
import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shiroha233.roadweaver.registry.ModEffects;
import net.shiroha233.roadweaver.registry.ModBlocks;
public class SettlementRoads implements ModInitializer {

	public static final String MOD_ID = "roadweaver";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing RoadWeaver (Fabric)...");
        ModBlocks.register();    ModEffects.register();        RedstoneLampOfTheRoadEffectHandler.register();
		// 注册 Fabric Attachment API
		WorldDataAttachment.registerWorldDataAttachment();
		
		// 加载配置
		FabricModConfig.load();
		LOGGER.info("Configuration loaded");
		
		// 注册网络包
		RoadWeaverNetworkManager.registerPackets();
		LOGGER.info("Network packets registered");
		
		// 注册特性
		RoadFeatureRegistry.registerFeatures();
		// Fabric 端通过 BiomeModifications 注入放置特性
		FabricBiomeInjection.inject();
		
		// 注册事件处理器
		ModEventHandler.register();
	}
}