package net.shiroha233.roadweaver.pathfinding.terrain.region;

/**
 * 接收粗地形采样计划与完成进度。
 */
public interface CoarseTerrainSamplingProgress {
    CoarseTerrainSamplingProgress NONE = new CoarseTerrainSamplingProgress() {
        @Override
        public void onPlan(int totalTiles, long totalSamples) {}

        @Override
        public void onTileCompleted(int completedTiles, int totalTiles) {}
    };

    void onPlan(int totalTiles, long totalSamples);

    void onTileCompleted(int completedTiles, int totalTiles);
}
