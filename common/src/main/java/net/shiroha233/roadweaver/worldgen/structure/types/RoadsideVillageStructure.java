package net.shiroha233.roadweaver.worldgen.structure.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 路边村庄结构类型
 */
public class RoadsideVillageStructure extends Structure {
    public static final MapCodec<RoadsideVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            settingsCodec(instance),
            Codec.INT.optionalFieldOf("max_depth", 1).forGetter(s -> s.maxDepth),
            Codec.INT.optionalFieldOf("max_distance_from_center", 96).forGetter(s -> s.maxDistanceFromCenter)
        ).apply(instance, RoadsideVillageStructure::new)
    );

    private final int maxDepth;
    private final int maxDistanceFromCenter;

    public RoadsideVillageStructure(StructureSettings settings, int maxDepth, int maxDistanceFromCenter) {
        super(settings);
        this.maxDepth = maxDepth;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.ROADSIDE_VILLAGE;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int maxDistanceFromCenter() {
        return maxDistanceFromCenter;
    }
}
