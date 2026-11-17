package net.shiroha233.roadweaver.worldgen.structure.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 桥模板结构类型
 */
public class BridgeTemplateStructure extends Structure {
    public static final MapCodec<BridgeTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    settingsCodec(instance),
                    Identifier.CODEC.fieldOf("template").forGetter(s -> s.templateId),
                    Codec.INT.optionalFieldOf("height_offset", 1).forGetter(s -> s.heightOffset),
                    Codec.INT.optionalFieldOf("bridge_deck_start", 0).forGetter(s -> s.bridgeDeckStart),
                    Codec.INT.optionalFieldOf("bridge_deck_end", 100).forGetter(s -> s.bridgeDeckEnd)
            ).apply(instance, (settings, template, height, start, end) -> new BridgeTemplateStructure(settings, template, height, start, end))
    );

    public final Identifier templateId;
    public final int heightOffset;
    public final int bridgeDeckStart;
    public final int bridgeDeckEnd;

    public BridgeTemplateStructure(StructureSettings structureSettings, Identifier templateId, int heightOffset, int bridgeDeckStart, int bridgeDeckEnd) {
        super(structureSettings);
        this.templateId = templateId;
        this.heightOffset = heightOffset;
        this.bridgeDeckStart = bridgeDeckStart;
        this.bridgeDeckEnd = bridgeDeckEnd;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext generationContext) {
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.BRIDGE_TEMPLATE;
    }
}
