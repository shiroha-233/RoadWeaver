package net.countered.settlementroads.platform.services.fabric;

import net.countered.settlementroads.fabric.persistence.FabricWorldDataAttachment;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric平台的WorldDataStorage实现
 * 用于Architectury的@ExpectPlatform机制
 */
public class WorldDataStorageImpl {
    
    public static Records.StructureLocationData getStructureLocations(ServerWorld world) {
        Records.StructureLocationData data = world.getAttached(FabricWorldDataAttachment.STRUCTURE_LOCATIONS);
        if (data == null) {
            data = new Records.StructureLocationData(new ArrayList<>());
            world.setAttached(FabricWorldDataAttachment.STRUCTURE_LOCATIONS, data);
        }
        return data;
    }
    
    public static void setStructureLocations(ServerWorld world, Records.StructureLocationData data) {
        world.setAttached(FabricWorldDataAttachment.STRUCTURE_LOCATIONS, data);
    }
    
    public static List<Records.StructureConnection> getStructureConnections(ServerWorld world) {
        List<Records.StructureConnection> connections = world.getAttached(FabricWorldDataAttachment.CONNECTED_STRUCTURES);
        if (connections == null) {
            connections = new ArrayList<>();
            world.setAttached(FabricWorldDataAttachment.CONNECTED_STRUCTURES, connections);
        }
        return connections;
    }
    
    public static void setStructureConnections(ServerWorld world, List<Records.StructureConnection> connections) {
        world.setAttached(FabricWorldDataAttachment.CONNECTED_STRUCTURES, connections);
    }
    
    public static List<Records.RoadData> getRoadDataList(ServerWorld world) {
        List<Records.RoadData> roadDataList = world.getAttached(FabricWorldDataAttachment.ROAD_DATA_LIST);
        if (roadDataList == null) {
            roadDataList = new ArrayList<>();
            world.setAttached(FabricWorldDataAttachment.ROAD_DATA_LIST, roadDataList);
        }
        return roadDataList;
    }
    
    public static void setRoadDataList(ServerWorld world, List<Records.RoadData> roadDataList) {
        world.setAttached(FabricWorldDataAttachment.ROAD_DATA_LIST, roadDataList);
    }
}
