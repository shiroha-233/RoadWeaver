package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.client.gui.ClothConfigScreen;
import net.shiroha233.roadweaver.config.forge.ForgeJsonConfig;
import net.shiroha233.roadweaver.events.ModEventHandler;
import net.shiroha233.roadweaver.events.RedstoneLampOfTheRoadEffectHandler;
import net.shiroha233.roadweaver.features.config.forge.ForgeRoadFeatureRegistry;
import net.shiroha233.roadweaver.datagen.SettlementRoadsDataGenerator;
import net.shiroha233.roadweaver.network.RoadWeaverNetworkManager;
import net.minecraftforge.eventbus.api.IEventBus;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.shiroha233.roadweaver.registry.ModBlocks;
import net.shiroha233.roadweaver.registry.ModEffects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SettlementRoads.MOD_ID)
public class SettlementRoads {

	public static final String MOD_ID = "roadweaver";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public SettlementRoads() {
		LOGGER.info("Initializing RoadWeaver (Forge)...");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 🔹 通知 Architectury 绑定事件总线（非常关键）
        EventBuses.registerModEventBus(MOD_ID, modEventBus);

        // 🔹 注册方块、物品等
        ModBlocks.register();ModEffects.register();RedstoneLampOfTheRoadEffectHandler.register();
		// 加载 JSON 配置（与 Fabric 一致，写入 config/roadweaver.json）
		ForgeJsonConfig.load();
		
		// 注册配置屏幕（Forge 模组菜单集成）
		ModLoadingContext.get().registerExtensionPoint(
			net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
				(client, parent) -> ClothConfigScreen.createConfigScreen(parent)
			)
		);
		
		// 使用 Forge 原生的 DeferredRegister 注册特性
		// 避免 Architectury DeferredRegister 的事件总线注册时序问题
		ForgeRoadFeatureRegistry.register(modEventBus);
		
		// 注册通用设置事件
		modEventBus.addListener(this::commonSetup);
		// 注册数据生成事件（确保 runData 时 provider 被加入）
		modEventBus.addListener(SettlementRoadsDataGenerator::gatherData);
		
		// 注册事件处理器（使用 Architectury 事件的 common 实现）
		ModEventHandler.register();
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		// 注册网络包
		event.enqueueWork(() -> {
			RoadWeaverNetworkManager.registerPackets();
			LOGGER.info("Network packets registered");
		});
		LOGGER.info("RoadWeaver common setup completed");
	}
	
	public static Logger getLogger() {
		return LOGGER;
	}
}
