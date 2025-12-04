package net.shiroha233.roadweaver.client.fabric;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Fabric 平台的配置屏幕工厂实现
 */
public class ConfigScreenFactoryImpl {

        /**
         * 创建配置屏幕 (Fabric实现)
         * 
         * @param parent 父屏幕
         * @return 配置屏幕实例
         */
        public static Screen createConfigScreen(Screen parent) {
                ConfigBuilder builder = ConfigBuilder.create()
                                .setParentScreen(parent)
                                .setTitle(Component.translatable("config.roadweaver.title"));

                PresetService.reload();
                ModConfig conf = ConfigService.get();
                builder.setSavingRunnable(ConfigService::save);

                ConfigCategory filters = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.structure_filters"));
                ConfigEntryBuilder eb = builder.entryBuilder();

                filters.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.enable_prediction"),
                                                conf.villagePredictionEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.enable_prediction.tooltip"))
                                                .setSaveConsumer(conf::setVillagePredictionEnabled)
                                                .build());

                filters.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.radius_chunks"),
                                                conf.predictRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(conf::setPredictRadiusChunks)
                                                .build());

                filters.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.biome_prefilter"),
                                                conf.biomePrefilter())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.biome_prefilter.tooltip"))
                                                .setSaveConsumer(conf::setBiomePrefilter)
                                                .build());

                List<String> whitelist = new ArrayList<>(
                                conf.structureWhitelist() == null ? List.of() : conf.structureWhitelist());
                List<String> blacklist = new ArrayList<>(
                                conf.structureBlacklist() == null ? List.of() : conf.structureBlacklist());

                filters.addEntry(
                                eb.startStrList(Component.translatable("config.roadweaver.whitelist"), whitelist)
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.whitelist.tooltip"))
                                                .setSaveConsumer(list -> conf.setStructureWhitelist(normalize(list)))
                                                .build());

                filters.addEntry(
                                eb.startStrList(Component.translatable("config.roadweaver.blacklist"), blacklist)
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.blacklist.tooltip"))
                                                .setSaveConsumer(list -> conf.setStructureBlacklist(normalize(list)))
                                                .build());

                ConfigCategory planning = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_planning"));

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.initial_plan_radius_chunks"),
                                                conf.initialPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.initial_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(conf::setInitialPlanRadiusChunks)
                                                .build());

                planning.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.dynamic_plan_enabled"),
                                                conf.dynamicPlanEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_enabled.tooltip"))
                                                .setSaveConsumer(conf::setDynamicPlanEnabled)
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks"),
                                                conf.dynamicPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(conf::setDynamicPlanRadiusChunks)
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks"),
                                                conf.dynamicPlanStrideChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_stride_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(256)
                                                .setSaveConsumer(conf::setDynamicPlanStrideChunks)
                                                .build());

                ModConfig.PlanningAlgorithm planningAlgo =
                                conf.planningAlgorithm() != null ? conf.planningAlgorithm() : ModConfig.PlanningAlgorithm.RNG;

                planning.addEntry(
                                eb.startEnumSelector(
                                                Component.translatable("config.roadweaver.planning_algorithm"),
                                                ModConfig.PlanningAlgorithm.class,
                                                planningAlgo)
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.planning_algorithm.tooltip"))
                                                .setEnumNameProvider(v -> Component.translatable(
                                                                "config.roadweaver.planning_algorithm.option."
                                                                                + v.name().toLowerCase(Locale.ROOT)))
                                                .setSaveConsumer(conf::setPlanningAlgorithm)
                                                .build());

                ConfigCategory roadGen = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_generation"));

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_artificial"),
                                                conf.allowArtificial())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_artificial.tooltip"))
                                                .setSaveConsumer(conf::setAllowArtificial)
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_natural"),
                                                conf.allowNatural())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_natural.tooltip"))
                                                .setSaveConsumer(conf::setAllowNatural)
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.place_waypoints"),
                                                conf.placeWaypoints())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.place_waypoints.tooltip"))
                                                .setSaveConsumer(conf::setPlaceWaypoints)
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_width"),
                                                conf.roadWidth())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.road_width.tooltip"))
                                                .setMin(0).setMax(15)
                                                .setSaveConsumer(conf::setRoadWidth)
                                                .build());

                // 村庄缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.village_road_offset"),
                                                conf.villageRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.village_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(conf::setVillageRoadOffset)
                                                .build());

                // 其他结构缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.other_structure_road_offset"),
                                                conf.otherStructureRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.other_structure_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(conf::setOtherStructureRoadOffset)
                                                .build());

                // 结构避让开关
                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.structure_avoidance_enabled"),
                                                conf.structureAvoidanceEnabled())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.structure_avoidance_enabled.tooltip"))
                                                .setSaveConsumer(conf::setStructureAvoidanceEnabled)
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.lamp_interval"),
                                                conf.lampInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.lamp_interval.tooltip"))
                                                .setMin(1).setMax(2048)
                                                .setSaveConsumer(conf::setLampInterval)
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_clear_height"),
                                                conf.roadClearHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.road_clear_height.tooltip"))
                                                .setMin(1).setMax(16)
                                                .setSaveConsumer(conf::setRoadClearHeight)
                                                .build());

                roadGen.addEntry(new OpenPresetEditorEntry());

                ConfigCategory genSurface = builder
                                .getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_surface"));

                genSurface.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.averaging_radius"),
                                                conf.averagingRadius())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.averaging_radius.tooltip"))
                                                .setMin(0).setMax(64)
                                                .setSaveConsumer(conf::setAveragingRadius)
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments"),
                                                conf.maxSlopeStepPerTwoSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments.tooltip"))
                                                .setMin(0).setMax(8)
                                                .setSaveConsumer(conf::setMaxSlopeStepPerTwoSegments)
                                                .build());

                genSurface.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.slope_limit_enabled"),
                                                conf.slopeLimitEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.slope_limit_enabled.tooltip"))
                                                .setSaveConsumer(conf::setSlopeLimitEnabled)
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.causeway_max_depth"),
                                                conf.causewayMaxDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.causeway_max_depth.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(conf::setCausewayMaxDepth)
                                                .build());
                genSurface.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.prevent_trees_on_road"),
                                                conf.preventTreesOnRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.prevent_trees_on_road.tooltip"))
                                                .setSaveConsumer(conf::setPreventTreesOnRoad)
                                                .build());

                // 桥梁设置
                ConfigCategory bridge = builder
                                .getOrCreateCategory(Component.translatable("config.roadweaver.category.bridge"));
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_enabled"),
                                                conf.bridgeEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_enabled.tooltip"))
                                                .setSaveConsumer(conf::setBridgeEnabled)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_deck_clearance"),
                                                conf.bridgeDeckClearance())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_deck_clearance.tooltip"))
                                                .setMin(1).setMax(8)
                                                .setSaveConsumer(conf::setBridgeDeckClearance)
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.bridge_railing_enabled"),
                                                conf.bridgeRailingEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_railing_enabled.tooltip"))
                                                .setSaveConsumer(conf::setBridgeRailingEnabled)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_interval"),
                                                conf.bridgePierInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_interval.tooltip"))
                                                .setMin(3).setMax(32)
                                                .setSaveConsumer(conf::setBridgePierInterval)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_width"),
                                                conf.bridgePierWidth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_width.tooltip"))
                                                .setMin(1).setMax(3)
                                                .setSaveConsumer(conf::setBridgePierWidth)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_max_height"),
                                                conf.bridgePierMaxHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_max_height.tooltip"))
                                                .setMin(6).setMax(64)
                                                .setSaveConsumer(conf::setBridgePierMaxHeight)
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_keep_lamps"),
                                                conf.bridgeKeepLamps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_keep_lamps.tooltip"))
                                                .setSaveConsumer(conf::setBridgeKeepLamps)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_ramp_segments"),
                                                conf.bridgeRampSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_ramp_segments.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(conf::setBridgeRampSegments)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_water_depth"),
                                                conf.bridgeMinWaterDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_water_depth.tooltip"))
                                                .setMin(1).setMax(10)
                                                .setSaveConsumer(conf::setBridgeMinWaterDepth)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_length"),
                                                conf.bridgeMinLength())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_length.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(conf::setBridgeMinLength)
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_merge_gap"),
                                                conf.bridgeMergeGap())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_merge_gap.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(conf::setBridgeMergeGap)
                                                .build());

                // 路边结构设置
                ConfigCategory roadsideStructures = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.roadside_structures"));
                roadsideStructures.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.roadside_structures_enabled"),
                                                conf.roadsideStructuresEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.roadside_structures_enabled.tooltip"))
                                                .setSaveConsumer(conf::setRoadsideStructuresEnabled)
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.max_structures_per_road"),
                                                conf.maxStructuresPerRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_structures_per_road.tooltip"))
                                                .setMin(0).setMax(20)
                                                .setSaveConsumer(conf::setMaxStructuresPerRoad)
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.min_structure_spacing"),
                                                conf.minStructureSpacing())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.min_structure_spacing.tooltip"))
                                                .setMin(1).setMax(256)
                                                .setSaveConsumer(conf::setMinStructureSpacing)
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.small_structure_offset"),
                                                conf.smallStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.small_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(conf::setSmallStructureOffset)
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.medium_structure_offset"),
                                                conf.mediumStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.medium_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(conf::setMediumStructureOffset)
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.large_structure_offset"),
                                                conf.largeStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.large_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(conf::setLargeStructureOffset)
                                                .build());

                ConfigCategory genPerformance = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.gen_performance"));

                genPerformance.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "text.autoconfig.roadweaver.option.computeThreads"),
                                                conf.computeThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.computeThreads.@Tooltip"))
                                                .setMin(0).setMax(128)
                                                .setSaveConsumer(conf::setComputeThreads)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads"),
                                                conf.generationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(conf::setGenerationThreads)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable(
                                                "text.autoconfig.roadweaver.option.initialGenerationThreads"),
                                                conf.initialGenerationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.initialGenerationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(conf::setInitialGenerationThreads)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_step"),
                                                conf.aStarStep())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.a_star_step.tooltip"))
                                                .setMin(4).setMax(128)
                                                .setSaveConsumer(conf::setAStarStep)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_max_steps"),
                                                conf.aStarMaxSteps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.a_star_max_steps.tooltip"))
                                                .setMin(100).setMax(100000)
                                                .setSaveConsumer(conf::setAStarMaxSteps)
                                                .build());

                genPerformance.addEntry(
                                eb.startEnumSelector(
                                                Component.translatable("config.roadweaver.pathfinding_algorithm"),
                                                ModConfig.PathfindingAlgorithm.class,
                                                conf.pathfindingAlgorithm())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.pathfinding_algorithm.tooltip"))
                                                .setEnumNameProvider(v -> Component.translatable(
                                                                "config.roadweaver.pathfinding_algorithm.option."
                                                                                + v.name().toLowerCase(Locale.ROOT)))
                                                .setSaveConsumer(conf::setPathfindingAlgorithm)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.max_concurrent_generations"),
                                                conf.maxConcurrentGenerations())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_concurrent_generations.tooltip"))
                                                .setMin(1).setMax(128)
                                                .setSaveConsumer(conf::setMaxConcurrentGenerations)
                                                .build());

                genPerformance.addEntry(
                                eb.startIntSlider(Component.translatable("config.roadweaver.thread_duty_cycle"),
                                                conf.threadDutyCycle(), 1, 100)
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.thread_duty_cycle.tooltip"))
                                                .setSaveConsumer(conf::setThreadDutyCycle)
                                                .build());

                ConfigCategory pathfindingCosts = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.pathfinding_costs"));

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.ortho_step_cost"),
                                                conf.orthoStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.ortho_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setOrthoStepCost)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.diag_step_cost"),
                                                conf.diagStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.diag_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setDiagStepCost)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.elevation_weight"),
                                                conf.elevationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.elevation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setElevationWeight)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.biome_weight"),
                                                conf.biomeWeight())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.biome_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setBiomeWeight)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.stability_weight"),
                                                conf.stabilityWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.stability_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setStabilityWeight)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_depth_weight"),
                                                conf.waterDepthWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_depth_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setWaterDepthWeight)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.near_water_cost"),
                                                conf.nearWaterCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.near_water_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setNearWaterCost)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_proximity_cost"),
                                                conf.waterProximityCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_proximity_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setWaterProximityCost)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.heuristic_weight"),
                                                conf.heuristicWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.heuristic_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setHeuristicWeight)
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.deviation_weight"),
                                                conf.deviationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.deviation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(conf::setDeviationWeight)
                                                .build());

                return builder.build();
        }

        private static List<String> normalize(List<String> src) {
                if (src == null)
                        return List.of();
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (String s : src) {
                        if (s == null)
                                continue;
                        String v = s.trim().toLowerCase(Locale.ROOT);
                        if (v.isEmpty())
                                continue;
                        set.add(v);
                }
                return new ArrayList<>(set);
        }
}
