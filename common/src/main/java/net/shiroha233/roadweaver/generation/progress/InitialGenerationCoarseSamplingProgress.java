package net.shiroha233.roadweaver.generation.progress;

import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainSamplingProgress;

/**
 * 初始道路生成阶段使用的粗采样进度适配器。
 */
public enum InitialGenerationCoarseSamplingProgress implements CoarseTerrainSamplingProgress {
    INSTANCE;

    @Override
    public void onPlan(int totalTiles, long totalSamples) {
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.COARSE_SAMPLING, "sampling_coarse_tiles");
        InitialGenerationProgressTracker.setTilePlan(
                totalTiles, totalSamples, "sampling_coarse_tiles");
    }

    @Override
    public void onTileCompleted(int completedTiles, int totalTiles) {
        InitialGenerationProgressTracker.recordTileLoaded();
    }
}
