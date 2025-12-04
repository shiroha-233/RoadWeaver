package net.shiroha233.roadweaver.client.forge;

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
 * Forge 平台的配置屏幕工厂实现
 */
public class ConfigScreenFactoryImpl {

        /**
         * 创建配置屏幕 (Forge实现)
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
                                                .setSaveConsumer(v -> { if (v != null) conf.setVillagePredictionEnabled(v); })
                                                .build());

                filters.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.radius_chunks"),
                                                conf.predictRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setPredictRadiusChunks(v); })
                                                .build());

                filters.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.biome_prefilter"),
                                                conf.biomePrefilter())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.biome_prefilter.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBiomePrefilter(v); })
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

                // 路网规划分类
                ConfigCategory planning = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_planning"));

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.initial_plan_radius_chunks"),
                                                conf.initialPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.initial_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setInitialPlanRadiusChunks(v); })
                                                .build());

                planning.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.dynamic_plan_enabled"),
                                                conf.dynamicPlanEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanEnabled(v); })
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks"),
                                                conf.dynamicPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanRadiusChunks(v); })
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks"),
                                                conf.dynamicPlanStrideChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_stride_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanStrideChunks(v); })
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
                                                .setSaveConsumer(v -> { if (v != null) conf.setPlanningAlgorithm(v); })
                                                .build());

                ConfigCategory roadGen = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_generation"));

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_artificial"),
                                                conf.allowArtificial())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_artificial.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setAllowArtificial(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_natural"),
                                                conf.allowNatural())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_natural.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setAllowNatural(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.place_waypoints"),
                                                conf.placeWaypoints())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.place_waypoints.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPlaceWaypoints(v); })
                                                .build());

                // 新增：道路宽度（0=自动）
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_width"),
                                                conf.roadWidth())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.road_width.tooltip"))
                                                .setMin(0).setMax(15)
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadWidth(v); })
                                                .build());

                // 村庄缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.village_road_offset"),
                                                conf.villageRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.village_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setVillageRoadOffset(v); })
                                                .build());

                // 其他结构缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.other_structure_road_offset"),
                                                conf.otherStructureRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.other_structure_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setOtherStructureRoadOffset(v); })
                                                .build());

                // 结构避让开关
                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.structure_avoidance_enabled"),
                                                conf.structureAvoidanceEnabled())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.structure_avoidance_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setStructureAvoidanceEnabled(v); })
                                                .build());

                // 新增：路灯间隔（段）
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.lamp_interval"),
                                                conf.lampInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.lamp_interval.tooltip"))
                                                .setMin(1).setMax(2048)
                                                .setSaveConsumer(v -> { if (v != null) conf.setLampInterval(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_clear_height"),
                                                conf.roadClearHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.road_clear_height.tooltip"))
                                                .setMin(1).setMax(16)
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadClearHeight(v); })
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
                                                .setSaveConsumer(v -> { if (v != null) conf.setAveragingRadius(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments"),
                                                conf.maxSlopeStepPerTwoSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments.tooltip"))
                                                .setMin(0).setMax(8)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxSlopeStepPerTwoSegments(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.slope_limit_enabled"),
                                                conf.slopeLimitEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.slope_limit_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setSlopeLimitEnabled(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.causeway_max_depth"),
                                                conf.causewayMaxDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.causeway_max_depth.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(v -> { if (v != null) conf.setCausewayMaxDepth(v); })
                                                .build());
                genSurface.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.prevent_trees_on_road"),
                                                conf.preventTreesOnRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.prevent_trees_on_road.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPreventTreesOnRoad(v); })
                                                .build());

                // 桥梁设置
                ConfigCategory bridge = builder
                                .getOrCreateCategory(Component.translatable("config.roadweaver.category.bridge"));
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_enabled"),
                                                conf.bridgeEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeEnabled(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_deck_clearance"),
                                                conf.bridgeDeckClearance())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_deck_clearance.tooltip"))
                                                .setMin(1).setMax(8)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeDeckClearance(v); })
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.bridge_railing_enabled"),
                                                conf.bridgeRailingEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_railing_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeRailingEnabled(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_interval"),
                                                conf.bridgePierInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_interval.tooltip"))
                                                .setMin(3).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierInterval(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_width"),
                                                conf.bridgePierWidth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_width.tooltip"))
                                                .setMin(1).setMax(3)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierWidth(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_max_height"),
                                                conf.bridgePierMaxHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_max_height.tooltip"))
                                                .setMin(6).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierMaxHeight(v); })
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_keep_lamps"),
                                                conf.bridgeKeepLamps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_keep_lamps.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeKeepLamps(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_ramp_segments"),
                                                conf.bridgeRampSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_ramp_segments.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeRampSegments(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_water_depth"),
                                                conf.bridgeMinWaterDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_water_depth.tooltip"))
                                                .setMin(1).setMax(10)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMinWaterDepth(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_length"),
                                                conf.bridgeMinLength())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_length.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMinLength(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_merge_gap"),
                                                conf.bridgeMergeGap())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_merge_gap.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMergeGap(v); })
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
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadsideStructuresEnabled(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.max_structures_per_road"),
                                                conf.maxStructuresPerRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_structures_per_road.tooltip"))
                                                .setMin(0).setMax(20)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxStructuresPerRoad(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.min_structure_spacing"),
                                                conf.minStructureSpacing())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.min_structure_spacing.tooltip"))
                                                .setMin(1).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMinStructureSpacing(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.small_structure_offset"),
                                                conf.smallStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.small_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setSmallStructureOffset(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.medium_structure_offset"),
                                                conf.mediumStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.medium_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMediumStructureOffset(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.large_structure_offset"),
                                                conf.largeStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.large_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setLargeStructureOffset(v); })
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
                                                .setSaveConsumer(v -> { if (v != null) conf.setComputeThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads"),
                                                conf.generationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setGenerationThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable(
                                                "text.autoconfig.roadweaver.option.initialGenerationThreads"),
                                                conf.initialGenerationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.initialGenerationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setInitialGenerationThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_step"),
                                                conf.aStarStep())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.a_star_step.tooltip"))
                                                .setMin(4).setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setAStarStep(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_max_steps"),
                                                conf.aStarMaxSteps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.a_star_max_steps.tooltip"))
                                                .setMin(100).setMax(100000)
                                                .setSaveConsumer(v -> { if (v != null) conf.setAStarMaxSteps(v); })
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
										.setSaveConsumer(v -> { if (v != null) conf.setPathfindingAlgorithm(v); })
										.build());

				genPerformance.addEntry(
									eb.startIntField(Component.translatable("config.roadweaver.max_concurrent_generations"),
                                                conf.maxConcurrentGenerations())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_concurrent_generations.tooltip"))
                                                .setMin(1).setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxConcurrentGenerations(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntSlider(Component.translatable("config.roadweaver.thread_duty_cycle"),
                                                conf.threadDutyCycle(), 1, 100)
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.thread_duty_cycle.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setThreadDutyCycle(v); })
                                                .build());

                ConfigCategory pathfindingCosts = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.pathfinding_costs"));

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.ortho_step_cost"),
                                                conf.orthoStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.ortho_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setOrthoStepCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.diag_step_cost"),
                                                conf.diagStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.diag_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDiagStepCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.elevation_weight"),
                                                conf.elevationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.elevation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setElevationWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.biome_weight"),
                                                conf.biomeWeight())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.biome_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBiomeWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.stability_weight"),
                                                conf.stabilityWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.stability_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setStabilityWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_depth_weight"),
                                                conf.waterDepthWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_depth_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setWaterDepthWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.near_water_cost"),
                                                conf.nearWaterCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.near_water_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setNearWaterCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_proximity_cost"),
                                                conf.waterProximityCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_proximity_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setWaterProximityCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.heuristic_weight"),
                                                conf.heuristicWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.heuristic_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHeuristicWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.deviation_weight"),
                                                conf.deviationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.deviation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDeviationWeight(v); })
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
