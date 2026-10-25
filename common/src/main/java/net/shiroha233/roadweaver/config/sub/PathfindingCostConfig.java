package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 寻路代价权重配置
 */
public final class PathfindingCostConfig implements SubConfig {

    /** 寻路算法枚举 */
    public enum PathfindingAlgorithm { ASTAR_BASIC, ASTAR_BIDIRECTIONAL, GRADIENT_DESCENT, POTENTIAL_FIELD }

    private double orthoStepCost = RoadConstants.DEFAULT_ORTHO_STEP_COST;
    private double diagStepCost = RoadConstants.DEFAULT_DIAG_STEP_COST;
    private int elevationWeight = RoadConstants.DEFAULT_ELEVATION_WEIGHT;
    private int biomeWeight = RoadConstants.DEFAULT_BIOME_WEIGHT;
    private int stabilityWeight = RoadConstants.DEFAULT_STABILITY_WEIGHT;
    private int waterDepthWeight = RoadConstants.DEFAULT_WATER_DEPTH_WEIGHT;
    private int nearWaterCost = RoadConstants.DEFAULT_NEAR_WATER_COST;
    private int waterProximityCost = RoadConstants.DEFAULT_WATER_PROXIMITY_COST;
    private double heuristicWeight = RoadConstants.DEFAULT_HEURISTIC_WEIGHT;
    private double deviationWeight = RoadConstants.DEFAULT_DEVIATION_WEIGHT;
    private int aStarStep = RoadConstants.DEFAULT_ASTAR_STEP;
    private int aStarMaxSteps = RoadConstants.DEFAULT_ASTAR_MAX_STEPS;
    private PathfindingAlgorithm pathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT;

    @Override
    public void sanitize() {
        orthoStepCost = Math.max(0, orthoStepCost);
        diagStepCost = Math.max(0, diagStepCost);
        elevationWeight = Math.max(0, elevationWeight);
        biomeWeight = Math.max(0, biomeWeight);
        stabilityWeight = Math.max(0, stabilityWeight);
        waterDepthWeight = Math.max(0, waterDepthWeight);
        nearWaterCost = Math.max(0, nearWaterCost);
        waterProximityCost = Math.max(0, waterProximityCost);
        heuristicWeight = Math.max(0, heuristicWeight);
        deviationWeight = Math.max(0, deviationWeight);
        aStarStep = Math.min(RoadConstants.ASTAR_STEP_MAX, aStarStep);
        aStarMaxSteps = Math.max(RoadConstants.ASTAR_MAX_STEPS_MIN,
                Math.min(RoadConstants.ASTAR_MAX_STEPS_MAX, aStarMaxSteps));
        if (pathfindingAlgorithm == null) pathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT;
    }

    @Override
    public PathfindingCostConfig snapshot() {
        PathfindingCostConfig copy = new PathfindingCostConfig();
        copy.orthoStepCost = this.orthoStepCost;
        copy.diagStepCost = this.diagStepCost;
        copy.elevationWeight = this.elevationWeight;
        copy.biomeWeight = this.biomeWeight;
        copy.stabilityWeight = this.stabilityWeight;
        copy.waterDepthWeight = this.waterDepthWeight;
        copy.nearWaterCost = this.nearWaterCost;
        copy.waterProximityCost = this.waterProximityCost;
        copy.heuristicWeight = this.heuristicWeight;
        copy.deviationWeight = this.deviationWeight;
        copy.aStarStep = this.aStarStep;
        copy.aStarMaxSteps = this.aStarMaxSteps;
        copy.pathfindingAlgorithm = this.pathfindingAlgorithm;
        return copy;
    }

    public double orthoStepCost() { return orthoStepCost; }
    public void setOrthoStepCost(double v) { this.orthoStepCost = v; }
    public double diagStepCost() { return diagStepCost; }
    public void setDiagStepCost(double v) { this.diagStepCost = v; }
    public int elevationWeight() { return elevationWeight; }
    public void setElevationWeight(int v) { this.elevationWeight = v; }
    public int biomeWeight() { return biomeWeight; }
    public void setBiomeWeight(int v) { this.biomeWeight = v; }
    public int stabilityWeight() { return stabilityWeight; }
    public void setStabilityWeight(int v) { this.stabilityWeight = v; }
    public int waterDepthWeight() { return waterDepthWeight; }
    public void setWaterDepthWeight(int v) { this.waterDepthWeight = v; }
    public int nearWaterCost() { return nearWaterCost; }
    public void setNearWaterCost(int v) { this.nearWaterCost = v; }
    public int waterProximityCost() { return waterProximityCost; }
    public void setWaterProximityCost(int v) { this.waterProximityCost = v; }
    public double heuristicWeight() { return heuristicWeight; }
    public void setHeuristicWeight(double v) { this.heuristicWeight = v; }
    public double deviationWeight() { return deviationWeight; }
    public void setDeviationWeight(double v) { this.deviationWeight = v; }
    public int aStarStep() { return aStarStep; }
    public void setAStarStep(int v) { this.aStarStep = v; }
    public int aStarMaxSteps() { return aStarMaxSteps; }
    public void setAStarMaxSteps(int v) { this.aStarMaxSteps = v; }
    public PathfindingAlgorithm pathfindingAlgorithm() { return pathfindingAlgorithm; }
    public void setPathfindingAlgorithm(PathfindingAlgorithm v) { this.pathfindingAlgorithm = v; }
    public PathfindingAlgorithm algorithm() { return pathfindingAlgorithm; }
    public void setAlgorithm(PathfindingAlgorithm v) { this.pathfindingAlgorithm = v; }
    public int effectiveAStarStep() {
        if (aStarStep < RoadConstants.ASTAR_STEP_MIN) return RoadConstants.DEFAULT_ASTAR_STEP;
        if (aStarStep > RoadConstants.ASTAR_STEP_MAX) return RoadConstants.ASTAR_STEP_MAX;
        return aStarStep;
    }
}
