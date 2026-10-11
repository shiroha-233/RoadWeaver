/* 文件职责：验证走廊模式把粗寻路、走廊精采与精寻路计入各自阶段，不合并进粗路径进度。 */
package net.shiroha233.roadweaver.generation.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorridorStageSplitProgressTest {

    @Test
    void coarseStageCompletesBeforeCorridorWorkStarts() {
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.COARSE_PATHING, "computing_coarse_paths");
        InitialGenerationProgressTracker.setCoarsePathPlan(2, "computing_coarse_paths");

        InitialGenerationProgressTracker.recordCoarsePathDone();
        int halfway = InitialGenerationProgressTracker.snapshot(true).stagePercent();
        assertEquals(50, halfway);

        // 粗寻路全部结束，此时尚未开始任何走廊精采或精寻路。
        InitialGenerationProgressTracker.recordCoarsePathDone();
        InitialGenerationProgressSnapshot afterCoarse = InitialGenerationProgressTracker.snapshot(true);
        assertEquals(100, afterCoarse.stagePercent());
        assertEquals(0, afterCoarse.exactPathsTotal());
        assertEquals(0, afterCoarse.exactPathsDone());
    }

    @Test
    void corridorWorkIsCountedUnderExactPathingNotCoarsePathing() {
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.COARSE_PATHING, "computing_coarse_paths");
        InitialGenerationProgressTracker.setCoarsePathPlan(2, "computing_coarse_paths");
        InitialGenerationProgressTracker.recordCoarsePathDone();
        InitialGenerationProgressTracker.recordCoarsePathDone();
        int afterCoarseOverall = InitialGenerationProgressTracker.snapshot(true).overallPercent();

        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_PATHING, "computing_corridor_paths");
        InitialGenerationProgressTracker.setExactPathPlan(2, "computing_corridor_paths");
        InitialGenerationProgressTracker.recordExactPathDone();

        InitialGenerationProgressSnapshot duringCorridor = InitialGenerationProgressTracker.snapshot(true);
        assertEquals(InitialGenerationStage.EXACT_PATHING, duringCorridor.stage());
        assertEquals(50, duringCorridor.stagePercent());
        assertEquals(1, duringCorridor.exactPathsDone());
        assertEquals(2, duringCorridor.exactPathsTotal());
        // 切换阶段不得让总进度倒退。
        assertTrue(duringCorridor.overallPercent() >= afterCoarseOverall);
    }
}