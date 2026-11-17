package net.shiroha233.roadweaver.terrain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 快速高度采样器。
 *
 * <p>26.3 把 {@code initialDensityWithoutJaggedness} 换成了路由中的 {@code chunkSurfaceLevel}，
 * 并且密度函数不再有 {@code compute(FunctionContext)}，单点求值走
 * {@link RandomState#sampleBlockValueUncached}（内部用 {@code SamplerContext.EMPTY_UNCACHED}）。
 * {@link NoiseSettings} 也不再暴露竖直格子高度，因此扫描步长固定为历史值 8。</p>
 */
public final class FastHeightSampler {

    private static final double DENSITY_THRESHOLD = 0.390625D;
    private static final int SCAN_STEP = 8;

    private final RandomState randomState;
    private final ChunkGenerator generator;
    private final LevelHeightAccessor heightAccessor;
    private final DensityFunction chunkSurfaceLevel;
    private final int minY;
    private final int maxY;
    private ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();

    private FastHeightSampler(RandomState randomState,
                              ChunkGenerator generator,
                              LevelHeightAccessor heightAccessor,
                              DensityFunction chunkSurfaceLevel,
                              NoiseSettings settings) {
        this.randomState = randomState;
        this.generator = generator;
        this.heightAccessor = heightAccessor;
        this.chunkSurfaceLevel = chunkSurfaceLevel;
        this.minY = settings.minY();
        this.maxY = this.minY + settings.height();
    }

    public static FastHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.getGeneratorState().randomState();
        ChunkGenerator generator = chunkSource.getGenerator();
        DensityFunction chunkSurfaceLevel = null;
        NoiseSettings settings;
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            var generatorSettings = noiseGenerator.generatorSettings().value();
            NoiseRouter router = generatorSettings.noiseRouter();
            chunkSurfaceLevel = router.chunkSurfaceLevel();
            settings = generatorSettings.noiseSettings().clampToHeightAccessor(level);
        } else {
            settings = NoiseSettings.create(level.getMinY(), level.getHeight());
        }
        return new FastHeightSampler(randomState, generator, level, chunkSurfaceLevel, settings);
    }

    public int sampleHeight(int x, int z) {
        int alignedX = (x >> 2) << 2;
        int alignedZ = (z >> 2) << 2;
        long key = packXZ(alignedX, alignedZ);
        Integer cached = heightCache.get(key);
        if (cached != null) return cached;

        int height = computeHeight(alignedX, alignedZ);
        heightCache.put(key, height);
        return height;
    }

    public void prewarmRegion(int minX, int minZ, int maxX, int maxZ, int step) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                sampleHeight((x >> 2) << 2, (z >> 2) << 2);
            }
        }
    }

    private int computeHeight(int x, int z) {
        if (chunkSurfaceLevel == null) {
            return generator.getBaseHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
        }
        for (int y = maxY; y >= minY; y -= SCAN_STEP) {
            float density = randomState.sampleBlockValueUncached(chunkSurfaceLevel, x, y, z);
            if (density > DENSITY_THRESHOLD) return y;
        }
        return minY;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * 释放缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    public void clearCache() {
        heightCache = new ConcurrentHashMap<>();
    }

    public int getCacheSize() {
        return heightCache.size();
    }
}
