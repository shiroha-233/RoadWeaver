package net.shiroha233.roadweaver.features.path;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.path.bridge.BridgeSegment;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlannerNew;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.HeightProfileService;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyMarkerPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkPlan;
import net.shiroha233.roadweaver.worldgen.road.RoadChunkSlice;
import net.shiroha233.roadweaver.worldgen.road.RoadWorldgenPlanCache;

import java.util.*;

/**
 * 道路世界生成 Feature
 */
public class PathFeature extends Feature<PathFeatureConfig> {
    public PathFeature(Codec<PathFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PathFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server) || !Level.OVERWORLD.equals(server.dimension()))
            return false;

        ModConfig cfg = ConfigService.get();
        if (!cfg.roadAppearance().roadsEnabled())
            return false;

        ChunkPos currentChunk = new ChunkPos(ctx.origin());
        RoadChunkPlan plan = RoadWorldgenPlanCache.get(server, currentChunk, cfg);
        if (plan.slices().isEmpty())
            return false;

        RandomSource random = ctx.random();
        int averagingRadius = Math.max(0, cfg.averagingRadius());

        Set<BlockPos> processedMiddle = new HashSet<>();
        Set<Decoration> decorations = new HashSet<>();

        for (RoadChunkSlice slice : plan.slices()) {
            processRoadSlice(world, server, currentChunk, slice, processedMiddle, decorations, random, cfg,
                    averagingRadius);
        }

        DecorationExecutor.tryPlaceDecorations(decorations);
        return true;
    }

    private static void processRoadSlice(WorldGenLevel world,
            ServerLevel server,
            ChunkPos currentChunk,
            RoadChunkSlice slice,
            Set<BlockPos> processedMiddle,
            Set<Decoration> decorations,
            RandomSource random,
            ModConfig cfg,
            int averagingRadius) {
        if (!cfg.roadAppearance().roadsEnabled()) {
            return;
        }

        RoadData data = slice.road();
        boolean bridgeEnabled = cfg.bridgeEnabled();
        int roadType = data.roadType();
        if (roadType != 0 && roadType != 1) {
            return;
        }
        
        int roadWidth = Math.max(1, data.width());
        List<BlockState> materials = data.materials();
        List<BlockState> slabMaterials = data.slabMaterials();
        List<RoadSegmentPlacement> segments = data.roadSegmentList();
        if (segments == null || segments.size() < 5)
            return;

        List<BlockPos> middlePositions = segments.stream().map(RoadSegmentPlacement::middlePos).toList();
        BridgeRangeCalculator.RangeResult res = BridgeRangeCalculator.compute(middlePositions, data.spans(), cfg);
        boolean[] isBridge = res.isBridge();
        List<int[]> bridgeRanges = res.mergedRanges();
        boolean[] skipSegments = res.skipSegments();

        BridgeSegment bridgeSegment = new BridgeSegment(isBridge, segments);

        boolean useBuoysInstead = bridgeEnabled && cfg.bridgeUseBuoysInstead();
        boolean useBuoysWhenSkipped = bridgeEnabled && cfg.bridgeUseBuoysWhenSkipped();

        int intervalBlocks = Math.max(4, cfg.buoyIntervalBlocks());
        boolean[] buoyMarkersForBridge = (useBuoysInstead
                ? BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks)
                : null);
        boolean[] buoyMarkersForSkipped = (useBuoysWhenSkipped
                ? BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks)
                : null);

        var targetY = data.targetY();
        boolean slopeLimitEnabled = cfg.slopeLimitEnabled();
        HeightProfileService.HeightProfile hp = HeightProfileService.build(
                world,
                middlePositions,
                currentChunk,
                averagingRadius,
                slopeLimitEnabled,
                cfg.maxSlopeStepPerTwoSegments(),
                targetY);
        boolean usePersisted = hp.usePersisted();
        int[] smoothedYArr = hp.smoothedY();
        int[] baseYArr;
        if (usePersisted && targetY != null && targetY.size() == middlePositions.size()) {
            baseYArr = targetY.stream().mapToInt(Integer::intValue).toArray();
        } else {
            baseYArr = smoothedYArr;
        }

        if (baseYArr != null && bridgeEnabled && !useBuoysInstead && !bridgeRanges.isEmpty()) {
            baseYArr = BridgeTransitionAdjuster.adjust(baseYArr, bridgeRanges, cfg);
        }

        int deckY = server.getSeaLevel() + cfg.bridgeDeckClearance();
        BridgeSegmentPlanner.Context bridgeCtx = BridgeSegmentPlanner.newContext();
        
        for (int localIndex = 0; localIndex < slice.segmentCount(); localIndex++) {
            int i = slice.segmentIndexAt(localIndex);
            if (i < 2 || i >= segments.size() - 2) {
                continue;
            }
            BlockPos middle = middlePositions.get(i);
            int segmentIndex = i - 1;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8)
                continue;
            if (!contains(currentChunk, middle))
                continue;
            if (!processedMiddle.add(middle))
                continue;

            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);

            int sea = server.getSeaLevel();
            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ());
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, middle.getX(), middle.getZ());
            int topYCenter = (motion > sea + 2) ? motion : surface;
            BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
            int baseYForThis = (baseYArr != null ? baseYArr[i] : topYCenter);

            RoadSegmentPlacement seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) {
                continue;
            }

            if (skipSegments != null && i >= 0 && i < skipSegments.length && skipSegments[i]) {
                if (useBuoysWhenSkipped && buoyMarkersForSkipped != null && i < buoyMarkersForSkipped.length
                        && buoyMarkersForSkipped[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (useBuoysInstead && isBridge != null && i >= 0 && i < isBridge.length && isBridge[i]) {
                if (buoyMarkersForBridge != null && i < buoyMarkersForBridge.length && buoyMarkersForBridge[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (bridgeEnabled && isBridge[i]) {
                var line = bridgeSegment.getLine(i);
                if (line == null || line.getTotalLength() <= 15) {
                    BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis, deckY,
                            segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                } else {
                    boolean success = BridgeSegmentPlannerNew.processSegment(world, line, seg, middle, prev);
                    if (!success) {
                        BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis,
                                deckY,
                                segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                    }
                }
            } else {
                SegmentPaver.paveSegment(world, seg, i, middlePositions, baseYArr, roadType, materials, slabMaterials,
                        random, cfg);

                if (cfg.roadAppearance().roadSignsEnabled()) {
                    SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world,
                            decorations,
                            averaged,
                            next,
                            prev,
                            roadWidth,
                            skipSegments,
                            i);
                }
            }

            if (!isBridge[i] || cfg.bridgeKeepLamps()) {
                DecorationPlanner.addDecoration(
                        world,
                        decorations,
                        averaged,
                        segmentIndex,
                        next,
                        prev,
                        middlePositions,
                        roadWidth,
                        random,
                        cfg,
                        (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL));
            }
        }
    }

    private static boolean contains(ChunkPos chunkPos, BlockPos pos) {
        return pos.getX() >= chunkPos.getMinBlockX() && pos.getX() <= chunkPos.getMaxBlockX()
                && pos.getZ() >= chunkPos.getMinBlockZ() && pos.getZ() <= chunkPos.getMaxBlockZ();
    }
}
