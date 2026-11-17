package net.shiroha233.roadweaver.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.shiroha233.roadweaver.RoadWeaver;

/**
 * NeoForge data generation entry.
 *
 * configured_feature and placed_feature are JSON-defined in the common module.
 * NeoForge only consumes biome_modifier resources from:
 * neoforge/src/main/resources/data/roadweaver/neoforge/biome_modifier
 */
@EventBusSubscriber(modid = RoadWeaver.MOD_ID)
public class RoadWeaverDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Server event) {
        RoadWeaver.getLogger().info("RoadWeaver data generation - using JSON-defined features from Common module");
    }
}
