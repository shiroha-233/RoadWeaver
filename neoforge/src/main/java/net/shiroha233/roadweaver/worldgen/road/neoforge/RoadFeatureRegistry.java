package net.shiroha233.roadweaver.worldgen.road.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.worldgen.road.PathFeature;

/**
 * NeoForge Feature 注册：把道路收尾 Feature 挂到 road_feature。
 */
public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.FEATURE_TYPE)) {
                event.register(
                        Registries.FEATURE_TYPE,
                        Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "road_feature"),
                        () -> PathFeature.CODEC);
            }
        });
    }
}
