package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.Holder;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.RoadsideVillageConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.structures.data.BiomeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路边村庄预计算器
 */
public final class RoadsideVillagePrecomputer {
    private RoadsideVillagePrecomputer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/RoadsideVillagePrecomputer");
    private static final String MOD_ID = "roadweaver";
    private static final ResourceLocation STRUCTURE_ID = new ResourceLocation(MOD_ID, "roadside_village");

    public static int precomputeVillages(ServerLevel level,
                                         List<RoadSegmentPlacement> segments,
                                         List<RoadSpan> spans,
                                         int width,
                                         TerrainSamplingCache cache,
                                         PathTerrainField terrain,
                                         RandomSource random,
                                         List<Integer> targetY) {
        if (segments == null || targetY == null || segments.size() != targetY.size()) {
            return 0;
        }

        ModConfig cfg = ConfigService.get();
        if (!cfg.roadsideStructure().enabled()) {
            return 0;
        }

        RoadsideVillageConfig villageCfg = cfg.roadsideVillage();
        if (!villageCfg.enabled() || villageCfg.maxVillagesPerRoad() <= 0) {
            return 0;
        }

        if (random.nextDouble() >= villageCfg.spawnChance()) {
            return 0;
        }

        if (segments.size() < villageCfg.minRoadSegments()) {
            return 0;
        }

        Set<Integer> bridgeIndices = collectBridgeIndices(segments, spans);
        List<WindowCandidate> candidates = chooseCandidateWindows(level, segments, targetY, width, cache, terrain, bridgeIndices, villageCfg);
        if (candidates.isEmpty()) {
            return 0;
        }

        int placedCount = 0;
        Set<Long> usedOriginChunks = new HashSet<>();
        List<BoundingBox> acceptedBounds = new ArrayList<>();
        for (WindowCandidate candidate : candidates) {
            if (placedCount >= villageCfg.maxVillagesPerRoad()) {
                break;
            }

            BlockPos center = segments.get(candidate.centerIndex()).middlePos();
            Holder<Biome> biome = cache.getBiome(level, center.getX(), center.getZ());
            ResourceLocation style = styleForBiome(BiomeCategory.fromBiome(biome));
            int minClusterSlots = minClusterSlots(villageCfg);
            int nodeCount = Math.max(randomNodeCount(villageCfg, random), minClusterSlots);
            SlotLayout layout = createSlots(level, segments, targetY, width, cache, terrain, villageCfg, candidate, nodeCount, style, random);
            List<PendingRoadsideVillageSlot> slots = layout.slots();
            if (slots.size() < minClusterSlots) {
                continue;
            }

            BoundingBox bounds = estimateBounds(layout.footprintBounds());
            if (intersectsAny(bounds, acceptedBounds)) {
                continue;
            }

            BlockPos villageCenter = new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2
            );
            ChunkPos originChunk = new ChunkPos(villageCenter);
            if (!usedOriginChunks.add(originChunk.toLong())) {
                continue;
            }

            ResourceLocation placementId = new ResourceLocation(
                MOD_ID,
                "roadside_village/" + originChunk.x + "_" + originChunk.z + "_" + candidate.startIndex()
            );

            PendingRoadsideVillage village = new PendingRoadsideVillage(
                placementId,
                STRUCTURE_ID,
                originChunk,
                candidate.startIndex(),
                candidate.endIndex(),
                style,
                random.nextLong(),
                bounds,
                List.copyOf(slots)
            );

            if (PendingRoadsideVillageStorage.addPendingVillage(level, village)) {
                acceptedBounds.add(bounds);
                placedCount++;
                LOGGER.debug("Precomputed roadside village {} style={} slots={} window=[{}, {}] score={} chunk=[{}, {}]",
                    placementId, style, slots.size(), candidate.startIndex(), candidate.endIndex(), candidate.score(), originChunk.x, originChunk.z);
            }
        }

        return placedCount;
    }

    private static Set<Integer> collectBridgeIndices(List<RoadSegmentPlacement> segments, List<RoadSpan> spans) {
        Set<Integer> result = new HashSet<>();
        if (spans == null || spans.isEmpty()) {
            return result;
        }

        for (RoadSpan span : spans) {
            if (span.type() != SpanType.BRIDGE) {
                continue;
            }
            for (int i = 0; i < segments.size(); i++) {
                if (isInSpan(segments.get(i).middlePos(), span)) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    private static boolean isInSpan(BlockPos pos, RoadSpan span) {
        int minX = Math.min(span.start().getX(), span.end().getX());
        int maxX = Math.max(span.start().getX(), span.end().getX());
        int minZ = Math.min(span.start().getZ(), span.end().getZ());
        int maxZ = Math.max(span.start().getZ(), span.end().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static List<WindowCandidate> chooseCandidateWindows(ServerLevel level,
                                                                List<RoadSegmentPlacement> segments,
                                                                List<Integer> targetY,
                                                                int width,
                                                                TerrainSamplingCache cache,
                                                                PathTerrainField terrain,
                                                                Set<Integer> bridgeIndices,
                                                                RoadsideVillageConfig cfg) {
        int window = Math.min(cfg.windowSegments(), segments.size());
        if (window < 3) {
            return List.of();
        }

        List<WindowCandidate> candidates = new ArrayList<>();
        int stride = Math.max(1, window / 8);
        for (int start = 0; start + window <= segments.size(); start += stride) {
            int end = start + window - 1;
            WindowCandidate candidate = evaluateWindow(level, segments, targetY, width, cache, terrain, bridgeIndices, cfg, start, end);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        return candidates;
    }

    private static WindowCandidate evaluateWindow(ServerLevel level,
                                                  List<RoadSegmentPlacement> segments,
                                                  List<Integer> targetY,
                                                  int width,
                                                  TerrainSamplingCache cache,
                                                  PathTerrainField terrain,
                                                  Set<Integer> bridgeIndices,
                                                  RoadsideVillageConfig cfg,
                                                  int start,
                                                  int end) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            if (bridgeIndices.contains(i)) {
                return null;
            }
            int y = targetY.get(i);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int roadHeightRange = maxY - minY;
        int extremeRoadRange = Math.max(8, Math.max(cfg.maxHeightDiff() * 4, cfg.maxLocalSlope() * 6));
        if (roadHeightRange > extremeRoadRange) {
            return null;
        }

        double curve = curveAngle(segments.get(start).middlePos(), segments.get((start + end) / 2).middlePos(), segments.get(end).middlePos());
        if (curve < cfg.minCurveAngle() || curve > cfg.maxCurveAngle()) {
            return null;
        }

        WindowFootprintScore footprintScore = scoreWindowFootprints(level, segments, targetY, width, cache, terrain, cfg, start, end);
        int minUsable = Math.max(1, Math.min(minClusterSlots(cfg), Math.max(1, footprintScore.totalCount() / 5)));
        if (footprintScore.usableCount() < minUsable) {
            return null;
        }

        double roadFlatnessScore = 1.0 / (1.0 + roadHeightRange);
        double curveScore = 1.0 / (1.0 + Math.abs(curve - ((cfg.minCurveAngle() + cfg.maxCurveAngle()) * 0.5)));
        double terrainScore = footprintScore.averageScore() + footprintScore.usableCount() * 0.35;
        return new WindowCandidate(start, end, (start + end) / 2, roadFlatnessScore + curveScore + terrainScore);
    }

    private static WindowFootprintScore scoreWindowFootprints(ServerLevel level,
                                                              List<RoadSegmentPlacement> segments,
                                                              List<Integer> targetY,
                                                              int width,
                                                              TerrainSamplingCache cache,
                                                              PathTerrainField terrain,
                                                              RoadsideVillageConfig cfg,
                                                              int start,
                                                              int end) {
        int probeCount = Math.max(3, Math.min(8, cfg.targetNodeCountMin()));
        int windowLength = end - start + 1;
        int radius = Math.max(8, Math.min(16, cfg.roadBufferBlocks() + 2));
        int offset = roadsideAnchorOffset(width, cfg);
        int usableCount = 0;
        int totalCount = 0;
        double scoreSum = 0.0;

        for (int n = 0; n < probeCount; n++) {
            int index = start + Math.min(windowLength - 1, (int) Math.floor((n + 0.5) * windowLength / probeCount));
            for (PendingRoadsideVillageSlot.Side side : PendingRoadsideVillageSlot.Side.values()) {
                SlotProbe probe = createSlotProbe(segments, index, side, offset, radius);
                if (probe == null) {
                    continue;
                }

                VillageFootprintScore score = scoreFootprint(level, cache, terrain, cfg, probe.footprintX(), probe.footprintZ(), targetY.get(index), radius);
                totalCount++;
                scoreSum += score.score();
                if (score.usable()) {
                    usableCount++;
                }
            }
        }

        if (totalCount == 0) {
            return new WindowFootprintScore(0, 0, 0.0);
        }
        return new WindowFootprintScore(usableCount, totalCount, scoreSum / totalCount);
    }

    private static double curveAngle(BlockPos start, BlockPos center, BlockPos end) {
        double ax = center.getX() - start.getX();
        double az = center.getZ() - start.getZ();
        double bx = end.getX() - center.getX();
        double bz = end.getZ() - center.getZ();
        double al = Math.sqrt(ax * ax + az * az);
        double bl = Math.sqrt(bx * bx + bz * bz);
        if (al < 0.01 || bl < 0.01) {
            return 0.0;
        }
        double dot = (ax * bx + az * bz) / (al * bl);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private static int randomNodeCount(RoadsideVillageConfig cfg, RandomSource random) {
        int min = cfg.targetNodeCountMin();
        int max = Math.max(min, cfg.targetNodeCountMax());
        return min + random.nextInt(max - min + 1);
    }

    private static int minClusterSlots(RoadsideVillageConfig cfg) {
        return 1;
    }

    private static SlotLayout createSlots(ServerLevel level,
                                          List<RoadSegmentPlacement> segments,
                                          List<Integer> targetY,
                                          int width,
                                          TerrainSamplingCache cache,
                                          PathTerrainField terrain,
                                          RoadsideVillageConfig cfg,
                                          WindowCandidate window,
                                          int nodeCount,
                                          ResourceLocation style,
                                          RandomSource random) {
        List<PendingRoadsideVillageSlot> result = new ArrayList<>();
        List<BoundingBox> occupied = new ArrayList<>();
        int offset = roadsideAnchorOffset(width, cfg);
        int gapInterval = cfg.buildingGapInterval();
        int maxStep = cfg.maxStepHeight();

        List<PendingRoadsideVillageSlot> leftSlots = buildStreetSide(
            level, segments, targetY, cache, terrain, cfg,
            window.startIndex(), window.endIndex(),
            PendingRoadsideVillageSlot.Side.LEFT, offset, gapInterval, maxStep,
            style, random, occupied
        );
        result.addAll(leftSlots);

        List<PendingRoadsideVillageSlot> rightSlots = buildStreetSide(
            level, segments, targetY, cache, terrain, cfg,
            window.startIndex(), window.endIndex(),
            PendingRoadsideVillageSlot.Side.RIGHT, offset, gapInterval, maxStep,
            style, random, occupied
        );
        result.addAll(rightSlots);

        for (int i = 0; i < result.size(); i++) {
            PendingRoadsideVillageSlot slot = result.get(i);
            PendingRoadsideVillageSlot.SlotKind kind = slotKind(i, result.size(), style, random);
            PendingRoadsideVillageSlot newSlot = new PendingRoadsideVillageSlot(
                slot.roadIndex(), slot.side(), slot.anchor(), slot.outward(), kind, slot.footprintRadius()
            );
            result.set(i, newSlot);
        }

        List<BoundingBox> footprints = new ArrayList<>();
        for (PendingRoadsideVillageSlot slot : result) {
            footprints.add(slotBounds(slot.anchor(), slot.footprintRadius()));
        }
        return new SlotLayout(result, footprints);
    }

    private static List<PendingRoadsideVillageSlot> buildStreetSide(ServerLevel level,
                                                                      List<RoadSegmentPlacement> segments,
                                                                      List<Integer> targetY,
                                                                      TerrainSamplingCache cache,
                                                                      PathTerrainField terrain,
                                                                      RoadsideVillageConfig cfg,
                                                                      int startIdx,
                                                                      int endIdx,
                                                                      PendingRoadsideVillageSlot.Side side,
                                                                      int offset,
                                                                      int gapInterval,
                                                                      int maxStepHeight,
                                                                      ResourceLocation style,
                                                                      RandomSource random,
                                                                      List<BoundingBox> occupied) {
        List<PendingRoadsideVillageSlot> slots = new ArrayList<>();
        int lastHeight = Integer.MIN_VALUE;
        int buildingsSinceGap = 0;
        int idx = startIdx;

        int phaseOffset = side == PendingRoadsideVillageSlot.Side.LEFT ? 0 : gapInterval / 2;
        int footprintRadius = 8;
        int buildingStride = 2;

        while (idx <= endIdx) {
            int adjustedIdx = idx + phaseOffset;
            if (adjustedIdx > endIdx) break;

            SlotProbe probe = createSlotProbe(segments, adjustedIdx, side, offset, footprintRadius);
            if (probe == null) {
                idx++;
                continue;
            }

            VillageFootprintScore score = scoreFootprint(level, cache, terrain, cfg, probe.footprintX(), probe.footprintZ(), targetY.get(adjustedIdx), footprintRadius);
            if (!score.usable()) {
                idx++;
                continue;
            }

            int currentHeight = (int) Math.round(score.meanHeight());
            if (!isHeightStepAcceptable(lastHeight, currentHeight, maxStepHeight)) {
                idx++;
                continue;
            }

            BlockPos anchor = new BlockPos(probe.anchorX(), currentHeight, probe.anchorZ());
            BoundingBox footprint = slotBounds(anchor, footprintRadius);
            if (intersectsAny(footprint, occupied, 1)) {
                idx++;
                continue;
            }

            occupied.add(footprint);
            PendingRoadsideVillageSlot slot = new PendingRoadsideVillageSlot(
                adjustedIdx, side, anchor, probe.outward(),
                PendingRoadsideVillageSlot.SlotKind.HOUSE, footprintRadius
            );
            slots.add(slot);
            lastHeight = currentHeight;
            buildingsSinceGap++;

            idx += buildingStride;

            if (buildingsSinceGap >= gapInterval) {
                buildingsSinceGap = 0;
                idx += gapInterval;
            }
        }

        return slots;
    }

    private static boolean isHeightStepAcceptable(int lastHeight, int currentHeight, int maxStepHeight) {
        if (lastHeight == Integer.MIN_VALUE) return true;
        return Math.abs(currentHeight - lastHeight) <= maxStepHeight;
    }

    private static int footprintRadius(PendingRoadsideVillageSlot.SlotKind kind) {
        return switch (kind) {
            case HOUSE -> 12;
            case DECOR, VILLAGER -> 6;
        };
    }

    private static BoundingBox slotBounds(BlockPos anchor, int radius) {
        return new BoundingBox(
            anchor.getX() - radius,
            anchor.getY() - 4,
            anchor.getZ() - radius,
            anchor.getX() + radius,
            anchor.getY() + 24,
            anchor.getZ() + radius
        );
    }

    private static PendingRoadsideVillageSlot.SlotKind slotKind(int ordinal, int total, ResourceLocation style, RandomSource random) {
        if (ordinal == total - 1) {
            return PendingRoadsideVillageSlot.SlotKind.VILLAGER;
        }
        if (ordinal % 4 == 3) {
            return PendingRoadsideVillageSlot.SlotKind.DECOR;
        }
        return PendingRoadsideVillageSlot.SlotKind.HOUSE;
    }

    private static SlotProbe createSlotProbe(List<RoadSegmentPlacement> segments,
                                             int index,
                                             PendingRoadsideVillageSlot.Side side,
                                             int anchorOffset,
                                             int footprintRadius) {
        BlockPos middle = segments.get(index).middlePos();
        BlockPos prev = segments.get(Math.max(0, index - 8)).middlePos();
        BlockPos next = segments.get(Math.min(segments.size() - 1, index + 8)).middlePos();

        double dirX = next.getX() - prev.getX();
        double dirZ = next.getZ() - prev.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.01) {
            return null;
        }
        dirX /= len;
        dirZ /= len;

        double perpX = side == PendingRoadsideVillageSlot.Side.LEFT ? -dirZ : dirZ;
        double perpZ = side == PendingRoadsideVillageSlot.Side.LEFT ? dirX : -dirX;
        int anchorX = middle.getX() + (int) Math.round(perpX * anchorOffset);
        int anchorZ = middle.getZ() + (int) Math.round(perpZ * anchorOffset);
        int footprintOffset = anchorOffset + Math.max(2, footprintRadius / 2);
        int footprintX = middle.getX() + (int) Math.round(perpX * footprintOffset);
        int footprintZ = middle.getZ() + (int) Math.round(perpZ * footprintOffset);
        return new SlotProbe(anchorX, anchorZ, footprintX, footprintZ, directionFromVector(perpX, perpZ));
    }

    private static VillageFootprintScore scoreFootprint(ServerLevel level,
                                                        TerrainSamplingCache cache,
                                                        PathTerrainField terrain,
                                                        RoadsideVillageConfig cfg,
                                                        int centerX,
                                                        int centerZ,
                                                        int roadY,
                                                        int radius) {
        int sampleStep = footprintSampleStep(terrain, radius);
        int sampleCount = 0;
        int waterCount = 0;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        double heightSum = 0.0;
        double heightSquareSum = 0.0;
        List<Integer> heights = new ArrayList<>();

        for (int dz = -radius; dz <= radius; dz += sampleStep) {
            for (int dx = -radius; dx <= radius; dx += sampleStep) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                TerrainSample sample = sampleTerrain(level, cache, terrain, centerX + dx, centerZ + dz);
                sampleCount++;
                if (sample.water()) {
                    waterCount++;
                }
                int height = sample.height();
                heights.add(height);
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                heightSum += height;
                heightSquareSum += (double) height * height;
            }
        }

        if (sampleCount == 0) {
            return new VillageFootprintScore(0, 0, 0, 0, 0.0, 0.0, -1.0, false);
        }

        double mean = heightSum / sampleCount;
        double variance = Math.max(0.0, heightSquareSum / sampleCount - mean * mean);
        heights.sort(Comparator.naturalOrder());
        int q1 = heights.get(Math.max(0, heights.size() / 4));
        int q3 = heights.get(Math.min(heights.size() - 1, heights.size() * 3 / 4));
        int iqr = q3 - q1;
        int coreMin = heights.get(Math.max(0, heights.size() / 5));
        int coreMax = heights.get(Math.min(heights.size() - 1, heights.size() * 4 / 5));
        int coreRange = coreMax - coreMin;
        int heightRange = maxHeight - minHeight;
        double waterRatio = waterCount / (double) sampleCount;
        double roadDelta = Math.abs(mean - roadY);

        int maxRange = Math.max(cfg.maxLocalSlope() + 2, cfg.maxHeightDiff() + 3);
        double maxVariance = Math.max(2.5, cfg.maxLocalSlope() * cfg.maxLocalSlope() * 1.5);
        boolean usable = waterRatio <= 0.08
            && coreRange <= maxRange
            && iqr <= Math.max(2, cfg.maxLocalSlope() + 1)
            && variance <= maxVariance
            && roadDelta <= Math.max(cfg.maxDistanceFromCenter() / 2.0, cfg.maxHeightDiff() * 2.0);

        double rangeScore = 1.0 / (1.0 + coreRange);
        double varianceScore = 1.0 / (1.0 + variance);
        double roadDeltaScore = 1.0 / (1.0 + Math.min(roadDelta, cfg.maxDistanceFromCenter()));
        double waterPenalty = waterRatio * 4.0;
        double score = rangeScore * 1.8 + varianceScore + roadDeltaScore - waterPenalty - (heightRange > maxRange ? 0.5 : 0.0);
        return new VillageFootprintScore(sampleCount, waterCount, minHeight, maxHeight, mean, variance, score, usable);
    }

    private static TerrainSample sampleTerrain(ServerLevel level,
                                               TerrainSamplingCache cache,
                                               PathTerrainField terrain,
                                               int x,
                                               int z) {
        if (terrain != null && terrain.contains(x, z)) {
            return new TerrainSample(terrain.height(x, z), terrain.isColumnWater(x, z));
        }

        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        int height = accurate.surfaceHeight(x, z);
        int oceanFloor = accurate.oceanFloorWg(x, z);
        int sea = level.getSeaLevel();
        boolean water = cache.isWaterLike(level, x, z) && oceanFloor < sea;
        boolean shallowWaterSurface = height <= sea + 1 && oceanFloor < height - 1;
        return new TerrainSample(height, water || shallowWaterSurface);
    }

    private static int footprintSampleStep(PathTerrainField terrain, int radius) {
        int terrainStep = terrain != null ? Math.max(1, terrain.step()) : 4;
        int footprintStep = Math.max(2, radius / 2);
        return Math.max(2, Math.min(Math.min(terrainStep, 6), footprintStep));
    }

    private static int roadsideAnchorOffset(int width, RoadsideVillageConfig cfg) {
        int roadHalfWidth = Math.max(1, (width + 1) / 2);
        return roadHalfWidth + cfg.roadBufferBlocks();
    }

    private static Direction directionFromVector(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x >= 0 ? Direction.EAST : Direction.WEST;
        }
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static BoundingBox estimateBounds(List<BoundingBox> footprints) {
        if (footprints.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BoundingBox footprint : footprints) {
            minX = Math.min(minX, footprint.minX());
            minY = Math.min(minY, footprint.minY());
            minZ = Math.min(minZ, footprint.minZ());
            maxX = Math.max(maxX, footprint.maxX());
            maxY = Math.max(maxY, footprint.maxY());
            maxZ = Math.max(maxZ, footprint.maxZ());
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean intersectsAny(BoundingBox bounds, List<BoundingBox> acceptedBounds) {
        return intersectsAny(bounds, acceptedBounds, 8);
    }

    private static boolean intersectsAny(BoundingBox bounds, List<BoundingBox> acceptedBounds, int inflateBy) {
        BoundingBox inflated = bounds.inflatedBy(inflateBy);
        for (BoundingBox accepted : acceptedBounds) {
            if (inflated.intersects(accepted)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation styleForBiome(BiomeCategory category) {
        return switch (category) {
            case DESERT, BADLANDS -> new ResourceLocation("minecraft", "desert");
            case SAVANNA -> new ResourceLocation("minecraft", "savanna");
            case TAIGA -> new ResourceLocation("minecraft", "taiga");
            case SNOWY -> new ResourceLocation("minecraft", "snowy");
            default -> new ResourceLocation("minecraft", "plains");
        };
    }

    private record SlotLayout(
        List<PendingRoadsideVillageSlot> slots,
        List<BoundingBox> footprintBounds
    ) {}

    private record SlotProbe(int anchorX, int anchorZ, int footprintX, int footprintZ, Direction outward) {}

    private record TerrainSample(int height, boolean water) {}

    private record VillageFootprintScore(
        int sampleCount,
        int waterCount,
        int minHeight,
        int maxHeight,
        double meanHeight,
        double variance,
        double score,
        boolean usable
    ) {}

    private record WindowFootprintScore(int usableCount, int totalCount, double averageScore) {}

    private record WindowCandidate(int startIndex, int endIndex, int centerIndex, double score) {}
}
