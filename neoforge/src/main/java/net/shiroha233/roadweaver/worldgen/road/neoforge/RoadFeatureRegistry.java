package net.shiroha233.roadweaver.worldgen.road.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.worldgen.road.PathFeature;
import net.shiroha233.roadweaver.worldgen.road.PathFeatureConfig;

/**
 * NeoForge Feature 注册：把道路收尾 Feature 挂到 oad_feature。
 */
public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.FEATURE)) {
                event.register(
                        Registries.FEATURE,
                        ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "road_feature"),
                        () -> new PathFeature(PathFeatureConfig.CODEC));
            }
        });
    }
}
