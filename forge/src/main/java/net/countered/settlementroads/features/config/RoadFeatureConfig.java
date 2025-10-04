package net.countered.settlementroads.features.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

public class RoadFeatureConfig implements FeatureConfiguration {

    public final List<List<BlockState>> artificialMaterials;
    public final List<List<BlockState>> naturalMaterials;
    public final List<Integer> width;
    public final List<Integer> quality;

    public static final Codec<RoadFeatureConfig> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            BlockState.CODEC.listOf().listOf().fieldOf("artificialMaterials").forGetter(RoadFeatureConfig::getArtificialMaterials),
                            BlockState.CODEC.listOf().listOf().fieldOf("naturalMaterials").forGetter(RoadFeatureConfig::getNaturalMaterials),
                            Codec.INT.listOf().fieldOf("width").forGetter(RoadFeatureConfig::getWidths),
                            Codec.INT.listOf().fieldOf("quality").forGetter(RoadFeatureConfig::getQualities)
                    )
                    .apply(instance, RoadFeatureConfig::new)
    );

    public RoadFeatureConfig(List<List<BlockState>> artificialMaterials, List<List<BlockState>> naturalMaterials, List<Integer> width, List<Integer> quality) {
        this.artificialMaterials = artificialMaterials;
        this.naturalMaterials = naturalMaterials;
        this.width = width;
        this.quality = quality;
    }

    public List<List<BlockState>> getArtificialMaterials() {
        return artificialMaterials;
    }
    public List<List<BlockState>> getNaturalMaterials() {
        return naturalMaterials;
    }
    public List<Integer> getWidths() {
        return width;
    }
    public List<Integer> getQualities() {
        return quality;
    }
}
