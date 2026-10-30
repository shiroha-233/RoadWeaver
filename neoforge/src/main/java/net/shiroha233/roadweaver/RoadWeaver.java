package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.datagen.RoadWeaverDataGenerator;
import net.shiroha233.roadweaver.worldgen.road.neoforge.RoadFeatureRegistry;
import net.shiroha233.roadweaver.network.neoforge.MapNetwork;
import net.shiroha233.roadweaver.planning.neoforge.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.neoforge.StructureRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {

    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    public RoadWeaver(IEventBus modEventBus) {
        LOGGER.info("Initializing RoadWeaver (NeoForge)...");
        
        // 加载配置（common 实现，写入 config/roadweaver.json）
        ConfigService.load();
        
        // 注册数据生成事件（确保 runData 时 provider 被加入）
        modEventBus.addListener(RoadWeaverDataGenerator::gatherData);

        // 注册结构类型（需要在特性注册之前）
        StructureRegistry.register(modEventBus);
        
        // 注册 Feature
        RoadFeatureRegistry.register(modEventBus);
        
        // 注册网络通道
        MapNetwork.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPermissionsChanged);
        
        // 注册服务器规划钩子：初始与动态增量规划
        ServerPlanningHooks.register(modEventBus);

        if (FMLLoader.getDist() == Dist.CLIENT) {
            // NeoForge 21.1.x 中配置屏幕的处理方式可能有所不同
            // 暂时注释掉，需要进一步研究正确的 API
            // ModLoadingContext.get().registerExtensionPoint(
            //         ConfigScreenHandler.ConfigScreenFactory.class,
            //         () -> new ConfigScreenHandler.ConfigScreenFactory(
            //                 (mc, screen) -> net.shiroha233.roadweaver.client.neoforge.ConfigScreenFactoryImpl.createConfigScreen(screen)
            //         )
            // );
            NeoForge.EVENT_BUS.addListener(RoadWeaver::onClientLoggingIn);
            NeoForge.EVENT_BUS.addListener(RoadWeaver::onClientLoggingOut);
        }
    }
    
    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache.clearNow();
    }
    
    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache.clearNow();
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MapAccessCommand.register(event.getDispatcher());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetwork.syncMapAccess(player);
        }
    }

    private void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetwork.syncMapAccess(player);
        }
    }
}
