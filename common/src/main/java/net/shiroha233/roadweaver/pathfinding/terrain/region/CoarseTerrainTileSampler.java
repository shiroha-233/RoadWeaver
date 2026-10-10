package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.map.tile.render.HeightShader;
import net.shiroha233.roadweaver.map.tile.render.TerrainTilePalette;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchRequest;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchSampler;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchSamplers;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

/**
 * 单个粗采样地形瓦片采样器。
 */
public final class CoarseTerrainTileSampler {
    private CoarseTerrainTileSampler() {}

    public static CoarseTerrainTile sample(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) {
            return null;
        }

        int sampleWidth = key.sampleWidth();
        int sampleHeight = key.sampleHeight();
        int size = Math.multiplyExact(sampleWidth, sampleHeight);
        short[] heights = new short[size];
        short[] oceanFloors = new short[size];
        byte[] flags = new byte[size];
        int[] terrainArgb = new int[size];

        int haloSamples = Math.max(1, ceilDiv(RoadConstants.COARSE_TERRAIN_TILE_HALO_BLOCKS, key.step()));
        int expandedWidth = sampleWidth + haloSamples * 2;
        int expandedHeight = sampleHeight + haloSamples * 2;
        byte[] expandedFlags = new byte[Math.multiplyExact(expandedWidth, expandedHeight)];

        int seaLevel = level.getSeaLevel();
        CoarseHeightBatchRequest heightRequest = new CoarseHeightBatchRequest(
                key.minBlockX() - haloSamples * key.step(),
                key.minBlockZ() - haloSamples * key.step(),
                key.step(),
                expandedWidth,
                expandedHeight);
        TerrainSamplingCache terrainCache = new TerrainSamplingCache();
        try (CoarseHeightBatchSampler heightSampler = CoarseHeightBatchSamplers.create(level, heightRequest)) {
            int[] expandedHeights = heightSampler.sampleHeights(heightRequest);
            if (expandedHeights == null) return null;

            for (int ez = 0; ez < expandedHeight; ez++) {
                if (Thread.currentThread().isInterrupted()) return null;
                int sampleZ = key.minBlockZ() + (ez - haloSamples) * key.step();
                for (int ex = 0; ex < expandedWidth; ex++) {
                    int sampleX = key.minBlockX() + (ex - haloSamples) * key.step();
                    int height = expandedHeights[ez * expandedWidth + ex];
                    int oceanFloor = height;
                    Holder<Biome> biome = terrainCache.getBiome(level, sampleX, sampleZ);
                    boolean waterBiome = isWaterBiome(biome);
                    boolean columnWater = waterBiome && oceanFloor < seaLevel;
                    byte rawFlags = CoarseTerrainTile.flags(columnWater, waterBiome, false);
                    expandedFlags[ez * expandedWidth + ex] = rawFlags;

                    int tx = ex - haloSamples;
                    int tz = ez - haloSamples;
                    if (tx >= 0 && tx < sampleWidth && tz >= 0 && tz < sampleHeight) {
                        int index = tz * sampleWidth + tx;
                        heights[index] = toShort(height);
                        oceanFloors[index] = toShort(oceanFloor);
                        flags[index] = rawFlags;
                        terrainArgb[index] = TerrainTilePalette.colorFor(
                                biome,
                                height,
                                seaLevel,
                                oceanFloor,
                                columnWater,
                                false);
                    }
                }
            }

            markNearWater(flags, sampleWidth, sampleHeight, expandedFlags, expandedWidth, haloSamples);
            refreshTerrainColors(level, key, seaLevel, heights, oceanFloors, flags, terrainArgb, terrainCache, sampleWidth, sampleHeight);
            return new CoarseTerrainTile(key, seaLevel, sampleWidth, sampleHeight, heights, oceanFloors, flags, terrainArgb);
        } finally {
            terrainCache.clear();
        }
    }

    private static void markNearWater(byte[] flags,
                                      int sampleWidth,
                                      int sampleHeight,
                                      byte[] expandedFlags,
                                      int expandedWidth,
                                      int haloSamples) {
        for (int z = 0; z < sampleHeight; z++) {
            if (Thread.currentThread().isInterrupted()) return;
            for (int x = 0; x < sampleWidth; x++) {
                int index = z * sampleWidth + x;
                if (hasColumnWater(flags[index]) || hasColumnWaterNearby(expandedFlags, expandedWidth, x + haloSamples, z + haloSamples, haloSamples)) {
                    flags[index] = CoarseTerrainTile.withNearWater(flags[index]);
                }
            }
        }
    }

    private static void refreshTerrainColors(ServerLevel level,
                                             CoarseTerrainTileKey key,
                                             int seaLevel,
                                             short[] heights,
                                             short[] oceanFloors,
                                             byte[] flags,
                                             int[] terrainArgb,
                                             TerrainSamplingCache terrainCache,
                                             int sampleWidth,
                                             int sampleHeight) {
        int step = key.step();
        for (int z = 0; z < sampleHeight; z++) {
            if (Thread.currentThread().isInterrupted()) return;
            int sampleZ = key.blockZAt(z);
            for (int x = 0; x < sampleWidth; x++) {
                int sampleX = key.blockXAt(x);
                int index = z * sampleWidth + x;
                Holder<Biome> biome = terrainCache.getBiome(level, sampleX, sampleZ);
                boolean columnWater = hasColumnWater(flags[index]);
                boolean nearWater = (flags[index] & 4) != 0;
                int baseColor = TerrainTilePalette.colorFor(biome, heights[index], seaLevel, oceanFloors[index], columnWater, nearWater);

                double shade = 1.0;
                if (x > 0 && x < sampleWidth - 1 && z > 0 && z < sampleHeight - 1) {
                    // 展开 3x3 邻域走无分配重载，避免逐采样点构造 int[3][3]
                    int upBase = (z - 1) * sampleWidth;
                    int downBase = (z + 1) * sampleWidth;
                    shade = HeightShader.computeShade(
                            heights[upBase + x - 1], heights[upBase + x], heights[upBase + x + 1],
                            heights[index - 1], heights[index], heights[index + 1],
                            heights[downBase + x - 1], heights[downBase + x], heights[downBase + x + 1]);
                } else if (x > 0) {
                    shade = HeightShader.simpleShade(heights[index], heights[index - 1], step);
                }

                terrainArgb[index] = HeightShader.multiplyRgb(baseColor, shade);
            }
        }
    }

    private static boolean hasColumnWaterNearby(byte[] expandedFlags,
                                                int expandedWidth,
                                                int centerX,
                                                int centerZ,
                                                int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            int z = centerZ + dz;
            for (int dx = -radius; dx <= radius; dx++) {
                int x = centerX + dx;
                if (hasColumnWater(expandedFlags[z * expandedWidth + x])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasColumnWater(byte flags) {
        return (flags & 1) != 0;
    }

    private static boolean isWaterBiome(Holder<Biome> biome) {
        return biome != null
                && (biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN));
    }

    private static short toShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }
}
