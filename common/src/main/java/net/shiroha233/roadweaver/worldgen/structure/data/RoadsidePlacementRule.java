package net.shiroha233.roadweaver.worldgen.structure.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 路边结构放置规则
 */
public record RoadsidePlacementRule(
    Set<BiomeCategory> allowedBiomes,
    int minRoadLength
) {
    
    public static final Codec<RoadsidePlacementRule> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BiomeCategory.CODEC.listOf()
                .optionalFieldOf("allowed_biomes", List.of())
                .forGetter(rule -> List.copyOf(rule.allowedBiomes)),
            Codec.INT.optionalFieldOf("min_road_length", 0).forGetter(RoadsidePlacementRule::minRoadLength)
        ).apply(instance, (biomes, minLen) -> {
            Set<BiomeCategory> biomeSet = biomes.isEmpty() 
                ? EnumSet.allOf(BiomeCategory.class) 
                : EnumSet.copyOf(biomes);
            return new RoadsidePlacementRule(biomeSet, minLen);
        })
    );
    
    public boolean isBiomeAllowed(BiomeCategory category) {
        return allowedBiomes.contains(category);
    }
    
    public boolean isRoadLongEnough(int roadLength) {
        return roadLength >= minRoadLength;
    }
    
    public static final RoadsidePlacementRule UNIVERSAL = new RoadsidePlacementRule(
        EnumSet.allOf(BiomeCategory.class), 0
    );
    
    public static final RoadsidePlacementRule TEMPERATE = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.PLAINS, BiomeCategory.FOREST, BiomeCategory.TAIGA, BiomeCategory.CHERRY_GROVE), 0
    );
    
    public static final RoadsidePlacementRule COLD = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.SNOWY, BiomeCategory.TAIGA), 0
    );
    
    public static final RoadsidePlacementRule HOT = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.DESERT, BiomeCategory.SAVANNA, BiomeCategory.BADLANDS), 0
    );
    
    public static final RoadsidePlacementRule LONG_ROAD_ONLY = new RoadsidePlacementRule(
        EnumSet.allOf(BiomeCategory.class), 100
    );
    
    public static final RoadsidePlacementRule CHERRY_ONLY = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.CHERRY_GROVE), 0
    );
    
    public static final RoadsidePlacementRule FOREST_ONLY = new RoadsidePlacementRule(
        EnumSet.of(BiomeCategory.FOREST), 0
    );
}
