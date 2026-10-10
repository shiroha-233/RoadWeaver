package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.*;

/** Sparse, grid-aligned accurate field. Searches cannot leave the sampled corridor. */
final class AccurateCorridorTerrain implements PathTerrainField {
    private final ServerLevel level;
    private final TerrainSamplingCache cache;
    private final AccurateHeightSampler sampler;
    private final int step;
    private final Map<Long, AccurateHeightSample> samples;

    AccurateCorridorTerrain(ServerLevel level, TerrainSamplingCache cache, List<BlockPos> path, int step) {
        this.level = level;
        this.cache = cache;
        this.sampler = cache.getAccurateSampler(level);
        this.step = step;
        samples = sampler.samplePositions(positions(path, step, Math.max(64, step * 2)));
    }

    static List<BlockPos> positions(List<BlockPos> path, int step, int radius) {
        LinkedHashMap<Long, BlockPos> points = new LinkedHashMap<>();
        int cells = (radius + step - 1) / step;
        for (int i = 0; i < path.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            BlockPos a = path.get(i), b = path.get(Math.min(i + 1, path.size() - 1));
            int divisions = Math.max(1, Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) / step);
            for (int n = 0; n <= divisions; n++) {
                int x = Math.floorDiv((int) Math.round(a.getX() + (b.getX() - a.getX()) * n / (double) divisions), step) * step;
                int z = Math.floorDiv((int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * n / (double) divisions), step) * step;
                for (int dz = -cells; dz <= cells; dz++) for (int dx = -cells; dx <= cells; dx++) {
                    BlockPos p = new BlockPos(x + dx * step, 0, z + dz * step);
                    points.putIfAbsent(AccurateHeightSample.key(p.getX(), p.getZ()), p);
                    if (points.size() > 500_000) throw new IllegalArgumentException("accurate corridor sample budget exceeded");
                }
            }
        }
        return List.copyOf(points.values());
    }

    private AccurateHeightSample sample(int x, int z) { return samples.get(AccurateHeightSample.key(x, z)); }
    public int seaLevel() { return level.getSeaLevel(); }
    public int step() { return step; }
    public boolean contains(int x, int z) { return sample(x, z) != null; }
    public boolean hasAccurateSample(int x, int z) { return contains(x, z); }
    public int height(int x, int z) { return contains(x, z) ? sampler.surfaceHeight(sample(x, z)) : seaLevel(); }
    public int oceanFloor(int x, int z) { return contains(x, z) ? sample(x, z).oceanFloorWg() : seaLevel(); }
    public Holder<Biome> biome(int x, int z) { return contains(x, z) ? cache.getBiome(level, x, z) : null; }
    public boolean isColumnWater(int x, int z) {
        AccurateHeightSample s = sample(x, z);
        return s != null && ((isWaterBiome(x, z) && s.oceanFloorWg() < seaLevel()) || s.oceanFloorWg() < s.worldSurfaceWg());
    }
    public boolean isNearWater(int x, int z, int distance) {
        int d = Math.max(step, ((distance + step - 1) / step) * step);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (isColumnWater(x + dx * d, z + dz * d)) return true;
        }
        return false;
    }
}
