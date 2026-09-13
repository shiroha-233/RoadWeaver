package net.shiroha233.roadweaver.datagen;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.data.event.GatherDataEvent;
import net.shiroha233.roadweaver.RoadWeaver;

/**
 * Forge data generation entry.
 *
 * configured_feature and placed_feature are JSON-defined in the common module.
 * Forge only consumes biome_modifier resources from:
 * forge/src/main/resources/data/roadweaver/forge/biome_modifier
 */
@Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RoadWeaverDataGenerator {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        RoadWeaver.getLogger().info("RoadWeaver data generation - using JSON-defined features from Common module");
    }
}
