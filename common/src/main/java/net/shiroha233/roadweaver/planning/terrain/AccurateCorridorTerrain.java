package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingProgress;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AccurateCorridorTerrain implements PathTerrainField {
    private static final byte FLAG_COLUMN_WATER = 1;
    private static final byte FLAG_WATER_BIOME = 1 << 1;

    private final int seaLevel;
    private final int step;
    private final long[] keys;
    private final int[] slots;
    private final short[] heights;
    private final short[] oceanFloors;
    private final byte[] flags;
    private final Holder<Biome>[] biomes;

    AccurateCorridorTerrain(ServerLevel level, TerrainSamplingCache cache, List<BlockPos> path, int step) {
        this(level.getSeaLevel(), step,
                cache.getAccurateSampler(level).samplePositions(positions(path, step, Math.max(64, step * 2))),
                (x, z) -> cache.getBiome(level, x, z));
    }

    static AccurateCorridorTerrain sample(ServerLevel level, TerrainSamplingCache cache,
                                          List<BlockPos> positions, int step, AccurateSamplingProgress progress) {
        AccurateHeightSampler sampler = cache.getAccurateSampler(level);
        Map<Long, AccurateHeightSample> samples = sampler.samplePositions(positions, progress);
        return new AccurateCorridorTerrain(level.getSeaLevel(), step, samples,
                (x, z) -> cache.getBiome(level, x, z));
    }

    @SuppressWarnings("unchecked")
    AccurateCorridorTerrain(int seaLevel, int step, Map<Long, AccurateHeightSample> samples, BiomeLookup biomeLookup) {
        this.seaLevel = seaLevel;
        this.step = step;
        int size = samples.size();
        this.keys = new long[size];
        this.heights = new short[size];
        this.oceanFloors = new short[size];
        this.flags = new byte[size];
        this.biomes = (Holder<Biome>[]) new Holder<?>[size];
        int capacity = 1;
        while (capacity < Math.max(2L, (long) size * 2)) capacity <<= 1;
        this.slots = new int[capacity];
        int index = 0;
        for (Map.Entry<Long, AccurateHeightSample> entry : samples.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            long key = entry.getKey();
            AccurateHeightSample sample = entry.getValue();
            int x = (int) (key >> 32);
            int z = (int) key;
            Holder<Biome> biome = biomeLookup.get(x, z);
            boolean waterBiome = biome != null && (biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN));
            int floor = sample.oceanFloorWg();
            int surface = sample.worldSurfaceWg();
            keys[index] = key;
            heights[index] = (short) (sample.motionBlockingNoLeaves() > seaLevel + 2
                    ? sample.motionBlockingNoLeaves() : surface);
            oceanFloors[index] = (short) floor;
            flags[index] = (byte) ((waterBiome && floor < seaLevel || floor < surface ? FLAG_COLUMN_WATER : 0)
                    | (waterBiome ? FLAG_WATER_BIOME : 0));
            biomes[index] = biome;
            int slot = hash(key) & (capacity - 1);
            while (slots[slot] != 0) slot = (slot + 1) & (capacity - 1);
            slots[slot] = ++index;
        }
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

    private int index(int x, int z) {
        long key = AccurateHeightSample.key(x, z);
        int mask = slots.length - 1;
        int slot = hash(key) & mask;
        while (slots[slot] != 0) {
            int index = slots[slot] - 1;
            if (keys[index] == key) return index;
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    private static int hash(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        return (int) key;
    }

    @Override public int seaLevel() { return seaLevel; }
    @Override public int step() { return step; }
    @Override public boolean contains(int x, int z) { return index(x, z) >= 0; }
    @Override public boolean hasAccurateSample(int x, int z) { return contains(x, z); }
    @Override public int height(int x, int z) {
        int i = index(x, z);
        return i >= 0 ? heights[i] : seaLevel;
    }
    @Override public int oceanFloor(int x, int z) {
        int i = index(x, z);
        return i >= 0 ? oceanFloors[i] : seaLevel;
    }
    @Override public Holder<Biome> biome(int x, int z) {
        int i = index(x, z);
        return i >= 0 ? biomes[i] : null;
    }
    @Override public boolean isColumnWater(int x, int z) {
        int i = index(x, z);
        return i >= 0 && (flags[i] & FLAG_COLUMN_WATER) != 0;
    }
    @Override public boolean isWaterBiome(int x, int z) {
        int i = index(x, z);
        return i >= 0 && (flags[i] & FLAG_WATER_BIOME) != 0;
    }
    @Override public SampleBundle sampleBundle(int x, int z) {
        int i = index(x, z);
        if (i < 0) return new SampleBundle(seaLevel, seaLevel, false, false, 0);
        int floor = oceanFloors[i];
        return new SampleBundle(heights[i], floor, (flags[i] & FLAG_COLUMN_WATER) != 0,
                (flags[i] & FLAG_WATER_BIOME) != 0, Math.max(0, seaLevel - floor));
    }
    @Override public boolean isNearWater(int x, int z, int distance) {
        int d = Math.max(step, ((distance + step - 1) / step) * step);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (isColumnWater(x + dx * d, z + dz * d)) return true;
        }
        return false;
    }

    @FunctionalInterface
    interface BiomeLookup {
        Holder<Biome> get(int x, int z);
    }
}
