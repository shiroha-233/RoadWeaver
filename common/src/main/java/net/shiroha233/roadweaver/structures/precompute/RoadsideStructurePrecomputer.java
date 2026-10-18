/* 文件职责：基于道路与共享精确地形预计算路边结构位置。 */
package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;

import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.structures.data.BiomeCategory;
import net.shiroha233.roadweaver.structures.data.StructureScale;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry.RoadsideStructureEntry;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路边结构预计算器
 */
public final class RoadsideStructurePrecomputer {
    private RoadsideStructurePrecomputer() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructurePrecomputer");
    
    public static int precomputeStructures(ServerLevel level,
                                           List<RoadSegmentPlacement> segments,
                                           List<RoadSpan> spans,
                                           int width,
                                           TerrainSamplingCache cache,
                                           PathTerrainField terrain,
                                           RandomSource random,
                                           List<Integer> targetY) {
        if (segments == null || segments.size() < RoadConstants.MIN_ROAD_SEGMENTS_FOR_STRUCTURE) {
            return 0;
        }
        
        ModConfig cfg = ConfigService.get();

        if (!cfg.roadsideStructure().enabled()) {
            return 0;
        }
        
        int maxStructures = cfg.roadsideStructure().maxStructuresPerRoad();
        if (maxStructures <= 0) {
            return 0;
        }
        
        List<RoadsideStructureEntry> allStructures = RoadsideStructureRegistry.getAll(level);
        if (allStructures.isEmpty()) {
            return 0;
        }
        
        Set<Integer> bridgeIndices = new HashSet<>();
        if (spans != null) {
            for (RoadSpan span : spans) {
                if (span.type() == SpanType.BRIDGE) {
                    for (int i = 0; i < segments.size(); i++) {
                        BlockPos pos = segments.get(i).middlePos();
                        if (isInSpan(pos, span)) {
                            bridgeIndices.add(i);
                        }
                    }
                }
            }
        }
        
        int roadLength = segments.size();
        int checkInterval = Math.max(1, roadLength / (maxStructures + 1));
        
        Set<Long> placedChunks = new HashSet<>();
        int placedCount = 0;
        
        for (int i = checkInterval; i < roadLength - checkInterval && placedCount < maxStructures; i += checkInterval) {
            if (bridgeIndices.contains(i)) {
                continue;
            }
            
            BlockPos middle = segments.get(i).middlePos();
            
            int windowSize = RoadConstants.STRUCTURE_PLACEMENT_WINDOW_SIZE;
            BlockPos prev = segments.get(Math.max(0, i - windowSize)).middlePos();
            BlockPos next = segments.get(Math.min(roadLength - 1, i + windowSize)).middlePos();
            
            double dirX = next.getX() - prev.getX();
            double dirZ = next.getZ() - prev.getZ();
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len < 0.01) continue;
            dirX /= len;
            dirZ /= len;
            
            Holder<Biome> biomeHolder = cache.getBiome(level, middle.getX(), middle.getZ());
            BiomeCategory category = BiomeCategory.fromBiome(biomeHolder);
            
            RoadsideStructureEntry entry = selectStructure(allStructures, category, roadLength, random);
            if (entry == null) {
                continue;
            }
            
            RoadsideStructure structure = entry.structure();
            Vec3i sizeHint = structure.sizeHint();
            
            boolean leftSide = random.nextBoolean();
            int offset = getOffsetForScale(structure.scale(), cfg);

            Rotation rotation = calculateRotation(dirX, dirZ, leftSide, structure.faceRoad());
            
            double perpX = leftSide ? -dirZ : dirZ;
            double perpZ = leftSide ? dirX : -dirX;

            int sizeX = sizeHint.getX();
            int sizeZ = sizeHint.getZ();
            
            double halfExtentInPerpDir;
            switch (rotation) {
                case NONE, CLOCKWISE_180 -> halfExtentInPerpDir = (Math.abs(perpX) * sizeX + Math.abs(perpZ) * sizeZ) / 2.0;
                case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> halfExtentInPerpDir = (Math.abs(perpX) * sizeZ + Math.abs(perpZ) * sizeX) / 2.0;
                default -> halfExtentInPerpDir = Math.max(sizeX, sizeZ) / 2.0;
            }
            
            double centerOffset = offset + halfExtentInPerpDir;
            
            int placeX = middle.getX() + (int) Math.round(perpX * centerOffset);
            int placeZ = middle.getZ() + (int) Math.round(perpZ * centerOffset);

            // Get structure-defined start height offset mapped on heightmap
            WorldGenerationContext worldGenContext = new WorldGenerationContext(level.getChunkSource().getGenerator(), level);
            int startHeight = structure.startHeight().sample(random, worldGenContext);

            Heightmap.Types heightmap = structure.projectStartToHeightmap();
            // Always the generator's own surface height: the fast sampler reads the preliminary surface, which is several
            // blocks off on slopes and in cherry groves, and a structure set that low ends up in a pit.
            boolean useBaseHeight = true;

            // Ground under the structure itself. The road's target height for this segment belongs to the road;
            // the structure sits beside it, and on a slope the ground there can be several blocks lower or higher.
            // The median over the footprint, so one low or high corner on a slope neither buries nor lifts the whole
            // structure.
            int[] ground = footprintHeights(placeX, placeZ, sizeHint, level, cache, terrain, heightmap, useBaseHeight);
            int baseHeight = ground[ground.length / 2];

            int placeY = baseHeight + startHeight;

            // The center of the structure. Used by terrain check.
            BlockPos center = new BlockPos(placeX, placeY, placeZ);

            // Calculate the structure anchor after rotation
            switch (rotation) {
                case NONE -> {
                    placeX -= sizeX / 2;
                    placeZ -= sizeZ / 2;
                }
                case CLOCKWISE_90 -> {
                    placeX += sizeZ / 2;
                    placeZ -= sizeX / 2;
                }
                case CLOCKWISE_180 -> {
                    placeX += sizeX / 2;
                    placeZ += sizeZ / 2;
                }
                case COUNTERCLOCKWISE_90 -> {
                    placeX -= sizeZ / 2;
                    placeZ += sizeX / 2;
                }
            }

            // The anchor of the structure placement. The placeX and placeZ value has been transformed by the
            // switch-case above.
            BlockPos anchor = new BlockPos(placeX, placeY, placeZ);
            ChunkPos chunkPos = new ChunkPos(anchor);

            long chunkKey = chunkPos.toLong();
            if (placedChunks.contains(chunkKey) ||
                    !structure.skipTerrainCheck() &&
                    !checkTerrainConditions(center, sizeHint, cache, terrain, level, heightmap, useBaseHeight)) {
                LOGGER.debug("Structure {} at {} failed the terrain check", entry.id(), anchor);
                continue;
            }

            // Push the precalculated data to pending queue
            PendingStructureStorage.addPendingStructure(
                level,
                entry.id(),
                anchor,
                rotation,
                sizeHint.getX(),
                sizeHint.getY(),
                sizeHint.getZ()
            );
            
            placedChunks.add(chunkKey);
            placedCount++;
            
            LOGGER.debug("Precomputed structure {} at {} for chunk [{}, {}]", entry.id(), anchor, chunkPos.x, chunkPos.z);
        }
        
        return placedCount;
    }
    
    private static boolean isInSpan(BlockPos pos, RoadSpan span) {
        int minX = Math.min(span.start().getX(), span.end().getX());
        int maxX = Math.max(span.start().getX(), span.end().getX());
        int minZ = Math.min(span.start().getZ(), span.end().getZ());
        int maxZ = Math.max(span.start().getZ(), span.end().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    private static RoadsideStructureEntry selectStructure(List<RoadsideStructureEntry> structures,
                                                          BiomeCategory category,
                                                          int roadLength,
                                                          RandomSource random) {
        List<RoadsideStructureEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideStructureEntry entry : structures) {
            RoadsideStructure structure = entry.structure();
            
            if (!structure.placementRule().isBiomeAllowed(category)) {
                continue;
            }
            
            if (roadLength < structure.placementRule().minRoadLength()) {
                continue;
            }
            
            candidates.add(entry);
            totalWeight += structure.weight();
        }
        
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RoadsideStructureEntry entry : candidates) {
            cumulative += entry.structure().weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        
        return candidates.get(candidates.size() - 1);
    }

    /**
     * Check if the terrain on a certain structure's center position meets the following condition:
     *
     * <ul>
     *     <li>The center's column does NOT have water body (river, ocean, etc.) on the surface.</li>
     *     <li>The highest and lowest ground under the footprint (centre, edge middles and corners) differ by no more
     *     than {@link RoadConstants#MAX_STRUCTURE_SLOPE} blocks.</li>
     * </ul>
     *
     * @param center The center of the structure.
     * @param size The size of the structure, represented by {@link Vec3i}.
     * @param cache The {@link TerrainSamplingCache} object storing the terrain cache. Used in {@link #getHeight}.
     * @param level The {@link ServerLevel} object representing the world.
     * @param heightmap The heightmap type that needs to be sampled. Used in {@link #getHeight}.
     * @param useBaseHeight Whether to use {@link TerrainSamplingCache#height} or {@link ChunkGenerator#getBaseHeight}
     *                      to calculate the terrain height. Used in {@link #getHeight}.
     * @return {@code true} if the terrain at the structure's center position meets the condition, {@code false}
     * otherwise.
     */
    private static boolean checkTerrainConditions(
        BlockPos center,
        Vec3i size,
        TerrainSamplingCache cache,
        PathTerrainField terrain,
        ServerLevel level,
        Heightmap.Types heightmap,
        boolean useBaseHeight
    ) {
        int x = center.getX();
        int z = center.getZ();

        // Check if the center's column has water body on the surface
        if (hasAlignedTerrainSample(terrain, x, z)
                ? terrain.isColumnWater(x, z)
                : cache.isColumnWater(level, x, z)) {
            return false;
        }

        // The ground under the whole footprint, corners included, may differ by at most MAX_STRUCTURE_SLOPE blocks.
        int[] ground = footprintHeights(x, z, size, level, cache, terrain, heightmap, useBaseHeight);
        return ground[ground.length - 1] - ground[0] <= RoadConstants.MAX_STRUCTURE_SLOPE;
    }
    
    /**
     * Ground heights at the centre, edge middles and corners of a footprint, sorted. The footprint is taken as a square
     * of the larger side, so it covers the structure in any rotation.
     */
    private static int[] footprintHeights(int x, int z, Vec3i size, ServerLevel level, TerrainSamplingCache cache,
                                          PathTerrainField terrain, Heightmap.Types heightmap, boolean useBaseHeight) {
        int half = Math.max(size.getX(), size.getZ()) / 2;
        int[] heights = new int[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                heights[i++] = getHeight(x + dx * half, z + dz * half, level, cache, terrain, heightmap, useBaseHeight);
            }
        }
        Arrays.sort(heights);
        return heights;
    }

    private static int getOffsetForScale(StructureScale scale, ModConfig cfg) {
        return switch (scale) {
            case SMALL -> cfg.roadsideStructure().smallStructureOffset();
            case MEDIUM -> cfg.roadsideStructure().mediumStructureOffset();
            case LARGE -> cfg.roadsideStructure().largeStructureOffset();
        };
    }

    /**
     * Calculate the terrain height of a specific coordinate.
     * <p>
     * When {@code useBaseHeight} is set to {@code false}, this method will use fast {@link TerrainSamplingCache#height}
     * to sample the height on the specific coordinate, which is inaccurate and can only probe the surface height, thus
     * {@code heightmap} is ignored in this case.
     * <p>
     * On the other hand, when {@code useBaseHeight} is set to true, this method will use the more accurate
     * {@link ChunkGenerator#getBaseHeight} to sample the height, which can specify the heightmap type that needs to be
     * sampled, but much slower compared to {@link TerrainSamplingCache#height}. In this case, parameter {@code cache}
     * is ignored since it is no longer engaged in height calculation.
     *
     * @param x The X value of the coordinate being sampled.
     * @param z The Z value of the coordinate being sampled.
     * @param level The {@link ServerLevel} object representing the world.
     * @param cache The {@link TerrainSamplingCache} object storing the terrain cache. Used when {@code useBaseHeight}
     *              is {@code false}.
     * @param heightmap The heightmap type that needs to be sampled. Used when {@code useBaseHeight} is {@code true}.
     * @param useBaseHeight Whether to use {@link TerrainSamplingCache#height} or {@link ChunkGenerator#getBaseHeight}
     *                      to calculate the terrain height.
     * @return The calculated height of the coordinate.
     */
    private static int getHeight(int x,
                                 int z,
                                 ServerLevel level,
                                 TerrainSamplingCache cache,
                                 PathTerrainField terrain,
                                 Heightmap.Types heightmap,
                                 boolean useBaseHeight) {
        if (!useBaseHeight) {
            return hasAlignedTerrainSample(terrain, x, z) ? terrain.height(x, z) : cache.height(level, x, z);
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(x, z, heightmap, level, chunkSource.getGeneratorState().randomState());
    }

    static boolean hasAlignedTerrainSample(PathTerrainField terrain, int x, int z) {
        return terrain instanceof AccurateTerrainRegion region && region.bounds().containsSample(x, z);
    }
    
    private static Rotation calculateRotation(double dirX, double dirZ, boolean leftSide, boolean faceRoad) {
        if (!faceRoad) {
            return Rotation.NONE;
        }
        
        double absX = Math.abs(dirX);
        double absZ = Math.abs(dirZ);
        
        if (absX > absZ) {
            if (leftSide) {
                return dirX > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
            } else {
                return dirX > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            }
        } else {
            if (leftSide) {
                return dirZ > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            } else {
                return dirZ > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            }
        }
    }
}
