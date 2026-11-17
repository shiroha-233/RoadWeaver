package net.shiroha233.roadweaver.worldgen.structure.precompute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * 待放置的路边结构信息
 */
public record PendingRoadsideStructure(
    Identifier structureId,
    BlockPos anchor,
    Rotation rotation,
    int sizeX,
    int sizeY,
    int sizeZ
) {
    public static final Codec<PendingRoadsideStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Identifier.CODEC.fieldOf("structure_id").forGetter(PendingRoadsideStructure::structureId),
            BlockPos.CODEC.fieldOf("anchor").forGetter(PendingRoadsideStructure::anchor),
            Codec.STRING.fieldOf("rotation").xmap(Rotation::valueOf, Rotation::name).forGetter(PendingRoadsideStructure::rotation),
            Codec.INT.fieldOf("size_x").forGetter(PendingRoadsideStructure::sizeX),
            Codec.INT.fieldOf("size_y").forGetter(PendingRoadsideStructure::sizeY),
            Codec.INT.fieldOf("size_z").forGetter(PendingRoadsideStructure::sizeZ)
        ).apply(instance, PendingRoadsideStructure::new)
    );
    
    public int chunkX() {
        return anchor.getX() >> 4;
    }
    
    public int chunkZ() {
        return anchor.getZ() >> 4;
    }
    
    public long chunkKey() {
        return ((long) chunkX() << 32) | (chunkZ() & 0xFFFFFFFFL);
    }
}
