package net.shiroha233.roadweaver.worldgen.road;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 普通道路 Feature 配置
 */
public class PathFeatureConfig implements FeatureConfiguration {
    public static final Codec<PathFeatureConfig> CODEC = Codec.unit(new PathFeatureConfig());
}
