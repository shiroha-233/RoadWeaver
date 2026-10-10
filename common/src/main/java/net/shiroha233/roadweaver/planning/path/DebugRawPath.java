package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.List;
import java.util.Objects;

/** Node coordinates before simplification, smoothing, rasterization or road-height adjustments. */
public record DebugRawPath(TerrainSamplingMode mode, List<BlockPos> coarse, List<BlockPos> accurate) {
    public DebugRawPath {
        Objects.requireNonNull(mode, "mode");
        coarse = List.copyOf(coarse == null ? List.of() : coarse);
        accurate = List.copyOf(accurate == null ? List.of() : accurate);
        if (mode != TerrainSamplingMode.COARSE_CORRIDOR) coarse = List.of();
    }
}
