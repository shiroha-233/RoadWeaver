package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 最终道路路径精确高度解析器。
 */
public final class AccuratePathHeightResolver {
    private AccuratePathHeightResolver() {}

    public static List<BlockPos> resolve(List<BlockPos> path,
                                         PathTerrainField terrain,
                                         AccurateHeightSampler sampler) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos position : path) {
            if (!hasAccurateTerrainSample(terrain, position)) {
                missing.add(position);
            }
        }
        Map<Long, AccurateHeightSample> sampled = sampler.samplePositions(missing);

        List<BlockPos> resolved = new ArrayList<>(path.size());
        for (BlockPos position : path) {
            int y;
            if (hasAccurateTerrainSample(terrain, position)) {
                y = terrain.height(position.getX(), position.getZ());
            } else {
                AccurateHeightSample sample = sampled.get(AccurateHeightSample.key(position.getX(), position.getZ()));
                y = sample != null ? sampler.surfaceHeight(sample) : sampler.surfaceHeight(position.getX(), position.getZ());
            }
            resolved.add(new BlockPos(position.getX(), y, position.getZ()));
        }
        return resolved;
    }

    private static boolean hasAccurateTerrainSample(PathTerrainField terrain, BlockPos position) {
        return terrain != null && terrain.hasAccurateSample(position.getX(), position.getZ());
    }
}
