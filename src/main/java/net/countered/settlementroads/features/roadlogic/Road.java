package net.countered.settlementroads.features.roadlogic;

import net.countered.settlementroads.config.ModConfig;
import net.countered.settlementroads.features.config.RoadFeatureConfig;
import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.persistence.attachments.WorldDataAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

public class Road {

    ServerWorld serverWorld;
    Records.StructureConnection structureConnection;
    RoadFeatureConfig context;

    public Road(ServerWorld serverWorld, Records.StructureConnection structureConnection, RoadFeatureConfig config) {
        this.serverWorld = serverWorld;
        this.structureConnection = structureConnection;
        this.context = config;
    }

    public void generateRoad(int maxSteps){
        Random random = Random.create();
        int width = getRandomWidth(random, context);
        int type = allowedRoadTypes(random);
        // if all road types are disabled in config
        if (type == -1) {
            return;
        }
        List<BlockState> material = (type == 1) ? getRandomNaturalRoadMaterials(random, context) : getRandomArtificialRoadMaterials(random, context);

        BlockPos start = structureConnection.from();
        BlockPos end = structureConnection.to();

        List<Records.RoadSegmentPlacement> roadSegmentPlacementList = RoadPathCalculator.calculateAStarRoadPath(start, end, width, serverWorld, maxSteps);

        List<Records.RoadData> roadDataList = new ArrayList<>(serverWorld.getAttachedOrCreate(WorldDataAttachment.ROAD_DATA_LIST, ArrayList::new));
        roadDataList.add(new Records.RoadData(width, type, material, roadSegmentPlacementList));

        serverWorld.setAttached(WorldDataAttachment.ROAD_DATA_LIST, roadDataList);
    }

    private static int allowedRoadTypes(Random deterministicRandom) {
        if (ModConfig.allowArtificial && ModConfig.allowNatural){
            return getRandomRoadType(deterministicRandom);
        }
        else if (ModConfig.allowArtificial){
            return 0;
        }
        else if (ModConfig.allowNatural) {
            return 1;
        }
        else {
            return -1;
        }
    }

    private static int getRandomRoadType(Random random) {
        return random.nextBetween(0, 1);
    }

    private static List<BlockState> getRandomNaturalRoadMaterials(Random random, RoadFeatureConfig config) {
        List<List<BlockState>> materialsList = config.getNaturalMaterials();
        return materialsList.get(random.nextInt(materialsList.size()));
    }

    private static List<BlockState> getRandomArtificialRoadMaterials(Random random, RoadFeatureConfig config) {
        List<List<BlockState>> materialsList = config.getArtificialMaterials();
        return materialsList.get(random.nextInt(materialsList.size()));
    }

    private static int getRandomWidth(Random random, RoadFeatureConfig config) {
        List<Integer> widthList = config.getWidths();
        return widthList.get(random.nextInt(widthList.size()));
    }
}
