package net.countered.settlementroads.features.config;

import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class RoadFeatureRegistry {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, SettlementRoads.MOD_ID);

    // Configured features are registered via RegisterEvent (Forge 1.20.1)
    // public static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES =
    //         DeferredRegister.create(Registries.CONFIGURED_FEATURE, SettlementRoads.MOD_ID);

    // Placed features are registered via RegisterEvent (Forge 1.20.1)
    // public static final DeferredRegister<PlacedFeature> PLACED_FEATURES =
    //         DeferredRegister.create(Registries.PLACED_FEATURE, SettlementRoads.MOD_ID);

    public static final RegistryObject<Feature<RoadFeatureConfig>> ROAD_FEATURE =
            FEATURES.register("road_feature", () -> new RoadFeature(RoadFeatureConfig.CODEC));

    // Configured feature registered in onRegister

    // Placed feature registered in onRegister

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }

    public static void onRegister(final RegisterEvent event) {
        // Configured features are provided via data pack JSON (see data/roadweaver/worldgen/configured_feature)
        // No runtime registration here to avoid conflicts with data-driven registry.
        // Placed features are provided via data pack JSON (see data/roadweaver/worldgen/placed_feature)
        // No runtime registration here to avoid conflicts with data-driven registry.
    }
}
