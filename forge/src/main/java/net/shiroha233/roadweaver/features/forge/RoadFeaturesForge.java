package net.shiroha233.roadweaver.features.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.path.PathFeature;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;

public final class RoadFeaturesForge {
    private RoadFeaturesForge() {
    }

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, RoadWeaver.MOD_ID);

    public static final RegistryObject<Feature<PathFeatureConfig>> ROAD_FEATURE = FEATURES.register(
            "road_feature", () -> new PathFeature(PathFeatureConfig.CODEC));

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
