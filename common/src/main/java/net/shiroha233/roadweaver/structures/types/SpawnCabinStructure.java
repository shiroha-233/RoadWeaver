package net.shiroha233.roadweaver.structures.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;

import java.util.Optional;

/**
 * 初始小屋结构类型
 */
public class SpawnCabinStructure extends Structure {
    
    public static final MapCodec<SpawnCabinStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.templateId),
            Vec3i.CODEC.optionalFieldOf("size_hint", new Vec3i(16, 10, 16)).forGetter(s -> s.sizeHint),
            Codec.BOOL.optionalFieldOf("with_terrace", true).forGetter(s -> s.withTerrace),
            Codec.INT.optionalFieldOf("terrace_inner_radius", 10).forGetter(s -> s.terraceInnerRadius),
            Codec.INT.optionalFieldOf("terrace_outer_radius", 16).forGetter(s -> s.terraceOuterRadius)
        ).apply(instance, SpawnCabinStructure::new)
    );
    
    private final ResourceLocation templateId;
    private final Vec3i sizeHint;
    private final boolean withTerrace;
    private final int terraceInnerRadius;
    private final int terraceOuterRadius;
    
    public SpawnCabinStructure(StructureSettings settings,
                               ResourceLocation templateId,
                               Vec3i sizeHint,
                               boolean withTerrace,
                               int terraceInnerRadius,
                               int terraceOuterRadius) {
        super(settings);
        this.templateId = templateId;
        this.sizeHint = sizeHint;
        this.withTerrace = withTerrace;
        this.terraceInnerRadius = terraceInnerRadius;
        this.terraceOuterRadius = terraceOuterRadius;
    }
    
    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return Optional.empty();
    }
    
    @Override
    public StructureType<?> type() {
        return ModStructureTypes.SPAWN_CABIN;
    }
    
    public ResourceLocation templateId() {
        return templateId;
    }
    
    public Vec3i sizeHint() {
        return sizeHint;
    }
    
    public boolean withTerrace() {
        return withTerrace;
    }
    
    public int terraceInnerRadius() {
        return terraceInnerRadius;
    }
    
    public int terraceOuterRadius() {
        return terraceOuterRadius;
    }
    
    public StructureStart placeAt(WorldGenLevel level,
                                  StructureManager structureManager,
                                  StructureTemplateManager templateManager,
                                  ChunkGenerator generator,
                                  BlockPos pos,
                                  Rotation rotation,
                                  RandomSource random) {
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        
        SimpleTemplatePiece piece = new SimpleTemplatePiece(
            templateManager,
            templateId,
            pos,
            rotation,
            Mirror.NONE
        );
        builder.addPiece(piece);
        
        ChunkPos chunkPos = new ChunkPos(pos);
        PiecesContainer container = builder.build();
        StructureStart start = new StructureStart(this, chunkPos, 0, container);
        
        if (!start.isValid()) {
            return null;
        }
        
        BoundingBox boundingBox = start.getBoundingBox();
        // Built outside world generation: extend the processing box downwards so the piece can add its foundation.
        BoundingBox processBox = new BoundingBox(boundingBox.minX(), level.getMinBuildHeight(), boundingBox.minZ(),
                boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ());
        start.placeInChunk(level, structureManager, generator, random, processBox, chunkPos);
        
        return start;
    }
}
