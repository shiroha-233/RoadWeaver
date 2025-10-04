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
        // 更新连接状态为"生成中"
        updateConnectionStatus(Records.ConnectionStatus.GENERATING);
        
        Random random = Random.create();
        int width = getRandomWidth(random, context);
        int type = allowedRoadTypes(random);
        // if all road types are disabled in config
        if (type == -1) {
            updateConnectionStatus(Records.ConnectionStatus.FAILED);
            return;
        }
        List<BlockState> material = (type == 1) ? getRandomNaturalRoadMaterials(random, context) : getRandomArtificialRoadMaterials(random, context);

        BlockPos start = structureConnection.from();
        BlockPos end = structureConnection.to();

        List<Records.RoadSegmentPlacement> roadSegmentPlacementList = RoadPathCalculator.calculateAStarRoadPath(start, end, width, serverWorld, maxSteps);

        // 检查是否生成失败（路径为空）
        if (roadSegmentPlacementList.isEmpty()) {
            updateConnectionStatus(Records.ConnectionStatus.FAILED);
            return;
        }

        List<Records.RoadData> roadDataList = new ArrayList<>(serverWorld.getAttachedOrCreate(WorldDataAttachment.ROAD_DATA_LIST, ArrayList::new));
        roadDataList.add(new Records.RoadData(width, type, material, roadSegmentPlacementList));

        serverWorld.setAttached(WorldDataAttachment.ROAD_DATA_LIST, roadDataList);
        
        // 道路生成完成，更新状态为"已完成"
        updateConnectionStatus(Records.ConnectionStatus.COMPLETED);
    }
    
    private void updateConnectionStatus(Records.ConnectionStatus newStatus) {
        List<Records.StructureConnection> connections = new ArrayList<>(
                serverWorld.getAttachedOrCreate(WorldDataAttachment.CONNECTED_STRUCTURES, ArrayList::new)
        );
        
        // 查找并更新对应的连接
        for (int i = 0; i < connections.size(); i++) {
            Records.StructureConnection conn = connections.get(i);
            if ((conn.from().equals(structureConnection.from()) && conn.to().equals(structureConnection.to())) ||
                (conn.from().equals(structureConnection.to()) && conn.to().equals(structureConnection.from()))) {
                // 更新状态
                connections.set(i, new Records.StructureConnection(conn.from(), conn.to(), newStatus));
                serverWorld.setAttached(WorldDataAttachment.CONNECTED_STRUCTURES, connections);
                break;
            }
        }
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
