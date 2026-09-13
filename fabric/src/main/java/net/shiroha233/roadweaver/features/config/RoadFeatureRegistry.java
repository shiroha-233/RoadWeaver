/* 文件职责：在 Fabric 主世界生物群系的表层阶段注册道路收尾 Feature。 */
package net.shiroha233.roadweaver.features.config;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.path.PathFeature;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;

/**
 * Fabric Feature 注册
 */
public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {}

    public static void register() {
        Feature<PathFeatureConfig> feature = new PathFeature(PathFeatureConfig.CODEC);
        Registry.register(BuiltInRegistries.FEATURE, new ResourceLocation(RoadWeaver.MOD_ID, "road_feature"), feature);

        ResourceKey<PlacedFeature> placedKey = ResourceKey.create(
                Registries.PLACED_FEATURE,
                new ResourceLocation(RoadWeaver.MOD_ID, "road_feature_placed"));
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placedKey);
    }
}
