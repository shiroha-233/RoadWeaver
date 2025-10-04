package net.countered.settlementroads.features.config;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.features.RoadFeature;
import dev.architectury.registry.level.biome.BiomeModifications;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoadFeatureRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadWeaver.MOD_ID);

    public static void registerFeatures() {
        LOGGER.info("Registering road_feature");
        Registry.register(Registries.FEATURE, Identifier.of(RoadWeaver.MOD_ID, "road_feature"), RoadFeature.ROAD_FEATURE);
        BiomeModifications.addProperties((context, mutable) -> {
            mutable.getGenerationProperties().addFeature(GenerationStep.Feature.LOCAL_MODIFICATIONS, RoadFeature.ROAD_FEATURE_PLACED_KEY);
        });
    }
}
