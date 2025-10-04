package net.countered.settlementroads;

import net.countered.datagen.ModWorldGenerator;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SettlementRoads.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SettlementRoadsDataGenerator {

    @SubscribeEvent
    public static void gatherData(final GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var lookupProvider = event.getLookupProvider();

        // Add our data provider to generate configured and placed features
        generator.addProvider(event.includeServer(), new ModWorldGenerator(output, lookupProvider));
    }
}
