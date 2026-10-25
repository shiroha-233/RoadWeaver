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

            WorldGenerationContext worldGenContext = new WorldGenerationContext(level.getChunkSource().getGenerator(), level);
            int startHeight = structure.startHeight().sample(random, worldGenContext);

            Heightmap.Types heightmap = structure.projectStartToHeightmap();
            boolean useBaseHeight = true;

            int[] ground = footprintHeights(placeX, placeZ, sizeHint, level, cache, terrain, heightmap, useBaseHeight);
            int baseHeight = ground[ground.length / 2];

            int placeY = baseHeight + startHeight;

            BlockPos center = new BlockPos(placeX, placeY, placeZ);

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

            BlockPos anchor = new BlockPos(placeX, placeY, placeZ);
            ChunkPos chunkPos = new ChunkPos(anchor);

            long chunkKey = chunkPos.toLong();
            if (placedChunks.contains(chunkKey) ||
                    !structure.skipTerrainCheck() &&
                    !checkTerrainConditions(center, sizeHint, cache, terrain, level, heightmap, useBaseHeight)) {
                LOGGER.debug("Structure {} at {} failed the terrain check", entry.id(), anchor);
                continue;
            }

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

        if (hasAlignedTerrainSample(terrain, x, z)
                ? terrain.isColumnWater(x, z)
                : cache.isColumnWater(level, x, z)) {
            return false;
        }

        int[] ground = footprintHeights(x, z, size, level, cache, terrain, heightmap, useBaseHeight);
        return ground[ground.length - 1] - ground[0] <= RoadConstants.MAX_STRUCTURE_SLOPE;
    }
    
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
