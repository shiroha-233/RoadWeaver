package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.features.forge.RoadFeaturesForge;
import net.shiroha233.roadweaver.network.forge.MapNetworkForge;
import net.shiroha233.roadweaver.planning.forge.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.forge.StructureRegistryForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {

    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    public RoadWeaver() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("Initializing RoadWeaver (Forge)...");
        
        // 加载配置（common 实现，写入 config/roadweaver.json）
        ConfigService.load();
        
        // 注册数据生成事件（确保 runData 时 provider 被加入）

        // 注册结构类型（需要在特性注册之前）
        StructureRegistryForge.register(modEventBus);
        
        // 注册 Feature
        RoadFeaturesForge.register(modEventBus);
        
        // 注册网络通道
        MapNetworkForge.register();

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPermissionsChanged);
        
        // 注册服务器规划钩子：初始与动态增量规划
        ServerPlanningHooks.register();

    }

    
    public static Logger getLogger() {
        return LOGGER;
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MapAccessCommand.register(event.getDispatcher());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetworkForge.syncMapAccess(player);
        }
    }

    private void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MapNetworkForge.syncMapAccess(player);
        }
    }
}
