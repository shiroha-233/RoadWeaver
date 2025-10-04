package net.countered.settlementroads.features.config;

import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ModPlacedFeatures {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static void bootstrapPlacedFeatures(BootstapContext<PlacedFeature> context) {
        LOGGER.info("Bootstrap PlacedFeature");
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatureLookup = context.lookup(Registries.CONFIGURED_FEATURE);

        context.register(RoadFeature.ROAD_FEATURE_PLACED_KEY,
                new PlacedFeature(configuredFeatureLookup.getOrThrow(RoadFeature.ROAD_FEATURE_KEY),
                        List.of(PlacementUtils.HEIGHTMAP_WORLD_SURFACE)));
    }
}
