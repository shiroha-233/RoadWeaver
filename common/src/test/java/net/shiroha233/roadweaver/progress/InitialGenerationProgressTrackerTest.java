/* 文件职责：验证精确区域采样批次会实时推进加载界面的进度与吞吐数据。 */
package net.shiroha233.roadweaver.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialGenerationProgressTrackerTest {
    @Test
    void exactSamplingBatchUpdatesVisibleProgress() {
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_SAMPLING, "sampling_accurate_region");
        InitialGenerationProgressTracker.setExactSamplingPlan(100L, "sampling_accurate_region");

        InitialGenerationProgressTracker.recordExactSampleBatch(
                25, 250L, "OPENCL_ACCURATE", "test-gpu");

        InitialGenerationProgressSnapshot snapshot = InitialGenerationProgressTracker.snapshot(true);
        assertEquals(25, snapshot.stagePercent());
        assertEquals(25L, snapshot.samplesDone());
        assertEquals(100L, snapshot.samplesTotal());
        assertEquals(25, snapshot.lastBatchSamples());
        assertEquals(250L, snapshot.lastBatchMillis());
        assertEquals("OPENCL_ACCURATE", snapshot.backend());
        assertEquals("test-gpu", snapshot.deviceName());
    }

    @Test
    void exactPathingExposesCompletedAndRemainingPaths() {
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_PATHING, "computing_accurate_paths");
        InitialGenerationProgressTracker.setExactPathPlan(4, "computing_accurate_paths");

        InitialGenerationProgressTracker.recordExactPathDone();

        InitialGenerationProgressSnapshot snapshot = InitialGenerationProgressTracker.snapshot(true);
        assertEquals(25, snapshot.stagePercent());
        assertEquals(4, snapshot.exactPathsTotal());
        assertEquals(1, snapshot.exactPathsDone());
    }

    @Test
    void retrySamplingDoesNotMoveOverallProgressBackwards() {
        InitialGenerationProgressTracker.reset();
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_PATHING, "computing_corridor_paths");
        InitialGenerationProgressTracker.setExactPathPlan(2, "computing_corridor_paths");
        InitialGenerationProgressTracker.recordExactPathDone();
        int beforeRetry = InitialGenerationProgressTracker.snapshot(true).overallPercent();

        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_SAMPLING, "sampling_accurate_corridors");
        InitialGenerationProgressTracker.setExactSamplingPlan(100, "sampling_accurate_corridors");
        int duringRetry = InitialGenerationProgressTracker.snapshot(true).overallPercent();

        assertTrue(duringRetry >= beforeRetry);
    }
}
