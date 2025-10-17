package net.shiroha233.roadweaver.config.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FabricModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("roadweaver.json");
    
    private static ConfigData data = new ConfigData();
    
    // 结构配置（多行：每行一个结构ID或标签）
    public static List<String> getStructuresToLocate() { return data.structuresToLocate; }
    public static void setStructuresToLocate(List<String> value) { data.structuresToLocate = value != null ? value : new ArrayList<>(); }
    
    public static int getStructureSearchRadius() { return data.structureSearchRadius; }
    public static void setStructureSearchRadius(int value) { data.structureSearchRadius = value; }
    
    // 预生成配置
    public static int getInitialLocatingCount() { return data.initialLocatingCount; }
    public static void setInitialLocatingCount(int value) { data.initialLocatingCount = value; }
    
    public static int getMaxConcurrentRoadGeneration() { return data.maxConcurrentRoadGeneration; }
    public static void setMaxConcurrentRoadGeneration(int value) { data.maxConcurrentRoadGeneration = value; }
    
    public static int getStructureSearchTriggerDistance() { return data.structureSearchTriggerDistance; }
    public static void setStructureSearchTriggerDistance(int value) { 
        data.structureSearchTriggerDistance = Math.max(150, Math.min(1500, value)); 
    }
    
    public static int getStructureBatchSize() { return data.structureBatchSize; }
    public static void setStructureBatchSize(int value) { 
        data.structureBatchSize = Math.max(1, Math.min(50, value)); 
    }
    
    public static int getStructureSearchThreads() { return data.structureSearchThreads; }
    public static void setStructureSearchThreads(int value) { 
        data.structureSearchThreads = Math.max(1, Math.min(8, value)); 
    }
    
    public static boolean getEnableAsyncStructureSearch() { return data.enableAsyncStructureSearch; }
    public static void setEnableAsyncStructureSearch(boolean value) { 
        data.enableAsyncStructureSearch = value; 
    }
    
    // 道路配置
    public static int getAveragingRadius() { return data.averagingRadius; }
    public static void setAveragingRadius(int value) { data.averagingRadius = value; }
    
    public static boolean getAllowArtificial() { return data.allowArtificial; }
    public static void setAllowArtificial(boolean value) { data.allowArtificial = value; }
    
    public static boolean getAllowNatural() { return data.allowNatural; }
    public static void setAllowNatural(boolean value) { data.allowNatural = value; }
    
    public static int getStructureDistanceFromRoad() { return data.structureDistanceFromRoad; }
    public static void setStructureDistanceFromRoad(int value) { data.structureDistanceFromRoad = value; }
    
    public static int getMaxHeightDifference() { return data.maxHeightDifference; }
    public static void setMaxHeightDifference(int value) { data.maxHeightDifference = value; }
    
    public static int getMaxTerrainStability() { return data.maxTerrainStability; }
    public static void setMaxTerrainStability(int value) { data.maxTerrainStability = value; }
    
    // 装饰配置
    public static boolean getPlaceWaypoints() { return data.placeWaypoints; }
    public static void setPlaceWaypoints(boolean value) { data.placeWaypoints = value; }
    
    public static boolean getPlaceRoadFences() { return data.placeRoadFences; }
    public static void setPlaceRoadFences(boolean value) { data.placeRoadFences = value; }
    
    public static boolean getPlaceSwings() { return data.placeSwings; }
    public static void setPlaceSwings(boolean value) { data.placeSwings = value; }
    
    public static boolean getPlaceBenches() { return data.placeBenches; }
    public static void setPlaceBenches(boolean value) { data.placeBenches = value; }
    
    public static boolean getPlaceGloriettes() { return data.placeGloriettes; }
    public static void setPlaceGloriettes(boolean value) { data.placeGloriettes = value; }


    //效果
    public static int getPowerOfTheRoadSpeedLevel() { return data.powerOfTheRoadSpeedLevel; }
    public static void setPowerOfTheRoadSpeedLevel(int value) { data.powerOfTheRoadSpeedLevel = Math.max(0, value); }

    public static int getPowerOfTheRoadRegenerationLevel() { return data.powerOfTheRoadRegenerationLevel; }
    public static void setPowerOfTheRoadRegenerationLevel(int value) { data.powerOfTheRoadRegenerationLevel = Math.max(0, value); }

    public static int getPowerOfTheRoadJumpLevel() { return data.powerOfTheRoadJumpLevel; }
    public static void setPowerOfTheRoadJumpLevel(int value) { data.powerOfTheRoadJumpLevel = Math.max(0, value); }


    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                data = GSON.fromJson(json, ConfigData.class);
                // 迁移：旧版单字符串 -> 新版多行列表
                if ((data.structuresToLocate == null || data.structuresToLocate.isEmpty()) && data.structureToLocate != null && !data.structureToLocate.isBlank()) {
                    data.structuresToLocate = tokenizeToList(data.structureToLocate);
                    // 清理旧字段以避免混淆
                    data.structureToLocate = null;
                    save();
                }
                // 验证并修正配置范围
                if (data.structureSearchTriggerDistance < 150 || data.structureSearchTriggerDistance > 1500) {
                    data.structureSearchTriggerDistance = 500; // 重置为默认值
                    save();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config file: {}", CONFIG_PATH, e);
            }
        } else {
            // 初始化默认
            if (data.structuresToLocate == null || data.structuresToLocate.isEmpty()) {
                data.structuresToLocate = new ArrayList<>(List.of("#minecraft:village"));
            }
            save();
        }
    }
    
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", CONFIG_PATH, e);
        }
    }
    
    private static class ConfigData {
        // 结构配置
        // 旧字段：向后兼容读取后迁移
        String structureToLocate = "#minecraft:village";
        // 新字段：每行一个结构/标签
        List<String> structuresToLocate = new ArrayList<>(List.of("#minecraft:village"));
        int structureSearchRadius = 100;
        
        // 预生成配置
        int initialLocatingCount = 7;
        int maxConcurrentRoadGeneration = 3;
        int structureSearchTriggerDistance = 500;
        int structureBatchSize = 5; // 批量累积：累积多少个结构后再统一加入道路规划
        int structureSearchThreads = 3; // 结构搜索线程池大小
        boolean enableAsyncStructureSearch = true; // 是否启用异步多线程结构搜索
        
        // 道路配置
        int averagingRadius = 1;
        boolean allowArtificial = true;
        boolean allowNatural = false;
        int structureDistanceFromRoad = 4;
        int maxHeightDifference = 5;
        int maxTerrainStability = 4;
        
        // 装饰配置
        boolean placeWaypoints = false;
        boolean placeRoadFences = true;
        boolean placeSwings = false;
        boolean placeBenches = false;
        boolean placeGloriettes = false;

        int powerOfTheRoadSpeedLevel = 2;
        int powerOfTheRoadRegenerationLevel = 0;
        int powerOfTheRoadJumpLevel = 2;
    }
    
    private static List<String> tokenizeToList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) return list;
        String normalized = raw.replace('\r', '\n');
        List<String> lines = Arrays.asList(normalized.split("\n"));
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 行内继续支持逗号/分号/空白分隔
            String[] tokens = trimmed.split("[;,\\s]+");
            for (String t : tokens) {
                if (t == null) continue;
                String token = t.trim();
                if (!token.isEmpty()) list.add(token);
            }
        }
        return list;
    }
}
