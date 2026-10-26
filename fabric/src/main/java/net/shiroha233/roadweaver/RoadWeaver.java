package net.shiroha233.roadweaver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.shiroha233.roadweaver.command.MapAccessCommand;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.worldgen.road.fabric.RoadFeatureRegistry;
import net.shiroha233.roadweaver.network.fabric.MapNetworkFabric;
import net.shiroha233.roadweaver.persistence.fabric.WorldDataAttachment;
import net.shiroha233.roadweaver.planning.fabric.ServerPlanningHooks;
import net.shiroha233.roadweaver.structures.fabric.StructureRegistryFabric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoadWeaver 模组主类 (Fabric)
 */
public class RoadWeaver implements ModInitializer {
    public static final String MOD_ID = "roadweaver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing RoadWeaver (Fabric)...");
        
        WorldDataAttachment.registerWorldDataAttachment();
        
        ConfigService.load();
        LOGGER.info("Configuration loaded");
        
        StructureRegistryFabric.register();
        LOGGER.info("Structure types registered");
        
        RoadFeatureRegistry.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MapAccessCommand.register(dispatcher));
        
        MapNetworkFabric.registerServerReceivers();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                MapNetworkFabric.syncMapAccess(handler.player));
        
        ServerPlanningHooks.register();
    }
}
