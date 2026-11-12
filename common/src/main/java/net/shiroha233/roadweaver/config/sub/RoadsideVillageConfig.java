package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 路边村庄配置
 */
public final class RoadsideVillageConfig implements SubConfig {
    private boolean enabled = true;
    private int maxVillagesPerRoad = RoadConstants.DEFAULT_MAX_ROADSIDE_VILLAGES_PER_ROAD;
    private int minRoadSegments = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MIN_ROAD_SEGMENTS;
    private int windowSegments = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_WINDOW_SEGMENTS;
    private int targetNodeCountMin = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_NODE_COUNT_MIN;
    private int targetNodeCountMax = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_NODE_COUNT_MAX;
    private int maxHeightDiff = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MAX_HEIGHT_DIFF;
    private int maxLocalSlope = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MAX_LOCAL_SLOPE;
    private double minCurveAngle = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MIN_CURVE_ANGLE;
    private double maxCurveAngle = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MAX_CURVE_ANGLE;
    private int roadBufferBlocks = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_ROAD_BUFFER_BLOCKS;
    private int buildingGapInterval = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_BUILDING_GAP_INTERVAL;
    private int maxStepHeight = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MAX_STEP_HEIGHT;
    private int maxDistanceFromCenter = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_MAX_DISTANCE_FROM_CENTER;
    private double spawnChance = RoadConstants.DEFAULT_ROADSIDE_VILLAGE_SPAWN_CHANCE;

    @Override
    public void sanitize() {
        maxVillagesPerRoad = Math.max(0, Math.min(RoadConstants.ROADSIDE_VILLAGE_MAX_PER_ROAD_MAX, maxVillagesPerRoad));
        minRoadSegments = Math.max(RoadConstants.ROADSIDE_VILLAGE_MIN_ROAD_SEGMENTS_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_MIN_ROAD_SEGMENTS_MAX, minRoadSegments));
        windowSegments = Math.max(RoadConstants.ROADSIDE_VILLAGE_WINDOW_SEGMENTS_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_WINDOW_SEGMENTS_MAX, windowSegments));
        targetNodeCountMin = Math.max(RoadConstants.ROADSIDE_VILLAGE_NODE_COUNT_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_NODE_COUNT_MAX, targetNodeCountMin));
        targetNodeCountMax = Math.max(targetNodeCountMin, Math.min(RoadConstants.ROADSIDE_VILLAGE_NODE_COUNT_MAX, targetNodeCountMax));
        maxHeightDiff = Math.max(0, Math.min(RoadConstants.ROADSIDE_VILLAGE_MAX_HEIGHT_DIFF_MAX, maxHeightDiff));
        maxLocalSlope = Math.max(0, Math.min(RoadConstants.ROADSIDE_VILLAGE_MAX_LOCAL_SLOPE_MAX, maxLocalSlope));
        minCurveAngle = Math.max(0.0, Math.min(180.0, minCurveAngle));
        maxCurveAngle = Math.max(minCurveAngle, Math.min(180.0, maxCurveAngle));
        roadBufferBlocks = Math.max(RoadConstants.ROADSIDE_VILLAGE_ROAD_BUFFER_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_ROAD_BUFFER_MAX, roadBufferBlocks));
        buildingGapInterval = Math.max(RoadConstants.ROADSIDE_VILLAGE_BUILDING_GAP_INTERVAL_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_BUILDING_GAP_INTERVAL_MAX, buildingGapInterval));
        maxStepHeight = Math.max(RoadConstants.ROADSIDE_VILLAGE_MAX_STEP_HEIGHT_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_MAX_STEP_HEIGHT_MAX, maxStepHeight));
        maxDistanceFromCenter = Math.max(RoadConstants.ROADSIDE_VILLAGE_MAX_DISTANCE_MIN, Math.min(RoadConstants.ROADSIDE_VILLAGE_MAX_DISTANCE_MAX, maxDistanceFromCenter));
        spawnChance = Math.max(0.0, Math.min(1.0, spawnChance));
    }

    @Override
    public RoadsideVillageConfig snapshot() {
        RoadsideVillageConfig copy = new RoadsideVillageConfig();
        copy.enabled = this.enabled;
        copy.maxVillagesPerRoad = this.maxVillagesPerRoad;
        copy.minRoadSegments = this.minRoadSegments;
        copy.windowSegments = this.windowSegments;
        copy.targetNodeCountMin = this.targetNodeCountMin;
        copy.targetNodeCountMax = this.targetNodeCountMax;
        copy.maxHeightDiff = this.maxHeightDiff;
        copy.maxLocalSlope = this.maxLocalSlope;
        copy.minCurveAngle = this.minCurveAngle;
        copy.maxCurveAngle = this.maxCurveAngle;
        copy.roadBufferBlocks = this.roadBufferBlocks;
        copy.buildingGapInterval = this.buildingGapInterval;
        copy.maxStepHeight = this.maxStepHeight;
        copy.maxDistanceFromCenter = this.maxDistanceFromCenter;
        copy.spawnChance = this.spawnChance;
        return copy;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int maxVillagesPerRoad() { return maxVillagesPerRoad; }
    public void setMaxVillagesPerRoad(int v) { this.maxVillagesPerRoad = Math.max(0, v); }
    public int minRoadSegments() { return minRoadSegments; }
    public void setMinRoadSegments(int v) { this.minRoadSegments = Math.max(1, v); }
    public int windowSegments() { return windowSegments; }
    public void setWindowSegments(int v) { this.windowSegments = Math.max(1, v); }
    public int targetNodeCountMin() { return targetNodeCountMin; }
    public void setTargetNodeCountMin(int v) { this.targetNodeCountMin = Math.max(1, v); }
    public int targetNodeCountMax() { return targetNodeCountMax; }
    public void setTargetNodeCountMax(int v) { this.targetNodeCountMax = Math.max(1, v); }
    public int maxHeightDiff() { return maxHeightDiff; }
    public void setMaxHeightDiff(int v) { this.maxHeightDiff = Math.max(0, v); }
    public int maxLocalSlope() { return maxLocalSlope; }
    public void setMaxLocalSlope(int v) { this.maxLocalSlope = Math.max(0, v); }
    public double minCurveAngle() { return minCurveAngle; }
    public void setMinCurveAngle(double v) { this.minCurveAngle = Math.max(0.0, v); }
    public double maxCurveAngle() { return maxCurveAngle; }
    public void setMaxCurveAngle(double v) { this.maxCurveAngle = Math.max(0.0, v); }
    public int roadBufferBlocks() { return roadBufferBlocks; }
    public void setRoadBufferBlocks(int v) { this.roadBufferBlocks = Math.max(0, v); }
    public int buildingGapInterval() { return buildingGapInterval; }
    public void setBuildingGapInterval(int v) { this.buildingGapInterval = Math.max(2, v); }
    public int maxStepHeight() { return maxStepHeight; }
    public void setMaxStepHeight(int v) { this.maxStepHeight = Math.max(1, v); }
    public int maxDistanceFromCenter() { return maxDistanceFromCenter; }
    public void setMaxDistanceFromCenter(int v) { this.maxDistanceFromCenter = Math.max(1, v); }
    public double spawnChance() { return spawnChance; }
    public void setSpawnChance(double v) { this.spawnChance = Math.max(0.0, Math.min(1.0, v)); }
}
