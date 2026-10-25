package net.shiroha233.roadweaver.map.search;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record MapSearchResult(BlockPos pos, String structureId, int source) {
    public MapSearchResult {
        Objects.requireNonNull(pos, "pos");
        pos = new BlockPos(pos.getX(), 0, pos.getZ());
        structureId = structureId == null || structureId.isBlank() ? "unknown" : structureId;
    }

    public MapStructureSource sourceType() {
        return MapStructureSource.fromId(source);
    }
}
