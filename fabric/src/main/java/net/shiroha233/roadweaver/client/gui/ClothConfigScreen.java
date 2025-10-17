package net.shiroha233.roadweaver.client.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.shiroha233.roadweaver.config.fabric.FabricModConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fabric端的Cloth Config配置界面
 */
public class ClothConfigScreen {
    
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.roadweaver.title"))
                .setSavingRunnable(FabricModConfig::save);
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        
        // 结构配置分类
        ConfigCategory structures = builder.getOrCreateCategory(
                Component.translatable("config.roadweaver.category.structures"));
        
        structures.addEntry(entryBuilder.startStrList(
                Component.translatable("config.roadweaver.structureToLocate"),
                new java.util.ArrayList<>(FabricModConfig.getStructuresToLocate()))
                .setTooltip(Component.translatable("config.roadweaver.structureToLocate.tooltip"))
                .setExpanded(true)
                .setSaveConsumer(FabricModConfig::setStructuresToLocate)
                .build());
        
        structures.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.roadweaver.structureSearchRadius"),
                FabricModConfig.getStructureSearchRadius(),
                50, 200)
                .setDefaultValue(100)
                .setTooltip(Component.translatable("config.roadweaver.structureSearchRadius.tooltip"))
                .setSaveConsumer(FabricModConfig::setStructureSearchRadius)
                .build());
        
        // 预生成配置分类
        ConfigCategory preGeneration = builder.getOrCreateCategory(
                Component.translatable("config.roadweaver.category.pregeneration"));
        
        preGeneration.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.initialLocatingCount"),
                FabricModConfig.getInitialLocatingCount())
                .setDefaultValue(7)
                .setMin(1)
                .setMax(50)
                .setTooltip(Component.translatable("config.roadweaver.initialLocatingCount.tooltip"))
                .setSaveConsumer(FabricModConfig::setInitialLocatingCount)
                .build());
        
        preGeneration.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.maxConcurrentRoadGeneration"),
                FabricModConfig.getMaxConcurrentRoadGeneration())
                .setDefaultValue(3)
                .setMin(1)
                .setMax(256)
                .setTooltip(Component.translatable("config.roadweaver.maxConcurrentRoadGeneration.tooltip"))
                .setSaveConsumer(FabricModConfig::setMaxConcurrentRoadGeneration)
                .build());
        
        preGeneration.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.roadweaver.structureSearchTriggerDistance"),
                FabricModConfig.getStructureSearchTriggerDistance(),
                150, 1500)
                .setDefaultValue(500)
                .setTooltip(Component.translatable("config.roadweaver.structureSearchTriggerDistance.tooltip"))
                .setSaveConsumer(FabricModConfig::setStructureSearchTriggerDistance)
                .build());
        
        preGeneration.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.roadweaver.structureBatchSize"),
                FabricModConfig.getStructureBatchSize(),
                1, 50)
                .setDefaultValue(5)
                .setTooltip(Component.translatable("config.roadweaver.structureBatchSize.tooltip"))
                .setSaveConsumer(FabricModConfig::setStructureBatchSize)
                .build());
        
        preGeneration.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.roadweaver.structureSearchThreads"),
                FabricModConfig.getStructureSearchThreads(),
                1, 8)
                .setDefaultValue(3)
                .setTooltip(Component.translatable("config.roadweaver.structureSearchThreads.tooltip"))
                .setSaveConsumer(FabricModConfig::setStructureSearchThreads)
                .build());
        
        preGeneration.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.enableAsyncStructureSearch"),
                FabricModConfig.getEnableAsyncStructureSearch())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.enableAsyncStructureSearch.tooltip"))
                .setSaveConsumer(FabricModConfig::setEnableAsyncStructureSearch)
                .build());
        
        // 道路配置分类
        ConfigCategory roads = builder.getOrCreateCategory(
                Component.translatable("config.roadweaver.category.roads"));
        
        roads.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.averagingRadius"),
                FabricModConfig.getAveragingRadius())
                .setDefaultValue(1)
                .setMin(0)
                .setMax(5)
                .setTooltip(Component.translatable("config.roadweaver.averagingRadius.tooltip"))
                .setSaveConsumer(FabricModConfig::setAveragingRadius)
                .build());
        
        roads.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.allowArtificial"),
                FabricModConfig.getAllowArtificial())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.allowArtificial.tooltip"))
                .setSaveConsumer(FabricModConfig::setAllowArtificial)
                .build());
        
        roads.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.allowNatural"),
                FabricModConfig.getAllowNatural())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.allowNatural.tooltip"))
                .setSaveConsumer(FabricModConfig::setAllowNatural)
                .build());
        
        roads.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.structureDistanceFromRoad"),
                FabricModConfig.getStructureDistanceFromRoad())
                .setDefaultValue(4)
                .setMin(3)
                .setMax(8)
                .setTooltip(Component.translatable("config.roadweaver.structureDistanceFromRoad.tooltip"))
                .setSaveConsumer(FabricModConfig::setStructureDistanceFromRoad)
                .build());
        
        roads.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.maxHeightDifference"),
                FabricModConfig.getMaxHeightDifference())
                .setDefaultValue(5)
                .setMin(3)
                .setMax(10)
                .setTooltip(Component.translatable("config.roadweaver.maxHeightDifference.tooltip"))
                .setSaveConsumer(FabricModConfig::setMaxHeightDifference)
                .build());
        
        roads.addEntry(entryBuilder.startIntField(
                Component.translatable("config.roadweaver.maxTerrainStability"),
                FabricModConfig.getMaxTerrainStability())
                .setDefaultValue(4)
                .setMin(2)
                .setMax(10)
                .setTooltip(Component.translatable("config.roadweaver.maxTerrainStability.tooltip"))
                .setSaveConsumer(FabricModConfig::setMaxTerrainStability)
                .build());
        
        // 装饰配置分类
        ConfigCategory decorations = builder.getOrCreateCategory(
                Component.translatable("config.roadweaver.category.decorations"));
        
        decorations.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.placeWaypoints"),
                FabricModConfig.getPlaceWaypoints())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.roadweaver.placeWaypoints.tooltip"))
                .setSaveConsumer(FabricModConfig::setPlaceWaypoints)
                .build());
        
        decorations.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.placeRoadFences"),
                FabricModConfig.getPlaceRoadFences())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeRoadFences.tooltip"))
                .setSaveConsumer(FabricModConfig::setPlaceRoadFences)
                .build());
        
        decorations.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.placeSwings"),
                FabricModConfig.getPlaceSwings())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeSwings.tooltip"))
                .setSaveConsumer(FabricModConfig::setPlaceSwings)
                .build());
        
        decorations.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.placeBenches"),
                FabricModConfig.getPlaceBenches())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeBenches.tooltip"))
                .setSaveConsumer(FabricModConfig::setPlaceBenches)
                .build());
        
        decorations.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.roadweaver.placeGloriettes"),
                FabricModConfig.getPlaceGloriettes())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.roadweaver.placeGloriettes.tooltip"))
                .setSaveConsumer(FabricModConfig::setPlaceGloriettes)
                .build());

        ConfigCategory mobeffects = builder.getOrCreateCategory(
                Component.translatable("config.roadweaver.category.mobeffects"));
        //效果
        mobeffects.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.roadweaver.powerOfTheRoadSpeedLevel"),
                        FabricModConfig.getPowerOfTheRoadSpeedLevel(),
                        0, 5) // 0~5级，根据需求
                .setDefaultValue(2)
                .setTooltip(Component.translatable("config.roadweaver.powerOfTheRoadSpeedLevel.tooltip"))
                .setSaveConsumer(FabricModConfig::setPowerOfTheRoadSpeedLevel)
                .build());

        mobeffects.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.roadweaver.powerOfTheRoadRegenerationLevel"),
                        FabricModConfig.getPowerOfTheRoadRegenerationLevel(),
                        0, 1) // 0~5级，根据需求
                .setDefaultValue(0)
                .setTooltip(Component.translatable("config.roadweaver.powerOfTheRoadRegenerationLevel.tooltip"))
                .setSaveConsumer(FabricModConfig::setPowerOfTheRoadRegenerationLevel)
                .build());
        mobeffects.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("config.roadweaver.powerOfTheRoadJumpLevel"),
                        FabricModConfig.getPowerOfTheRoadJumpLevel(),
                        0, 3) // 0~5级，根据需求
                .setDefaultValue(2)
                .setTooltip(Component.translatable("config.roadweaver.powerOfTheRoadJumpLevel.tooltip"))
                .setSaveConsumer(FabricModConfig::setPowerOfTheRoadJumpLevel)
                .build());

        return builder.build();
    }
}
