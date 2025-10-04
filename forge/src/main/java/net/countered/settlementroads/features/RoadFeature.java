package net.countered.settlementroads.features;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.decoration.*;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.RoadDataStorage;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureConnectionsData;
import net.countered.settlementroads.persistence.attachments.WorldDataHelper.StructureLocationsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoadFeature extends Feature<RoadFeatureConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static final Set<Block> DONT_PLACE_HERE = new HashSet<>();
    static {
        DONT_PLACE_HERE.add(Blocks.PACKED_ICE);
        DONT_PLACE_HERE.add(Blocks.ICE);
        DONT_PLACE_HERE.add(Blocks.BLUE_ICE);
        DONT_PLACE_HERE.add(Blocks.TALL_SEAGRASS);
        DONT_PLACE_HERE.add(Blocks.MANGROVE_ROOTS);
    }

    public static int chunksForLocatingCounter = 1;

    public static final ResourceKey<PlacedFeature> ROAD_FEATURE_PLACED_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(SettlementRoads.MOD_ID, "road_feature_placed"));
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROAD_FEATURE_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(SettlementRoads.MOD_ID, "road_feature"));
    public static final Feature<RoadFeatureConfig> ROAD_FEATURE = new RoadFeature(RoadFeatureConfig.CODEC);

    public RoadFeature(Codec<RoadFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RoadFeatureConfig> context) {
        if (RoadPathCalculator.heightCache.size() > 100_000) {
            RoadPathCalculator.heightCache.clear();
        }

        WorldGenLevel level = context.level();
        ServerLevel serverLevel = level.getLevel();
        StructureLocationsData locationsData = WorldDataHelper.structureLocations(serverLevel);
        List<BlockPos> villageLocations = locationsData.getLocations();
        if (villageLocations.isEmpty()) {
            return false;
        }

        tryFindNewStructureConnection(villageLocations, serverLevel);
        Set<Decoration> roadDecorationCache = new HashSet<>();
        runRoadLogic(level, context, roadDecorationCache);
        RoadStructures.tryPlaceDecorations(roadDecorationCache);
        return true;
    }

    private void tryFindNewStructureConnection(List<BlockPos> villageLocations, ServerLevel serverWorld) {
        chunksForLocatingCounter++;
        if (chunksForLocatingCounter > 300) {
            StructureConnectionsData connectionsData = WorldDataHelper.structureConnections(serverWorld);
            serverWorld.getServer().execute(() -> StructureConnector.cacheNewConnection(serverWorld, true));
            chunksForLocatingCounter = 1;
        }
    }

    private void runRoadLogic(WorldGenLevel level, FeaturePlaceContext<RoadFeatureConfig> context, Set<Decoration> decorationPositions) {
        int averagingRadius = ModConfig.averagingRadius;
        RoadDataStorage roadDataStorage = WorldDataHelper.roadData(level.getLevel());
        List<Records.RoadData> roadDataList = roadDataStorage.getRoadData();
        if (roadDataList.isEmpty()) {
            return;
        }

        ChunkPos currentChunkPos = new ChunkPos(context.origin());
        RandomSource random = context.random();

        Set<BlockPos> occupied = new HashSet<>();
        for (Records.RoadData data : roadDataList) {
            int roadType = data.roadType();
            List<BlockState> materials = data.materials();
            List<Records.RoadSegmentPlacement> segments = data.roadSegmentList();
            List<BlockPos> middles = segments.stream().map(Records.RoadSegmentPlacement::middlePos).toList();

            int segmentIndex = 0;
            for (int i = 2; i < segments.size() - 2; i++) {
                BlockPos mid = middles.get(i);
                if (occupied.contains(mid)) {
                    continue;
                }

                segmentIndex++;
                if (segmentIndex < 60 || segmentIndex > segments.size() - 60) {
                    continue;
                }

                ChunkPos middleChunk = new ChunkPos(mid);
                if (!middleChunk.equals(currentChunkPos)) {
                    continue;
                }

                Records.RoadSegmentPlacement segment = segments.get(i);
                BlockPos prev = middles.get(i - 2);
                BlockPos next = middles.get(i + 2);

                List<Double> heights = new ArrayList<>();
                for (int j = i - averagingRadius; j <= i + averagingRadius; j++) {
                    if (j >= 0 && j < middles.size()) {
                        BlockPos sample = middles.get(j);
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sample.getX(), sample.getZ());
                        heights.add((double) y);
                    }
                }

                int averageY = (int) Math.round(heights.stream().mapToDouble(Double::doubleValue).average().orElse(mid.getY()));
                BlockPos averagedPos = new BlockPos(mid.getX(), averageY, mid.getZ());

                if (!ModConfig.placeWaypoints) {
                    for (BlockPos widthPos : segment.positions()) {
                        BlockPos corrected = new BlockPos(widthPos.getX(), averageY, widthPos.getZ());
                        placeOnSurface(level, corrected, materials, roadType, random);
                    }
                }

                addDecoration(level, decorationPositions, averagedPos, segmentIndex, next, prev, middles, roadType, random);
                occupied.add(mid);
            }
        }
    }

    private void addDecoration(WorldGenLevel level, Set<Decoration> decorationPositions,
                               BlockPos placePos, int segmentIndex, BlockPos nextPos, BlockPos prevPos,
                               List<BlockPos> middlePositions, int roadType, RandomSource random) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, placePos.getX(), placePos.getZ());
        BlockPos surfacePos = new BlockPos(placePos.getX(), surfaceY, placePos.getZ());

        if (ModConfig.placeWaypoints) {
            if (segmentIndex % 25 == 0) {
                decorationPositions.add(new FenceWaypointDecoration(surfacePos, level));
            }
            return;
        }

        int dx = nextPos.getX() - prevPos.getX();
        int dz = nextPos.getZ() - prevPos.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        int normDx = length != 0 ? (int) Math.round(dx / length) : 0;
        int normDz = length != 0 ? (int) Math.round(dz / length) : 0;
        Vec3i directionVector = new Vec3i(normDx, 0, normDz);
        Vec3i orthogonalVector = new Vec3i(-directionVector.getZ(), 0, directionVector.getX());

        boolean isEnd = segmentIndex != middlePositions.size() - 65;
        BlockPos shiftedPos;

        if (segmentIndex == 65 || segmentIndex == middlePositions.size() - 65) {
            shiftedPos = isEnd ? placePos.offset(orthogonalVector.multiply(2)) : placePos.offset(orthogonalVector.multiply(-2));
            decorationPositions.add(new DistanceSignDecoration(shiftedPos, orthogonalVector, level, isEnd, String.valueOf(middlePositions.size())));
        } else if (segmentIndex % 59 == 0) {
            boolean leftSide = random.nextBoolean();
            shiftedPos = leftSide ? placePos.offset(orthogonalVector.multiply(2)) : placePos.offset(orthogonalVector.multiply(-2));
            shiftedPos = withSurfaceY(level, shiftedPos);
            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }

            if (roadType == 0) {
                decorationPositions.add(new LamppostDecoration(shiftedPos, orthogonalVector, level, leftSide));
            } else {
                decorationPositions.add(new FenceWaypointDecoration(shiftedPos, level));
            }
        } else if (ModConfig.placeRoadFences && segmentIndex % 15 == 0) {
            boolean leftSide = random.nextBoolean();
            shiftedPos = leftSide ? placePos.offset(orthogonalVector.multiply(2)) : placePos.offset(orthogonalVector.multiply(-2));
            shiftedPos = withSurfaceY(level, shiftedPos);
            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }

            int fenceLength = random.nextIntBetweenInclusive(1, 3);
            decorationPositions.add(new RoadFenceDecoration(shiftedPos, orthogonalVector, level, leftSide, fenceLength));
        } else if (segmentIndex % 80 == 0) {
            List<String> structures = new ArrayList<>();
            if (ModConfig.placeSwings) structures.add("swing");
            if (ModConfig.placeBenches) structures.add("bench");
            if (ModConfig.placeGloriettes) structures.add("gloriette");
            if (structures.isEmpty()) {
                return;
            }

            String selected = structures.get(random.nextInt(structures.size()));
            boolean leftSide = random.nextBoolean();
            shiftedPos = leftSide
                    ? placePos.offset(orthogonalVector.multiply(ModConfig.structureDistanceFromRoad))
                    : placePos.offset(orthogonalVector.multiply(-ModConfig.structureDistanceFromRoad));
            shiftedPos = withSurfaceY(level, shiftedPos);

            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }

            switch (selected) {
                case "swing" -> decorationPositions.add(new SwingDecoration(shiftedPos, orthogonalVector, level));
                case "bench" -> decorationPositions.add(new NbtStructureDecoration(shiftedPos, orthogonalVector, level, "bench", new Vec3i(3, 3, 3)));
                case "gloriette" -> decorationPositions.add(new NbtStructureDecoration(shiftedPos, orthogonalVector, level, "gloriette", new Vec3i(5, 5, 5)));
            }
        }
    }

    private BlockPos withSurfaceY(WorldGenLevel level, BlockPos pos) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private void placeOnSurface(WorldGenLevel level, BlockPos placePos, List<BlockState> material, int roadType, RandomSource random) {
        double naturalBlockChance = 0.5;
        BlockPos surfacePos = placePos;
        if (roadType == 1 || ModConfig.averagingRadius == 0) {
            surfacePos = withSurfaceY(level, placePos);
        }

        BlockPos topPos = withSurfaceY(level, surfacePos);
        BlockState belowState = level.getBlockState(topPos.below());

        if (belowState.is(Blocks.WATER)) {
            level.setBlock(topPos, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), 3);
            return;
        }

        if (roadType == 0 || random.nextDouble() < naturalBlockChance) {
            placeRoadBlock(level, belowState, surfacePos, material, random);
        }
    }

    private void placeRoadBlock(WorldGenLevel level, BlockState blockStateAtPos, BlockPos surfacePos,
                                List<BlockState> materials, RandomSource random) {
        boolean sturdyBelow = level.getBlockState(surfacePos.below()).isFaceSturdy(level, surfacePos.below(), Direction.UP);
        boolean sturdyBelow2 = level.getBlockState(surfacePos.below(2)).isFaceSturdy(level, surfacePos.below(2), Direction.UP);

        if (!placeAllowedCheck(blockStateAtPos.getBlock()) || (!sturdyBelow && !sturdyBelow2)) {
            return;
        }

        BlockState material = materials.get(random.nextInt(materials.size()));
        level.setBlock(surfacePos.below(), material, 3);

        for (int i = 0; i < 3; i++) {
            BlockPos abovePos = surfacePos.above(i);
            BlockState aboveState = level.getBlockState(abovePos);
            if (!aboveState.isAir() && !aboveState.is(BlockTags.LOGS) && !aboveState.is(BlockTags.FENCES)) {
                level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), 3);
            } else {
                break;
            }
        }

        BlockPos belowPos = surfacePos.below(2);
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(belowPos, Blocks.DIRT.defaultBlockState(), 3);
        }
    }

    private boolean placeAllowedCheck(Block blockToCheck) {
        return !(DONT_PLACE_HERE.contains(blockToCheck)
                || blockToCheck.defaultBlockState().is(BlockTags.LEAVES)
                || blockToCheck.defaultBlockState().is(BlockTags.LOGS)
                || blockToCheck.defaultBlockState().is(BlockTags.UNDERWATER_BONEMEALS)
                || blockToCheck.defaultBlockState().is(BlockTags.WOODEN_FENCES)
                || blockToCheck.defaultBlockState().is(BlockTags.PLANKS));
    }
}
