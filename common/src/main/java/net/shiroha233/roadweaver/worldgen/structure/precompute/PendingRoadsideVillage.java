package net.shiroha233.roadweaver.worldgen.structure.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * 待注入的路边村庄
 */
public record PendingRoadsideVillage(
    Identifier placementId,
    Identifier structureId,
    ChunkPos originChunk,
    int windowStartIndex,
    int windowEndIndex,
    Identifier style,
    long seed,
    BoundingBox estimatedBounds,
    List<PendingRoadsideVillageSlot> slots
) {
    public long chunkKey() {
        return originChunk.pack();
    }

    public BlockPos center() {
        return new BlockPos(
            (estimatedBounds.minX() + estimatedBounds.maxX()) / 2,
            (estimatedBounds.minY() + estimatedBounds.maxY()) / 2,
            (estimatedBounds.minZ() + estimatedBounds.maxZ()) / 2
        );
    }
}