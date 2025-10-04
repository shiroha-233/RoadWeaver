package net.countered.settlementroads.features.config;

import net.countered.settlementroads.RoadWeaver;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.HeightmapPlacementModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ModPlacedFeatures {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoadWeaver.MOD_ID);

    public static void bootstrap(Registerable<PlacedFeature> context){
        LOGGER.info("Bootstrap PlacedFeature");
        var configuredFeatureRegistryEntryLookup = context.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE);

        context.register(RoadFeature.ROAD_FEATURE_PLACED_KEY,
                new PlacedFeature(configuredFeatureRegistryEntryLookup.getOrThrow(RoadFeature.ROAD_FEATURE_KEY),
                        List.of(HeightmapPlacementModifier.of(Heightmap.Type.WORLD_SURFACE_WG)))
        );
    }
}