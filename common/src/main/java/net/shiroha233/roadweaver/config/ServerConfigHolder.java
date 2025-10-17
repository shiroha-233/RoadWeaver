package net.shiroha233.roadweaver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端配置持有者
 * 在多人游戏中，客户端使用从服务端同步的配置
 */
public class ServerConfigHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    
    // 是否在多人游戏的客户端
    private static boolean isMultiplayerClient = false;
    
    // 从服务端同步的配置值
    private static final Map<String, Object> syncedConfig = new HashMap<>();
    
    /**
     * 设置是否为多人游戏客户端
     */
    public static void setMultiplayerClient(boolean value) {
        isMultiplayerClient = value;
        if (!value) {
            // 离开服务器时清空同步配置
            syncedConfig.clear();
            LOGGER.info("RoadWeaver: 已清空服务端同步配置");
        }
    }
    
    /**
     * 检查是否为多人游戏客户端
     */
    public static boolean isMultiplayerClient() {
        return isMultiplayerClient;
    }
    
    /**
     * 从服务端同步配置
     */
    public static void syncFromServer(Map<String, Object> config) {
        syncedConfig.clear();
        syncedConfig.putAll(config);
        LOGGER.info("RoadWeaver: 已从服务端同步配置 ({} 项)", config.size());
    }
    
    /**
     * 获取同步的配置值
     */
    public static <T> T getSynced(String key, T defaultValue) {
        if (!isMultiplayerClient) {
            return defaultValue; // 单人游戏或服务端，使用本地配置
        }
        
        Object value = syncedConfig.get(key);
        if (value == null) {
            LOGGER.warn("RoadWeaver: 配置项 {} 未从服务端同步，使用默认值", key);
            return defaultValue;
        }
        
        try {
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } catch (ClassCastException e) {
            LOGGER.error("RoadWeaver: 配置项 {} 类型不匹配，使用默认值", key, e);
            return defaultValue;
        }
    }
    
    /**
     * 将当前配置打包为 Map（用于发送到客户端）
     */
    public static Map<String, Object> packConfig(IModConfig config) {
        Map<String, Object> map = new HashMap<>();
        
        // 结构配置
        map.put("structuresToLocate", config.structuresToLocate());
        map.put("structureSearchRadius", config.structureSearchRadius());
        
        // 预生成配置
        map.put("initialLocatingCount", config.initialLocatingCount());
        map.put("maxConcurrentRoadGeneration", config.maxConcurrentRoadGeneration());
        map.put("structureSearchTriggerDistance", config.structureSearchTriggerDistance());
        map.put("structureBatchSize", config.structureBatchSize());
        map.put("structureSearchThreads", config.structureSearchThreads());
        map.put("enableAsyncStructureSearch", config.enableAsyncStructureSearch());
        
        // 道路配置
        map.put("averagingRadius", config.averagingRadius());
        map.put("allowArtificial", config.allowArtificial());
        map.put("allowNatural", config.allowNatural());
        map.put("placeWaypoints", config.placeWaypoints());
        map.put("placeRoadFences", config.placeRoadFences());
        map.put("placeSwings", config.placeSwings());
        map.put("placeBenches", config.placeBenches());
        map.put("placeGloriettes", config.placeGloriettes());
        map.put("structureDistanceFromRoad", config.structureDistanceFromRoad());
        map.put("maxHeightDifference", config.maxHeightDifference());
        map.put("maxTerrainStability", config.maxTerrainStability());

        //效果
        map.put("powerOfTheRoadSpeedLevel", config.getPowerOfTheRoadSpeedLevel()); // packConfig 内
        map.put("powerOfTheRoadRegenerationLevel", config.getPowerOfTheRoadRegenerationLevel());
        map.put("powerOfTheRoadJumpLevel", config.getPowerOfTheRoadJumpLevel());

        return map;
    }
}
