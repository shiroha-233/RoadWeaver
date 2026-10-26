/* 文件职责：验证共享 OpenCL 会话优先让精采任务越过等待中的粗采任务。 */
package net.shiroha233.roadweaver.terrain.opencl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCLSubmissionGateTest {
    @Test
    void waitingAccurateSubmissionRunsBeforeWaitingCoarseSubmission() throws Exception {
        OpenCLSubmissionGate gate = new OpenCLSubmissionGate();
        CountDownLatch firstCoarseStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCoarse = new CountDownLatch(1);
        CountDownLatch accurateStarted = new CountDownLatch(1);
        CountDownLatch coarseAfterAccurate = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            executor.submit(() -> gate.submit(OpenCLSubmissionPriority.COARSE, () -> {
                firstCoarseStarted.countDown();
                await(releaseFirstCoarse);
                return null;
            }));
            assertTrue(firstCoarseStarted.await(5, TimeUnit.SECONDS));
            executor.submit(() -> gate.submit(OpenCLSubmissionPriority.COARSE, () -> {
                assertEquals(1, order.getAndIncrement());
                coarseAfterAccurate.countDown();
                return null;
            }));
            executor.submit(() -> gate.submit(OpenCLSubmissionPriority.ACCURATE, () -> {
                assertEquals(0, order.getAndIncrement());
                accurateStarted.countDown();
                return null;
            }));

            awaitAccurateQueue(gate);
            releaseFirstCoarse.countDown();
            assertTrue(accurateStarted.await(5, TimeUnit.SECONDS));
            assertTrue(coarseAfterAccurate.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitAccurateQueue(OpenCLSubmissionGate gate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (gate.waitingAccurateSubmissions() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue(gate.waitingAccurateSubmissions() > 0, "accurate submission did not reach the queue");
    }
}
