package net.countered.settlementroads.platform.services;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * 世界数据存储抽象接口
 * 用于在不同平台上持久化保存数据
 */
public class WorldDataStorage {
    
    /**
     * 获取结构位置数据
     */
    @ExpectPlatform
    public static Records.StructureLocationData getStructureLocations(ServerWorld world) {
        throw new AssertionError();
    }
    
    /**
     * 设置结构位置数据
     */
    @ExpectPlatform
    public static void setStructureLocations(ServerWorld world, Records.StructureLocationData data) {
        throw new AssertionError();
    }
    
    /**
     * 获取结构连接列表
     */
    @ExpectPlatform
    public static List<Records.StructureConnection> getStructureConnections(ServerWorld world) {
        throw new AssertionError();
    }
    
    /**
     * 设置结构连接列表
     */
    @ExpectPlatform
    public static void setStructureConnections(ServerWorld world, List<Records.StructureConnection> connections) {
        throw new AssertionError();
    }
    
    /**
     * 获取道路数据列表
     */
    @ExpectPlatform
    public static List<Records.RoadData> getRoadDataList(ServerWorld world) {
        throw new AssertionError();
    }
    
    /**
     * 设置道路数据列表
     */
    @ExpectPlatform
    public static void setRoadDataList(ServerWorld world, List<Records.RoadData> roadDataList) {
        throw new AssertionError();
    }
}
