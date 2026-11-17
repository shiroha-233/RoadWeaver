package net.shiroha233.roadweaver;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.network.neoforge.MapNetwork;
import net.shiroha233.roadweaver.persistence.files.FileBackedAccurateSampleStore;
import net.shiroha233.roadweaver.planning.neoforge.ServerPlanningHooks;
import net.shiroha233.roadweaver.terrain.accurate.AccurateSampleStoreProviders;
import net.shiroha233.roadweaver.worldgen.road.neoforge.RoadFeatureRegistry;
import net.shiroha233.roadweaver.worldgen.structure.neoforge.StructureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (NeoForge)
 */
@Mod(RoadWeaver.MOD_ID)
public class RoadWeaver {

    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RoadWeaver(IEventBus modEventBus) {
        LOGGER.info("Initializing RoadWeaver (NeoForge)...");

        ConfigService.load();
        AccurateSampleStoreProviders.install(level -> FileBackedAccurateSampleStore.create(level));

        StructureRegistry.register(modEventBus);
        RoadFeatureRegistry.register(modEventBus);
        MapNetwork.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPermissionsChanged);

        ServerPlanningHooks.register(modEventBus);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(RoadWeaver::onClientLoggingIn);
            NeoForge.EVENT_BUS.addListener(RoadWeaver::onClientLoggingOut);
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache.clearNow();
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache.clearNow();
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
