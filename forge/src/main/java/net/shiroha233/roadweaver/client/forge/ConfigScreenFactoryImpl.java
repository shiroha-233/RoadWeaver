/* 文件职责：构建 Forge 配置界面。 */
package net.shiroha233.roadweaver.client.forge;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.sub.*;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLDevicePreference;

import java.util.Locale;

/**
 * Forge 平台配置屏幕工厂实现
 */
public class ConfigScreenFactoryImpl {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.roadweaver.title"));

        PresetService.reload();
        ModConfig conf = ConfigService.get();
        ModConfig defaultConf = new ModConfig();
        builder.setSavingRunnable(ConfigService::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        buildStructurePredictionCategory(builder, eb, conf, defaultConf);
        
        buildPlanningCategory(builder, eb, conf, defaultConf);
        
        buildRoadGenerationCategory(builder, eb, conf, defaultConf);
        
        buildSurfaceSettingsCategory(builder, eb, conf, defaultConf);
        
        buildBridgeCategory(builder, eb, conf, defaultConf);
        
        buildRoadsideStructuresCategory(builder, eb, conf, defaultConf);

        buildRoadsideVillageCategory(builder, eb, conf, defaultConf);

        buildPerformanceCategory(builder, eb, conf, defaultConf);
        
        buildPathfindingCostsCategory(builder, eb, conf, defaultConf);
        
        buildClientCategory(builder, eb, conf, defaultConf);

        return builder.build();
    }

    private static void buildStructurePredictionCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.structure_filters"));
        StructurePredictionConfig cfg = conf.structurePrediction();
        StructurePredictionConfig def = defaultConf.structurePrediction();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.enable_prediction"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.enable_prediction.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.radius_chunks"), cfg.predictRadiusChunks())
                .setDefaultValue(def.predictRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setPredictRadiusChunks)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.biome_prefilter"), cfg.biomePrefilter())
                .setDefaultValue(def.biomePrefilter())
                .setTooltip(Component.translatable("config.roadweaver.biome_prefilter.tooltip"))
                .setSaveConsumer(cfg::setBiomePrefilter)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.structure_avoidance_enabled"), cfg.structureAvoidanceEnabled())
                .setDefaultValue(def.structureAvoidanceEnabled())
                .setTooltip(Component.translatable("config.roadweaver.structure_avoidance_enabled.tooltip"))
                .setSaveConsumer(cfg::setStructureAvoidanceEnabled)
                .build());

        category.addEntry(new OpenStructureSelectionEntry());
    }

    private static void buildPlanningCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.road_planning"));
        PlanningConfig cfg = conf.planning();
        PlanningConfig def = defaultConf.planning();

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.initial_plan_radius_chunks"), cfg.initialPlanRadiusChunks())
                .setDefaultValue(def.initialPlanRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.initial_plan_radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setInitialPlanRadiusChunks)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.dynamic_plan_enabled"), cfg.dynamicPlanEnabled())
                .setDefaultValue(def.dynamicPlanEnabled())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_enabled.tooltip"))
                .setSaveConsumer(cfg::setDynamicPlanEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks"), cfg.dynamicPlanRadiusChunks())
                .setDefaultValue(def.dynamicPlanRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setDynamicPlanRadiusChunks)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks"), cfg.dynamicPlanStrideChunks())
                .setDefaultValue(def.dynamicPlanStrideChunks())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks.tooltip"))
                .setMin(1).setMax(256)
                .setSaveConsumer(cfg::setDynamicPlanStrideChunks)
                .build());

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.planning_algorithm"),
                        PlanningConfig.PlanningAlgorithm.class, cfg.planningAlgorithm())
                .setDefaultValue(def.planningAlgorithm())
                .setTooltip(Component.translatable("config.roadweaver.planning_algorithm.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.planning_algorithm.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(cfg::setPlanningAlgorithm)
                .build());
    }


    private static void buildRoadGenerationCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.road_generation"));
        RoadAppearanceConfig cfg = conf.roadAppearance();
        RoadAppearanceConfig def = defaultConf.roadAppearance();
        StructurePredictionConfig structureCfg = conf.structurePrediction();
        StructurePredictionConfig structureDef = defaultConf.structurePrediction();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.roads_enabled"), cfg.roadsEnabled())
                .setDefaultValue(def.roadsEnabled())
                .setTooltip(Component.translatable("config.roadweaver.roads_enabled.tooltip"))
                .setSaveConsumer(cfg::setRoadsEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.allow_artificial"), cfg.allowArtificial())
                .setDefaultValue(def.allowArtificial())
                .setTooltip(Component.translatable("config.roadweaver.allow_artificial.tooltip"))
                .setSaveConsumer(cfg::setAllowArtificial)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.allow_natural"), cfg.allowNatural())
                .setDefaultValue(def.allowNatural())
                .setTooltip(Component.translatable("config.roadweaver.allow_natural.tooltip"))
                .setSaveConsumer(cfg::setAllowNatural)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.place_waypoints"), cfg.placeWaypoints())
                .setDefaultValue(def.placeWaypoints())
                .setTooltip(Component.translatable("config.roadweaver.place_waypoints.tooltip"))
                .setSaveConsumer(cfg::setPlaceWaypoints)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.spawn_cabin_enabled"), cfg.spawnCabinEnabled())
                .setDefaultValue(def.spawnCabinEnabled())
                .setTooltip(Component.translatable("config.roadweaver.spawn_cabin_enabled.tooltip"))
                .setSaveConsumer(cfg::setSpawnCabinEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.road_width"), cfg.roadWidth())
                .setDefaultValue(def.roadWidth())
                .setTooltip(Component.translatable("config.roadweaver.road_width.tooltip"))
                .setMin(0).setMax(15)
                .setSaveConsumer(cfg::setRoadWidth)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.village_road_offset"), structureCfg.villageRoadOffset())
                .setDefaultValue(structureDef.villageRoadOffset())
                .setTooltip(Component.translatable("config.roadweaver.village_road_offset.tooltip"))
                .setMin(0).setMax(64)
                .setSaveConsumer(structureCfg::setVillageRoadOffset)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.other_structure_road_offset"), structureCfg.otherStructureRoadOffset())
                .setDefaultValue(structureDef.otherStructureRoadOffset())
                .setTooltip(Component.translatable("config.roadweaver.other_structure_road_offset.tooltip"))
                .setMin(0).setMax(64)
                .setSaveConsumer(structureCfg::setOtherStructureRoadOffset)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.road_signs_enabled"), cfg.roadSignsEnabled())
                .setDefaultValue(def.roadSignsEnabled())
                .setTooltip(Component.translatable("config.roadweaver.road_signs_enabled.tooltip"))
                .setSaveConsumer(cfg::setRoadSignsEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.lamp_interval"), cfg.lampInterval())
                .setDefaultValue(def.lampInterval())
                .setTooltip(Component.translatable("config.roadweaver.lamp_interval.tooltip"))
                .setMin(1).setMax(2048)
                .setSaveConsumer(cfg::setLampInterval)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.road_clear_height"), cfg.roadClearHeight())
                .setDefaultValue(def.roadClearHeight())
                .setTooltip(Component.translatable("config.roadweaver.road_clear_height.tooltip"))
                .setMin(1).setMax(16)
                .setSaveConsumer(cfg::setRoadClearHeight)
                .build());

        category.addEntry(new OpenPresetEditorEntry());
    }

    private static void buildSurfaceSettingsCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_surface"));
        RoadAppearanceConfig cfg = conf.roadAppearance();
        RoadAppearanceConfig def = defaultConf.roadAppearance();

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.averaging_radius"), cfg.averagingRadius())
                .setDefaultValue(def.averagingRadius())
                .setTooltip(Component.translatable("config.roadweaver.averaging_radius.tooltip"))
                .setMin(0).setMax(64)
                .setSaveConsumer(cfg::setAveragingRadius)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.max_slope_step_per_two_segments"), cfg.maxSlopeStepPerTwoSegments())
                .setDefaultValue(def.maxSlopeStepPerTwoSegments())
                .setTooltip(Component.translatable("config.roadweaver.max_slope_step_per_two_segments.tooltip"))
                .setMin(0).setMax(8)
                .setSaveConsumer(cfg::setMaxSlopeStepPerTwoSegments)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.slope_limit_enabled"), cfg.slopeLimitEnabled())
                .setDefaultValue(def.slopeLimitEnabled())
                .setTooltip(Component.translatable("config.roadweaver.slope_limit_enabled.tooltip"))
                .setSaveConsumer(cfg::setSlopeLimitEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.prevent_trees_on_road"), cfg.preventTreesOnRoad())
                .setDefaultValue(def.preventTreesOnRoad())
                .setTooltip(Component.translatable("config.roadweaver.prevent_trees_on_road.tooltip"))
                .setSaveConsumer(cfg::setPreventTreesOnRoad)
                .build());
    }

    private static void buildBridgeCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.bridge"));
        BridgeConfig cfg = conf.bridge();
        BridgeConfig def = defaultConf.bridge();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.bridge_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.bridge_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_deck_clearance"), cfg.deckClearance())
                .setDefaultValue(def.deckClearance())
                .setTooltip(Component.translatable("config.roadweaver.bridge_deck_clearance.tooltip"))
                .setMin(1).setMax(8)
                .setSaveConsumer(cfg::setDeckClearance)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_max_length_blocks"), cfg.maxLengthBlocks())
                .setDefaultValue(def.maxLengthBlocks())
                .setTooltip(Component.translatable("config.roadweaver.bridge_max_length_blocks.tooltip"))
                .setMin(0).setMax(10000)
                .setSaveConsumer(cfg::setMaxLengthBlocks)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.bridge_use_buoys_instead"), cfg.useBuoysInstead())
                .setDefaultValue(def.useBuoysInstead())
                .setTooltip(Component.translatable("config.roadweaver.bridge_use_buoys_instead.tooltip"))
                .setSaveConsumer(cfg::setUseBuoysInstead)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.bridge_use_buoys_when_skipped"), cfg.useBuoysWhenSkipped())
                .setDefaultValue(def.useBuoysWhenSkipped())
                .setTooltip(Component.translatable("config.roadweaver.bridge_use_buoys_when_skipped.tooltip"))
                .setSaveConsumer(cfg::setUseBuoysWhenSkipped)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.buoy_interval_blocks"), cfg.buoyIntervalBlocks())
                .setDefaultValue(def.buoyIntervalBlocks())
                .setTooltip(Component.translatable("config.roadweaver.buoy_interval_blocks.tooltip"))
                .setMin(4).setMax(256)
                .setSaveConsumer(cfg::setBuoyIntervalBlocks)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_pier_interval"), cfg.pierInterval())
                .setDefaultValue(def.pierInterval())
                .setTooltip(Component.translatable("config.roadweaver.bridge_pier_interval.tooltip"))
                .setMin(3).setMax(32)
                .setSaveConsumer(cfg::setPierInterval)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_pier_width"), cfg.pierWidth())
                .setDefaultValue(def.pierWidth())
                .setTooltip(Component.translatable("config.roadweaver.bridge_pier_width.tooltip"))
                .setMin(1).setMax(3)
                .setSaveConsumer(cfg::setPierWidth)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_pier_max_height"), cfg.pierMaxHeight())
                .setDefaultValue(def.pierMaxHeight())
                .setTooltip(Component.translatable("config.roadweaver.bridge_pier_max_height.tooltip"))
                .setMin(6).setMax(64)
                .setSaveConsumer(cfg::setPierMaxHeight)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.bridge_keep_lamps"), cfg.keepLamps())
                .setDefaultValue(def.keepLamps())
                .setTooltip(Component.translatable("config.roadweaver.bridge_keep_lamps.tooltip"))
                .setSaveConsumer(cfg::setKeepLamps)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_ramp_segments"), cfg.rampSegments())
                .setDefaultValue(def.rampSegments())
                .setTooltip(Component.translatable("config.roadweaver.bridge_ramp_segments.tooltip"))
                .setMin(0).setMax(12)
                .setSaveConsumer(cfg::setRampSegments)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_min_water_depth"), cfg.minWaterDepth())
                .setDefaultValue(def.minWaterDepth())
                .setTooltip(Component.translatable("config.roadweaver.bridge_min_water_depth.tooltip"))
                .setMin(1).setMax(10)
                .setSaveConsumer(cfg::setMinWaterDepth)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_min_length"), cfg.minLength())
                .setDefaultValue(def.minLength())
                .setTooltip(Component.translatable("config.roadweaver.bridge_min_length.tooltip"))
                .setMin(1).setMax(32)
                .setSaveConsumer(cfg::setMinLength)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_merge_gap"), cfg.mergeGap())
                .setDefaultValue(def.mergeGap())
                .setTooltip(Component.translatable("config.roadweaver.bridge_merge_gap.tooltip"))
                .setMin(1).setMax(32)
                .setSaveConsumer(cfg::setMergeGap)
                .build());
    }

    private static void buildRoadsideStructuresCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.roadside_structures"));
        RoadsideStructureConfig cfg = conf.roadsideStructure();
        RoadsideStructureConfig def = defaultConf.roadsideStructure();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.roadside_structures_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.roadside_structures_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.max_structures_per_road"), cfg.maxStructuresPerRoad())
                .setDefaultValue(def.maxStructuresPerRoad())
                .setTooltip(Component.translatable("config.roadweaver.max_structures_per_road.tooltip"))
                .setMin(0).setMax(20)
                .setSaveConsumer(cfg::setMaxStructuresPerRoad)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.small_structure_offset"), cfg.smallStructureOffset())
                .setDefaultValue(def.smallStructureOffset())
                .setTooltip(Component.translatable("config.roadweaver.small_structure_offset.tooltip"))
                .setMin(1).setMax(64)
                .setSaveConsumer(cfg::setSmallStructureOffset)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.medium_structure_offset"), cfg.mediumStructureOffset())
                .setDefaultValue(def.mediumStructureOffset())
                .setTooltip(Component.translatable("config.roadweaver.medium_structure_offset.tooltip"))
                .setMin(1).setMax(64)
                .setSaveConsumer(cfg::setMediumStructureOffset)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.large_structure_offset"), cfg.largeStructureOffset())
                .setDefaultValue(def.largeStructureOffset())
                .setTooltip(Component.translatable("config.roadweaver.large_structure_offset.tooltip"))
                .setMin(1).setMax(64)
                .setSaveConsumer(cfg::setLargeStructureOffset)
                .build());
    }

    private static void buildRoadsideVillageCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.roadside_village"));
        RoadsideVillageConfig cfg = conf.roadsideVillage();
        RoadsideVillageConfig def = defaultConf.roadsideVillage();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.roadside_village_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.roadside_village_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.roadside_village_spawn_chance"), cfg.spawnChance())
                .setDefaultValue(def.spawnChance())
                .setTooltip(Component.translatable("config.roadweaver.roadside_village_spawn_chance.tooltip"))
                .setMin(0.0D).setMax(1.0D)
                .setSaveConsumer(cfg::setSpawnChance)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.roadside_village_max_per_road"), cfg.maxVillagesPerRoad())
                .setDefaultValue(def.maxVillagesPerRoad())
                .setTooltip(Component.translatable("config.roadweaver.roadside_village_max_per_road.tooltip"))
                .setMin(0).setMax(RoadConstants.ROADSIDE_VILLAGE_MAX_PER_ROAD_MAX)
                .setSaveConsumer(cfg::setMaxVillagesPerRoad)
                .build());

    }

    private static void buildPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_performance"));
        PerformanceConfig cfg = conf.performance();
        PerformanceConfig def = defaultConf.performance();
        PlanningConfig planning = conf.planning();
        PlanningConfig planningDef = defaultConf.planning();

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.terrain_sampling_mode"),
                        TerrainSamplingMode.class, planning.terrainSamplingMode())
                .setDefaultValue(planningDef.terrainSamplingMode())
                .setTooltip(Component.translatable("config.roadweaver.terrain_sampling_mode.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.terrain_sampling_mode.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(planning::setTerrainSamplingMode)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.initial_generation_threads"), cfg.initialGenerationThreads())
                .setDefaultValue(def.initialGenerationThreads())
                .setTooltip(Component.translatable("config.roadweaver.initial_generation_threads.tooltip"))
                .setMin(1).setMax(128)
                .setSaveConsumer(cfg::setInitialGenerationThreads)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.shared_worker_threads"), cfg.sharedWorkerThreads())
                .setDefaultValue(def.sharedWorkerThreads())
                .setTooltip(Component.translatable("config.roadweaver.shared_worker_threads.tooltip"))
                .setMin(1).setMax(128)
                .setSaveConsumer(cfg::setSharedWorkerThreads)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.idle_generation_enabled"), cfg.idleGenerationEnabled())
                .setDefaultValue(def.idleGenerationEnabled())
                .setTooltip(Component.translatable("config.roadweaver.idle_generation_enabled.tooltip"))
                .setSaveConsumer(cfg::setIdleGenerationEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.max_concurrent_generations"), cfg.maxConcurrentGenerations())
                .setDefaultValue(def.maxConcurrentGenerations())
                .setTooltip(Component.translatable("config.roadweaver.max_concurrent_generations.tooltip"))
                .setMin(1).setMax(128)
                .setSaveConsumer(cfg::setMaxConcurrentGenerations)
                .build());

        category.addEntry(eb
                .startIntSlider(Component.translatable("config.roadweaver.thread_duty_cycle"), cfg.threadDutyCycle(), 1, 100)
                .setDefaultValue(def.threadDutyCycle())
                .setTooltip(Component.translatable("config.roadweaver.thread_duty_cycle.tooltip"))
                .setSaveConsumer(cfg::setThreadDutyCycle)
                .build());

        category.addEntry(eb
                .startIntSlider(Component.translatable("config.roadweaver.idle_thread_duty_cycle"),
                        cfg.idleThreadDutyCycle(), 1, 100)
                .setDefaultValue(def.idleThreadDutyCycle())
                .setTooltip(Component.translatable("config.roadweaver.idle_thread_duty_cycle.tooltip"))
                .setSaveConsumer(cfg::setIdleThreadDutyCycle)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.opencl_coarse_sampling_enabled"), cfg.openclCoarseSamplingEnabled())
                .setDefaultValue(def.openclCoarseSamplingEnabled())
                .setTooltip(Component.translatable("config.roadweaver.opencl_coarse_sampling_enabled.tooltip"))
                .setSaveConsumer(cfg::setOpenclCoarseSamplingEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.opencl_accurate_sampling_enabled"), cfg.openclAccurateSamplingEnabled())
                .setDefaultValue(def.openclAccurateSamplingEnabled())
                .setTooltip(Component.translatable("config.roadweaver.opencl_accurate_sampling_enabled.tooltip"))
                .setSaveConsumer(cfg::setOpenclAccurateSamplingEnabled)
                .build());

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.opencl_device_preference"),
                        OpenCLDevicePreference.class, OpenCLDevicePreference.valueOf(cfg.openclDevicePreference()))
                .setDefaultValue(OpenCLDevicePreference.valueOf(def.openclDevicePreference()))
                .setTooltip(Component.translatable("config.roadweaver.opencl_device_preference.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.opencl_device_preference.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(v -> cfg.setOpenclDevicePreference(v.name()))
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.opencl_min_samples"), cfg.openclMinSamples())
                .setDefaultValue(def.openclMinSamples())
                .setTooltip(Component.translatable("config.roadweaver.opencl_min_samples.tooltip"))
                .setMin(0).setMax(RoadConstants.COARSE_REGION_MAX_SAMPLES)
                .setSaveConsumer(cfg::setOpenclMinSamples)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.opencl_validate_samples"), cfg.openclValidateSamples())
                .setDefaultValue(def.openclValidateSamples())
                .setTooltip(Component.translatable("config.roadweaver.opencl_validate_samples.tooltip"))
                .setSaveConsumer(cfg::setOpenclValidateSamples)
                .build());
    }

    private static void buildPathfindingCostsCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.pathfinding_costs"));
        PathfindingCostConfig cfg = conf.pathfindingCost();
        PathfindingCostConfig def = defaultConf.pathfindingCost();

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.pathfinding_algorithm"),
                        PathfindingCostConfig.PathfindingAlgorithm.class, cfg.pathfindingAlgorithm())
                .setDefaultValue(def.pathfindingAlgorithm())
                .setTooltip(Component.translatable("config.roadweaver.pathfinding_algorithm.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.pathfinding_algorithm.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(cfg::setPathfindingAlgorithm)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.a_star_step"), cfg.aStarStep())
                .setDefaultValue(def.aStarStep())
                .setTooltip(Component.translatable("config.roadweaver.a_star_step.tooltip"))
                .setMin(4).setMax(128)
                .setSaveConsumer(cfg::setAStarStep)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.a_star_max_steps"), cfg.aStarMaxSteps())
                .setDefaultValue(def.aStarMaxSteps())
                .setTooltip(Component.translatable("config.roadweaver.a_star_max_steps.tooltip"))
                .setMin(100).setMax(RoadConstants.ASTAR_MAX_STEPS_MAX)
                .setSaveConsumer(cfg::setAStarMaxSteps)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.ortho_step_cost"), cfg.orthoStepCost())
                .setDefaultValue(def.orthoStepCost())
                .setTooltip(Component.translatable("config.roadweaver.ortho_step_cost.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setOrthoStepCost)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.diag_step_cost"), cfg.diagStepCost())
                .setDefaultValue(def.diagStepCost())
                .setTooltip(Component.translatable("config.roadweaver.diag_step_cost.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setDiagStepCost)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.elevation_weight"), cfg.elevationWeight())
                .setDefaultValue(def.elevationWeight())
                .setTooltip(Component.translatable("config.roadweaver.elevation_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setElevationWeight)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.biome_weight"), cfg.biomeWeight())
                .setDefaultValue(def.biomeWeight())
                .setTooltip(Component.translatable("config.roadweaver.biome_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setBiomeWeight)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.stability_weight"), cfg.stabilityWeight())
                .setDefaultValue(def.stabilityWeight())
                .setTooltip(Component.translatable("config.roadweaver.stability_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setStabilityWeight)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.water_depth_weight"), cfg.waterDepthWeight())
                .setDefaultValue(def.waterDepthWeight())
                .setTooltip(Component.translatable("config.roadweaver.water_depth_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setWaterDepthWeight)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.near_water_cost"), cfg.nearWaterCost())
                .setDefaultValue(def.nearWaterCost())
                .setTooltip(Component.translatable("config.roadweaver.near_water_cost.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setNearWaterCost)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.water_proximity_cost"), cfg.waterProximityCost())
                .setDefaultValue(def.waterProximityCost())
                .setTooltip(Component.translatable("config.roadweaver.water_proximity_cost.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setWaterProximityCost)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.heuristic_weight"), cfg.heuristicWeight())
                .setDefaultValue(def.heuristicWeight())
                .setTooltip(Component.translatable("config.roadweaver.heuristic_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setHeuristicWeight)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.deviation_weight"), cfg.deviationWeight())
                .setDefaultValue(def.deviationWeight())
                .setTooltip(Component.translatable("config.roadweaver.deviation_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setDeviationWeight)
                .build());
    }

    private static void buildClientCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.test"));
        ClientConfig cfg = conf.client();
        ClientConfig def = defaultConf.client();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.loading_tips_enabled"), cfg.loadingTipsEnabled())
                .setDefaultValue(def.loadingTipsEnabled())
                .setTooltip(Component.translatable("config.roadweaver.loading_tips_enabled.tooltip"))
                .setSaveConsumer(cfg::setLoadingTipsEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.loading_progress_enabled"), cfg.loadingProgressEnabled())
                .setDefaultValue(def.loadingProgressEnabled())
                .setTooltip(Component.translatable("config.roadweaver.loading_progress_enabled.tooltip"))
                .setSaveConsumer(cfg::setLoadingProgressEnabled)
                .build());
    }
}
