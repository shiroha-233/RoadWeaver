package net.shiroha233.roadweaver.config;

import net.shiroha233.roadweaver.config.sub.*;

import java.util.List;

/**
 * 模组配置聚合类，通过组合持有所有子配置实例
 */
public final class ModConfig {

    private StructurePredictionConfig structurePrediction = new StructurePredictionConfig();
    private PlanningConfig planning = new PlanningConfig();
    private PathfindingCostConfig pathfindingCost = new PathfindingCostConfig();
    private RoadAppearanceConfig roadAppearance = new RoadAppearanceConfig();
    private BridgeConfig bridge = new BridgeConfig();

    private PerformanceConfig performance = new PerformanceConfig();
    private RoadsideStructureConfig roadsideStructure = new RoadsideStructureConfig();
    private RoadsideVillageConfig roadsideVillage = new RoadsideVillageConfig();
    private ClientConfig client = new ClientConfig();

    public void sanitize() {
        if (structurePrediction == null) structurePrediction = new StructurePredictionConfig();
        if (planning == null) planning = new PlanningConfig();
        if (pathfindingCost == null) pathfindingCost = new PathfindingCostConfig();
        if (roadAppearance == null) roadAppearance = new RoadAppearanceConfig();
        if (bridge == null) bridge = new BridgeConfig();
        if (performance == null) performance = new PerformanceConfig();
        if (roadsideStructure == null) roadsideStructure = new RoadsideStructureConfig();
        if (roadsideVillage == null) roadsideVillage = new RoadsideVillageConfig();
        if (client == null) client = new ClientConfig();

        structurePrediction.sanitize();
        planning.sanitize();
        pathfindingCost.sanitize();
        roadAppearance.sanitize();
        bridge.sanitize();
        performance.sanitize();
        roadsideStructure.sanitize();
        roadsideVillage.sanitize();
        client.sanitize();

    }


    public StructurePredictionConfig structurePrediction() { return structurePrediction; }
    public PlanningConfig planning() { return planning; }
    public PathfindingCostConfig pathfindingCost() { return pathfindingCost; }
    public RoadAppearanceConfig roadAppearance() { return roadAppearance; }
    public BridgeConfig bridge() { return bridge; }

    public PerformanceConfig performance() { return performance; }
    public RoadsideStructureConfig roadsideStructure() { return roadsideStructure; }
    public RoadsideVillageConfig roadsideVillage() { return roadsideVillage; }
    public ClientConfig client() { return client; }

    public boolean loadingTipsEnabled() {
        return client.loadingTipsEnabled();
    }

    public boolean loadingProgressEnabled() {
        return client.loadingProgressEnabled();
    }

    public boolean tunnelEnabled() {
        return roadAppearance.tunnelEnabled();
    }

    public int roadClearHeight() {
        return roadAppearance.roadClearHeight();
    }

    public int tunnelClearHeight() {
        return roadAppearance.tunnelClearHeight();
    }

    public int averagingRadius() {
        return roadAppearance.averagingRadius();
    }

    public boolean structurePredictionEnabled() {
        return structurePrediction.enabled();
    }

    public boolean structureAvoidanceEnabled() {
        return structurePrediction.structureAvoidanceEnabled();
    }

    public boolean bridgeEnabled() {
        return bridge.enabled();
    }

    public int bridgeMinLength() {
        return bridge.minLength();
    }

    public int bridgeMergeGap() {
        return bridge.mergeGap();
    }

    public int bridgeMaxLengthBlocks() {
        return bridge.maxLengthBlocks();
    }

    public boolean bridgeUseBuoysInstead() {
        return bridge.useBuoysInstead();
    }

    public boolean bridgeUseBuoysWhenSkipped() {
        return bridge.useBuoysWhenSkipped();
    }

    public int buoyIntervalBlocks() {
        return bridge.buoyIntervalBlocks();
    }

    public int bridgeDeckClearance() {
        return bridge.deckClearance();
    }

    public boolean bridgeKeepLamps() {
        return bridge.keepLamps();
    }

    public int bridgePierInterval() {
        return bridge.pierInterval();
    }

    public int bridgePierWidth() {
        return bridge.pierWidth();
    }

    public int bridgePierMaxHeight() {
        return bridge.pierMaxHeight();
    }

    public int bridgeRampSegments() {
        return bridge.rampSegments();
    }

    public int bridgeMinWaterDepth() {
        return bridge.minWaterDepth();
    }

    public int maxSlopeStepPerTwoSegments() {
        return roadAppearance.maxSlopeStepPerTwoSegments();
    }

    public boolean slopeLimitEnabled() {
        return roadAppearance.slopeLimitEnabled();
    }

    public int lampInterval() {
        return roadAppearance.lampInterval();
    }

    public int predictRadiusChunks() {
        return structurePrediction.predictRadiusChunks();
    }

    public boolean biomePrefilter() {
        return structurePrediction.biomePrefilter();
    }

    public List<String> structureWhitelist() {
        return structurePrediction.structureWhitelist();
    }

    public void setStructureWhitelist(List<String> v) {
        structurePrediction.setStructureWhitelist(v);
    }

    public List<String> structureBlacklist() {
        return structurePrediction.structureBlacklist();
    }

    public void setStructureBlacklist(List<String> v) {
        structurePrediction.setStructureBlacklist(v);
    }

    public int villageRoadOffset() {
        return structurePrediction.villageRoadOffset();
    }

    public int otherStructureRoadOffset() {
        return structurePrediction.otherStructureRoadOffset();
    }

    public int aStarStep() {
        return pathfindingCost.aStarStep();
    }
}
