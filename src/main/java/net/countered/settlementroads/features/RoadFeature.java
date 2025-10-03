package net.countered.settlementroads.features;

import com.mojang.serialization.Codec;
import net.countered.settlementroads.SettlementRoads;
import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.features.decoration.*;
import net.countered.settlementroads.features.roadlogic.RoadPathCalculator;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.helpers.StructureConnector;
import net.countered.settlementroads.persistence.attachments.WorldDataAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoadFeature extends Feature<RoadFeatureConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementRoads.MOD_ID);

    public static final Set<Block> dontPlaceHere = new HashSet<>();
    static {
        dontPlaceHere.add(Blocks.PACKED_ICE);
        dontPlaceHere.add(Blocks.ICE);
        dontPlaceHere.add(Blocks.BLUE_ICE);
        dontPlaceHere.add(Blocks.TALL_SEAGRASS);
        dontPlaceHere.add(Blocks.MANGROVE_ROOTS);
    }

    public static int chunksForLocatingCounter = 1;

    public static final RegistryKey<PlacedFeature> ROAD_FEATURE_PLACED_KEY =
            RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(SettlementRoads.MOD_ID, "road_feature_placed"));
    public static final RegistryKey<ConfiguredFeature<?,?>> ROAD_FEATURE_KEY =
            RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, Identifier.of(SettlementRoads.MOD_ID, "road_feature"));
    public static final Feature<RoadFeatureConfig> ROAD_FEATURE = new RoadFeature(RoadFeatureConfig.CODEC);
    public RoadFeature(Codec<RoadFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<RoadFeatureConfig> context) {
        if (RoadPathCalculator.heightCache.size() > 100_000){
            RoadPathCalculator.heightCache.clear();
        }
        ServerWorld serverWorld = context.getWorld().toServerWorld();
        StructureWorldAccess structureWorldAccess = context.getWorld();
        Records.StructureLocationData structureLocationData = serverWorld.getAttached(WorldDataAttachment.STRUCTURE_LOCATIONS);
        if (structureLocationData == null) {
            return false;
        }
        List<BlockPos> villageLocations = structureLocationData.structureLocations();;
        tryFindNewStructureConnection(villageLocations, serverWorld);
        Set<Decoration> roadDecorationCache = new HashSet<>();
        runRoadLogic(structureWorldAccess, context, roadDecorationCache);
        RoadStructures.tryPlaceDecorations(roadDecorationCache);
        return true;
    }

    private void tryFindNewStructureConnection(List<BlockPos> villageLocations, ServerWorld serverWorld) {
        // 移除数量限制，改为基于距离的智能搜寻
        chunksForLocatingCounter++;
        if (chunksForLocatingCounter > 300) {
            List<Records.StructureConnection> connectionList= serverWorld.getAttached(WorldDataAttachment.CONNECTED_STRUCTURES);
            serverWorld.getServer().execute(() -> {
                StructureConnector.cacheNewConnection(serverWorld, true);
            });
            chunksForLocatingCounter = 1;
        }
    }

    private void runRoadLogic(StructureWorldAccess structureWorldAccess, FeatureContext<RoadFeatureConfig> context, Set<Decoration> roadDecorationPlacementPositions) {
        int averagingRadius = ModConfig.averagingRadius;
        List<Records.RoadData> roadDataList = structureWorldAccess.toServerWorld().getAttached(WorldDataAttachment.ROAD_DATA_LIST);
        if (roadDataList == null) return;
        ChunkPos currentChunkPos = new ChunkPos(context.getOrigin());

        Set<BlockPos> posAlreadyContainsSegment = new HashSet<>();
        for (Records.RoadData data : roadDataList) {
            int roadType = data.roadType();
            List<BlockState> materials = data.materials();
            List<Records.RoadSegmentPlacement> segmentList = data.roadSegmentList();

            List<BlockPos> middlePositions = segmentList.stream().map(Records.RoadSegmentPlacement::middlePos).toList();
            int segmentIndex = 0;
            for (int i = 2; i < segmentList.size() - 2; i++) {
                if (posAlreadyContainsSegment.contains(middlePositions.get(i))) continue;
                segmentIndex++;
                Records.RoadSegmentPlacement segment = segmentList.get(i);
                BlockPos segmentMiddlePos = segment.middlePos();
                // offset to structure
                if (segmentIndex < 60 || segmentIndex > segmentList.size() - 60) continue;
                ChunkPos middleChunkPos = new ChunkPos(segmentMiddlePos);
                if (!middleChunkPos.equals(currentChunkPos)) continue;

                BlockPos prevPos = middlePositions.get(i - 2);
                BlockPos nextPos = middlePositions.get(i + 2);
                List<Double> heights = new ArrayList<>();
                for (int j = i - averagingRadius; j <= i + averagingRadius; j++) {
                    if (j >= 0 && j < middlePositions.size()) {
                        BlockPos samplePos = middlePositions.get(j);
                        double y = structureWorldAccess.getTopY(Heightmap.Type.WORLD_SURFACE_WG, samplePos.getX(), samplePos.getZ());
                        heights.add(y);
                    }
                }

                int averageY = (int) Math.round(heights.stream().mapToDouble(Double::doubleValue).average().orElse(segmentMiddlePos.getY()));
                BlockPos averagedPos = new BlockPos(segmentMiddlePos.getX(), averageY, segmentMiddlePos.getZ());

                Random random = context.getRandom();
                if (!ModConfig.placeWaypoints) {
                    for (BlockPos widthBlock : segment.positions()) {
                        BlockPos correctedYPos = new BlockPos(widthBlock.getX(), averageY, widthBlock.getZ());
                        placeOnSurface(structureWorldAccess, correctedYPos, materials, roadType, random);
                    }
                }
                addDecoration(structureWorldAccess, roadDecorationPlacementPositions, averagedPos, segmentIndex, nextPos, prevPos, middlePositions, roadType, random);
                posAlreadyContainsSegment.add(segmentMiddlePos);
            }
        }
    }

    private void addDecoration(StructureWorldAccess structureWorldAccess, Set<Decoration> roadDecorationPlacementPositions,
                               BlockPos placePos, int segmentIndex, BlockPos nextPos, BlockPos prevPos, List<BlockPos> middleBlockPositions, int roadType, Random random) {
        BlockPos surfacePos = placePos.withY(structureWorldAccess.getTopY(Heightmap.Type.WORLD_SURFACE_WG, placePos.getX(), placePos.getZ()));
        BlockState blockStateAtPos = structureWorldAccess.getBlockState(surfacePos.down());
        // Water surface handling is now done in placeOnSurface method
        if (ModConfig.placeWaypoints) {
            if (segmentIndex % 25 == 0) {
                roadDecorationPlacementPositions.add(new FenceWaypointDecoration(surfacePos, structureWorldAccess));
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
        boolean isEnd = segmentIndex != middleBlockPositions.size() - 65;
        BlockPos shiftedPos;
        if (segmentIndex == 65 || segmentIndex == middleBlockPositions.size() - 65) {
            shiftedPos = isEnd ? placePos.add(orthogonalVector.multiply(2)) : placePos.subtract(orthogonalVector.multiply(2));
            roadDecorationPlacementPositions.add(new DistanceSignDecoration(shiftedPos, orthogonalVector, structureWorldAccess, isEnd, String.valueOf(middleBlockPositions.size())));
        }
        else if (segmentIndex % 59 == 0) {
            boolean leftRoadSide = random.nextBoolean();
            shiftedPos = leftRoadSide ? placePos.add(orthogonalVector.multiply(2)) : placePos.subtract(orthogonalVector.multiply(2));
            shiftedPos = shiftedPos.withY(structureWorldAccess.getTopY(Heightmap.Type.WORLD_SURFACE_WG, shiftedPos.getX(), shiftedPos.getZ()));
            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }
            if (roadType == 0) {
                roadDecorationPlacementPositions.add(new LamppostDecoration(shiftedPos, orthogonalVector, structureWorldAccess, leftRoadSide));
            }
            else {
                roadDecorationPlacementPositions.add(new FenceWaypointDecoration(shiftedPos, structureWorldAccess));
            }
        }
        // 添加间断栏杆装饰
        else if (ModConfig.placeRoadFences && segmentIndex % 15 == 0) {
            // 随机选择道路一侧
            boolean leftRoadSide = random.nextBoolean();
            shiftedPos = leftRoadSide ? placePos.add(orthogonalVector.multiply(2)) : placePos.subtract(orthogonalVector.multiply(2));
            shiftedPos = shiftedPos.withY(structureWorldAccess.getTopY(Heightmap.Type.WORLD_SURFACE_WG, shiftedPos.getX(), shiftedPos.getZ()));
            
            // 检查高度差
            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }
            
            // 随机栏杆长度（1-3个方块）
            int fenceLength = random.nextBetween(1, 3);
            roadDecorationPlacementPositions.add(new RoadFenceDecoration(shiftedPos, orthogonalVector, structureWorldAccess, leftRoadSide, fenceLength));
        }
        // 添加大型装饰结构（秋千、长椅、凉亭等）
        else if (segmentIndex % 80 == 0) {
            // 随机选择装饰类型
            java.util.List<String> availableStructures = new java.util.ArrayList<>();
            
            if (ModConfig.placeSwings) availableStructures.add("swing");
            if (ModConfig.placeBenches) availableStructures.add("bench");
            if (ModConfig.placeGloriettes) availableStructures.add("gloriette");
            
            if (availableStructures.isEmpty()) {
                return;
            }
            
            // 随机选择一个结构
            String selectedStructure = availableStructures.get(random.nextInt(availableStructures.size()));
            
            // 大型结构需要更多空间，选择道路一侧并远离道路中心
            boolean leftRoadSide = random.nextBoolean();
            shiftedPos = leftRoadSide ? placePos.add(orthogonalVector.multiply(ModConfig.structureDistanceFromRoad)) : placePos.subtract(orthogonalVector.multiply(ModConfig.structureDistanceFromRoad));
            shiftedPos = shiftedPos.withY(structureWorldAccess.getTopY(Heightmap.Type.WORLD_SURFACE_WG, shiftedPos.getX(), shiftedPos.getZ()));
            
            // 对地形要求更严格
            if (Math.abs(shiftedPos.getY() - placePos.getY()) > 1) {
                return;
            }
            
            // 根据选择的结构类型创建装饰
            switch (selectedStructure) {
                case "swing":
                    roadDecorationPlacementPositions.add(new SwingDecoration(shiftedPos, orthogonalVector, structureWorldAccess));
                    break;
                case "bench":
                    roadDecorationPlacementPositions.add(new NbtStructureDecoration(shiftedPos, orthogonalVector, structureWorldAccess, "bench", new Vec3i(3, 3, 3)));
                    break;
                case "gloriette":
                    roadDecorationPlacementPositions.add(new NbtStructureDecoration(shiftedPos, orthogonalVector, structureWorldAccess, "gloriette", new Vec3i(5, 5, 5)));
                    break;
            }
        }
    }

    private void placeOnSurface(StructureWorldAccess structureWorldAccess, BlockPos placePos, List<BlockState> material, int natural, Random random) {
        double naturalBlockChance = 0.5;
        BlockPos surfacePos = placePos;
        if (natural == 1 || ModConfig.averagingRadius == 0) {
            surfacePos = structureWorldAccess.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, placePos);
        }
        BlockPos topPos = structureWorldAccess.getTopPosition(Heightmap.Type.WORLD_SURFACE_WG, surfacePos);
        BlockState blockStateAtPos = structureWorldAccess.getBlockState(topPos.down());
        
        // Check if this is water surface - place unlit campfire instead of regular road
        if (blockStateAtPos.equals(Blocks.WATER.getDefaultState())) {
            structureWorldAccess.setBlockState(topPos, Blocks.CAMPFIRE.getDefaultState().with(net.minecraft.state.property.Properties.LIT, false), 3);
            return;
        }
        
        // place road
        if (natural == 0 || random.nextDouble() < naturalBlockChance) {
            placeRoadBlock(structureWorldAccess, blockStateAtPos, surfacePos, material, random);
        }
    }

    private void placeRoadBlock(StructureWorldAccess structureWorldAccess, BlockState blockStateAtPos, BlockPos surfacePos, List<BlockState> materials, Random deterministicRandom) {
        // If not water, just place the road
        if (!placeAllowedCheck(blockStateAtPos.getBlock())
                || (!structureWorldAccess.getBlockState(surfacePos.down()).isOpaque())
                && !structureWorldAccess.getBlockState(surfacePos.down(2)).isOpaque()
                //&& !structureWorldAccess.getBlockState(surfacePos.down(3)).isOpaque())
                //|| structureWorldAccess.getBlockState(surfacePos.up(3)).isOpaque()
        ) {
            return;
        }
        BlockState material = materials.get(deterministicRandom.nextInt(materials.size()));
        setBlockState(structureWorldAccess, surfacePos.down(), material);

        for (int i = 0; i < 3; i++) {
            BlockState blockStateUp = structureWorldAccess.getBlockState(surfacePos.up(i));
            if (!blockStateUp.getBlock().equals(Blocks.AIR) && !blockStateUp.isIn(BlockTags.LOGS) && !blockStateUp.isIn(BlockTags.FENCES)) {
                setBlockState(structureWorldAccess, surfacePos.up(i), Blocks.AIR.getDefaultState());
            }
            else {
                break;
            }
        }

        BlockPos belowPos1 = surfacePos.down(2);
        BlockState belowState1 = structureWorldAccess.getBlockState(belowPos1);

        if (belowState1.getBlock().equals(Blocks.GRASS_BLOCK)) {
            setBlockState(structureWorldAccess, belowPos1, Blocks.DIRT.getDefaultState());
        }
    }

    private boolean placeAllowedCheck (Block blockToCheck) {
        return !(dontPlaceHere.contains(blockToCheck)
                || blockToCheck.getDefaultState().isIn(BlockTags.LEAVES)
                || blockToCheck.getDefaultState().isIn(BlockTags.LOGS)
                || blockToCheck.getDefaultState().isIn(BlockTags.UNDERWATER_BONEMEALS)
                || blockToCheck.getDefaultState().isIn(BlockTags.WOODEN_FENCES)
                || blockToCheck.getDefaultState().isIn(BlockTags.PLANKS)
        );
    }
}

