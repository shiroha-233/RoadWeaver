package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 单设备提交闸门。
 */
final class OpenCLSubmissionGate {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition available = lock.newCondition();
    private boolean running;
    private int waitingAccurate;
    private boolean closed;

    <T> T submit(OpenCLSubmissionPriority priority, Supplier<T> operation) {
        long submittedAt = System.nanoTime();
        acquire(priority);
        if (priority == OpenCLSubmissionPriority.ACCURATE) {
            AccurateSamplingStats.recordGpuQueueWait(System.nanoTime() - submittedAt);
        }
        try {
            return operation.get();
        } finally {
            release();
        }
    }

    void close() {
        lock.lock();
        try {
            closed = true;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    int waitingAccurateSubmissions() {
        lock.lock();
        try {
            return waitingAccurate;
        } finally {
            lock.unlock();
        }
    }

    private void acquire(OpenCLSubmissionPriority priority) {
        boolean accurate = priority == OpenCLSubmissionPriority.ACCURATE;
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("OpenCL submission gate is closed");
            }
            if (accurate) {
                waitingAccurate++;
            }
            while (running || (priority == OpenCLSubmissionPriority.COARSE && waitingAccurate > 0)) {
                try {
                    available.await();
                } catch (InterruptedException interrupted) {
                    if (accurate) {
                        waitingAccurate--;
                    }
                    available.signalAll();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the OpenCL device", interrupted);
                }
                if (closed) {
                    if (accurate) {
                        waitingAccurate--;
                    }
                    throw new IllegalStateException("OpenCL submission gate is closed");
                }
            }
            if (accurate) {
                waitingAccurate--;
            }
            running = true;
        } finally {
            lock.unlock();
        }
    }

    private void release() {
        lock.lock();
        try {
            running = false;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
