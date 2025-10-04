package net.countered.settlementroads.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RoadWeaver 模组配置类，使用 Cloth Config API
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("roadweaver.json");

    // Structure configuration
    public static String structureToLocate = "#minecraft:village";
    public static int structureSearchRadius = 100;

    // Pre-generation configuration
    public static int initialLocatingCount = 7;
    public static int maxLocatingCount = 15;
    public static int maxConcurrentRoadGeneration = 3;

    // Road configuration
    public static int averagingRadius = 1;
    public static boolean allowArtificial = true;
    public static boolean allowNatural = true;
    public static boolean placeWaypoints = false;
    public static boolean placeRoadFences = true;
    public static boolean placeSwings = true;
    public static boolean placeBenches = true;
    public static boolean placeGloriettes = true;
    public static int structureDistanceFromRoad = 4;
    public static int maxHeightDifference = 5;
    public static int maxTerrainStability = 4;

    /**
     * 配置数据类用于序列化
     */
    public static class ConfigData {
        public String structureToLocate = "#minecraft:village";
        public int structureSearchRadius = 100;
        public int initialLocatingCount = 7;
        public int maxLocatingCount = 15;
        public int maxConcurrentRoadGeneration = 3;
        public int averagingRadius = 1;
        public boolean allowArtificial = true;
        public boolean allowNatural = true;
        public boolean placeWaypoints = false;
        public boolean placeRoadFences = true;
        public boolean placeSwings = true;
        public boolean placeBenches = true;
        public boolean placeGloriettes = true;
        public int structureDistanceFromRoad = 4;
        public int maxHeightDifference = 5;
        public int maxTerrainStability = 4;
    }

    /**
     * 初始化配置
     */
    public static void init() {
        loadConfig();
    }

    /**
     * 创建配置界面
     */
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.roadweaver.title"));

        // 结构配置分类
        ConfigCategory structures = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.structures"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        structures.addEntry(entryBuilder.startStrField(Component.translatable("config.roadweaver.structureToLocate"), structureToLocate)
                .setDefaultValue("#minecraft:village")
                .setTooltip(Component.translatable("config.roadweaver.structureToLocate.tooltip"))
                .setSaveConsumer(value -> structureToLocate = value)
                .build());

        structures.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.structureSearchRadius"), structureSearchRadius)
                .setDefaultValue(100)
                .setMin(50).setMax(200)
                .setTooltip(Component.translatable("config.roadweaver.structureSearchRadius.tooltip"))
                .setSaveConsumer(value -> structureSearchRadius = value)
                .build());

        // 预生成配置分类
        ConfigCategory preGeneration = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.pregeneration"));

        preGeneration.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.initialLocatingCount"), initialLocatingCount)
                .setDefaultValue(7)
                .setMin(1).setMax(20)
                .setTooltip(Component.translatable("config.roadweaver.initialLocatingCount.tooltip"))
                .setSaveConsumer(value -> initialLocatingCount = value)
                .build());

        preGeneration.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.maxLocatingCount"), maxLocatingCount)
                .setDefaultValue(15)
                .setMin(2).setMax(50)
                .setTooltip(Component.translatable("config.roadweaver.maxLocatingCount.tooltip"))
                .setSaveConsumer(value -> maxLocatingCount = value)
                .build());

        preGeneration.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.maxConcurrentRoadGeneration"), maxConcurrentRoadGeneration)
                .setDefaultValue(3)
                .setMin(1).setMax(10)
                .setTooltip(Component.translatable("config.roadweaver.maxConcurrentRoadGeneration.tooltip"))
                .setSaveConsumer(value -> maxConcurrentRoadGeneration = value)
                .build());

        // 道路配置分类
        ConfigCategory roads = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.roads"));

        roads.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.averagingRadius"), averagingRadius)
                .setDefaultValue(1)
                .setMin(0).setMax(5)
                .setTooltip(Component.translatable("config.roadweaver.averagingRadius.tooltip"))
                .setSaveConsumer(value -> averagingRadius = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.allowArtificial"), allowArtificial)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.allowArtificial.tooltip"))
                .setSaveConsumer(value -> allowArtificial = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.allowNatural"), allowNatural)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.allowNatural.tooltip"))
                .setSaveConsumer(value -> allowNatural = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.placeWaypoints"), placeWaypoints)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.roadweaver.placeWaypoints.tooltip"))
                .setSaveConsumer(value -> placeWaypoints = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.placeRoadFences"), placeRoadFences)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeRoadFences.tooltip"))
                .setSaveConsumer(value -> placeRoadFences = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.placeSwings"), placeSwings)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeSwings.tooltip"))
                .setSaveConsumer(value -> placeSwings = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.placeBenches"), placeBenches)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeBenches.tooltip"))
                .setSaveConsumer(value -> placeBenches = value)
                .build());

        roads.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.roadweaver.placeGloriettes"), placeGloriettes)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeGloriettes.tooltip"))
                .setSaveConsumer(value -> placeGloriettes = value)
                .build());

        roads.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.structureDistanceFromRoad"), structureDistanceFromRoad)
                .setDefaultValue(4)
                .setMin(3).setMax(8)
                .setTooltip(Component.translatable("config.roadweaver.structureDistanceFromRoad.tooltip"))
                .setSaveConsumer(value -> structureDistanceFromRoad = value)
                .build());

        roads.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.maxHeightDifference"), maxHeightDifference)
                .setDefaultValue(5)
                .setMin(3).setMax(10)
                .setTooltip(Component.translatable("config.roadweaver.maxHeightDifference.tooltip"))
                .setSaveConsumer(value -> maxHeightDifference = value)
                .build());

        roads.addEntry(entryBuilder.startIntField(Component.translatable("config.roadweaver.maxTerrainStability"), maxTerrainStability)
                .setDefaultValue(4)
                .setMin(2).setMax(10)
                .setTooltip(Component.translatable("config.roadweaver.maxTerrainStability.tooltip"))
                .setSaveConsumer(value -> maxTerrainStability = value)
                .build());

        builder.setSavingRunnable(ModConfig::saveConfig);

        return builder.build();
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                applyConfig(data);
                LOGGER.info("RoadWeaver config loaded successfully");
            } else {
                saveConfig(); // 创建默认配置文件
                LOGGER.info("RoadWeaver config created with default values");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load RoadWeaver config: {}", e.getMessage());
        }
    }

    /**
     * 保存配置文件
     */
    public static void saveConfig() {
        try {
            ConfigData data = getCurrentConfig();
            String json = GSON.toJson(data);
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, json);
            LOGGER.info("RoadWeaver config saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save RoadWeaver config: {}", e.getMessage());
        }
    }

    /**
     * 应用配置数据到静态字段
     */
    private static void applyConfig(ConfigData data) {
        structureToLocate = data.structureToLocate;
        structureSearchRadius = data.structureSearchRadius;
        initialLocatingCount = data.initialLocatingCount;
        maxLocatingCount = data.maxLocatingCount;
        maxConcurrentRoadGeneration = data.maxConcurrentRoadGeneration;
        averagingRadius = data.averagingRadius;
        allowArtificial = data.allowArtificial;
        allowNatural = data.allowNatural;
        placeWaypoints = data.placeWaypoints;
        placeRoadFences = data.placeRoadFences;
        placeSwings = data.placeSwings;
        placeBenches = data.placeBenches;
        placeGloriettes = data.placeGloriettes;
        structureDistanceFromRoad = data.structureDistanceFromRoad;
        maxHeightDifference = data.maxHeightDifference;
        maxTerrainStability = data.maxTerrainStability;
    }

    /**
     * 获取当前配置数据
     */
    private static ConfigData getCurrentConfig() {
        ConfigData data = new ConfigData();
        data.structureToLocate = structureToLocate;
        data.structureSearchRadius = structureSearchRadius;
        data.initialLocatingCount = initialLocatingCount;
        data.maxLocatingCount = maxLocatingCount;
        data.maxConcurrentRoadGeneration = maxConcurrentRoadGeneration;
        data.averagingRadius = averagingRadius;
        data.allowArtificial = allowArtificial;
        data.allowNatural = allowNatural;
        data.placeWaypoints = placeWaypoints;
        data.placeRoadFences = placeRoadFences;
        data.placeSwings = placeSwings;
        data.placeBenches = placeBenches;
        data.placeGloriettes = placeGloriettes;
        data.structureDistanceFromRoad = structureDistanceFromRoad;
        data.maxHeightDifference = maxHeightDifference;
        data.maxTerrainStability = maxTerrainStability;
        return data;
    }
}