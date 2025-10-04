package net.countered.settlementroads;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.events.ModEventHandler;
import net.countered.settlementroads.features.config.RoadFeatureRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SettlementRoads.MOD_ID)
public class SettlementRoads {

	public static final String MOD_ID = "roadweaver";

	private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

	// -5233360391469774945

	// Fix:
	// Clean snow from roads

	// OPTIONAL
	// Location lag reducing (async locator?)/ structure essentials / place instant roads?
	// Bridges
	// Tunnels

	public SettlementRoads() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		modEventBus.addListener(this::commonSetup);
		RoadFeatureRegistry.register(modEventBus);
		modEventBus.addListener(RoadFeatureRegistry::onRegister);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		LOGGER.info("Initializing roadweaver...");
		// 初始化 Cloth Config 配置
		ModConfig.init();
		ModEventHandler.register();
	}
}