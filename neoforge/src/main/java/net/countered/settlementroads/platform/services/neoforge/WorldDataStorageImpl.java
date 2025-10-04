package net.countered.settlementroads.platform.services.neoforge;

import net.countered.settlementroads.helpers.Records;
import net.countered.settlementroads.neoforge.persistence.NeoForgeWorldDataStorage;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * NeoForge平台的WorldDataStorage实现
 * 用于Architectury的@ExpectPlatform机制
 */
public class WorldDataStorageImpl {
    
    public static Records.StructureLocationData getStructureLocations(ServerLevel world) {
        return NeoForgeWorldDataStorage.getStructureLocations(world);
    }
    
    public static void setStructureLocations(ServerLevel world, Records.StructureLocationData data) {
        NeoForgeWorldDataStorage.setStructureLocations(world, data);
    }
    
    public static List<Records.StructureConnection> getStructureConnections(ServerLevel world) {
        return NeoForgeWorldDataStorage.getStructureConnections(world);
    }
    
    public static void setStructureConnections(ServerLevel world, List<Records.StructureConnection> connections) {
        NeoForgeWorldDataStorage.setStructureConnections(world, connections);
    }
    
    public static List<Records.RoadData> getRoadDataList(ServerLevel world) {
        return NeoForgeWorldDataStorage.getRoadDataList(world);
    }
    
    public static void setRoadDataList(ServerLevel world, List<Records.RoadData> roadDataList) {
        NeoForgeWorldDataStorage.setRoadDataList(world, roadDataList);
    }
}
