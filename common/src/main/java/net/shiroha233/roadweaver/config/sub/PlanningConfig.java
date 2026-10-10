/* 文件职责：保存路网规划半径、算法与地形采样模式配置。 */
package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 路网规划配置。
 */
public final class PlanningConfig implements SubConfig {

    /**
     * 规划算法枚举。
     */
    public enum PlanningAlgorithm { KNN, DELAUNAY, RNG, MST, SNAP_BRANCH }

    private int initialPlanRadiusChunks = RoadConstants.DEFAULT_INITIAL_PLAN_RADIUS_CHUNKS;
    private boolean dynamicPlanEnabled = true;
    private int dynamicPlanRadiusChunks = RoadConstants.DEFAULT_DYNAMIC_PLAN_RADIUS_CHUNKS;
    private int dynamicPlanStrideChunks = RoadConstants.DEFAULT_DYNAMIC_PLAN_STRIDE_CHUNKS;
    private PlanningAlgorithm planningAlgorithm = PlanningAlgorithm.RNG;
    private TerrainSamplingMode terrainSamplingMode = TerrainSamplingMode.COARSE_CORRIDOR;
    /** Capture the unprocessed path nodes for the in-world debug overlay. */
    private boolean debugRawPaths = false;

    @Override
    public void sanitize() {
        if (initialPlanRadiusChunks <= 0) initialPlanRadiusChunks = 64;
        initialPlanRadiusChunks = Math.min(RoadConstants.COARSE_REGION_MAX_RADIUS_CHUNKS, initialPlanRadiusChunks);
        if (dynamicPlanRadiusChunks <= 0) dynamicPlanRadiusChunks = RoadConstants.DEFAULT_DYNAMIC_PLAN_RADIUS_CHUNKS;
        dynamicPlanRadiusChunks = Math.min(RoadConstants.COARSE_REGION_MAX_RADIUS_CHUNKS, dynamicPlanRadiusChunks);
        if (dynamicPlanStrideChunks <= 0) {
            dynamicPlanStrideChunks = Math.max(RoadConstants.PLAN_TILE_MIN,
                    Math.min(RoadConstants.PLAN_TILE_MAX, Math.max(1, dynamicPlanRadiusChunks) / 2));
        }
        if (dynamicPlanStrideChunks > dynamicPlanRadiusChunks) {
            dynamicPlanStrideChunks = dynamicPlanRadiusChunks;
        }
        dynamicPlanStrideChunks = Math.min(RoadConstants.PLAN_TILE_MAX, dynamicPlanStrideChunks);
        if (planningAlgorithm == null) planningAlgorithm = PlanningAlgorithm.RNG;
        if (terrainSamplingMode == null) terrainSamplingMode = TerrainSamplingMode.COARSE_CORRIDOR;
    }

    @Override
    public PlanningConfig snapshot() {
        PlanningConfig copy = new PlanningConfig();
        copy.initialPlanRadiusChunks = this.initialPlanRadiusChunks;
        copy.dynamicPlanEnabled = this.dynamicPlanEnabled;
        copy.dynamicPlanRadiusChunks = this.dynamicPlanRadiusChunks;
        copy.dynamicPlanStrideChunks = this.dynamicPlanStrideChunks;
        copy.planningAlgorithm = this.planningAlgorithm;
        copy.terrainSamplingMode = this.terrainSamplingMode;
        copy.debugRawPaths = this.debugRawPaths;
        return copy;
    }

    public int initialPlanRadiusChunks() { return initialPlanRadiusChunks; }
    public void setInitialPlanRadiusChunks(int v) { this.initialPlanRadiusChunks = v; }
    public boolean dynamicPlanEnabled() { return dynamicPlanEnabled; }
    public void setDynamicPlanEnabled(boolean v) { this.dynamicPlanEnabled = v; }
    public int dynamicPlanRadiusChunks() { return dynamicPlanRadiusChunks; }
    public void setDynamicPlanRadiusChunks(int v) { this.dynamicPlanRadiusChunks = v; }
    public int dynamicPlanStrideChunks() { return dynamicPlanStrideChunks; }
    public void setDynamicPlanStrideChunks(int v) { this.dynamicPlanStrideChunks = v; }
    public PlanningAlgorithm planningAlgorithm() { return planningAlgorithm; }
    public void setPlanningAlgorithm(PlanningAlgorithm v) { this.planningAlgorithm = v; }
    public PlanningAlgorithm algorithm() { return planningAlgorithm; }
    public void setAlgorithm(PlanningAlgorithm v) { this.planningAlgorithm = v; }
    public TerrainSamplingMode terrainSamplingMode() { return terrainSamplingMode; }
    public void setTerrainSamplingMode(TerrainSamplingMode v) { this.terrainSamplingMode = v; }
    public boolean debugRawPaths() { return debugRawPaths; }
    public void setDebugRawPaths(boolean value) { this.debugRawPaths = value; }
}
