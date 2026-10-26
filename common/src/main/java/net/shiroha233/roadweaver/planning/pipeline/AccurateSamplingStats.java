package net.shiroha233.roadweaver.planning.pipeline;

import java.util.concurrent.atomic.AtomicLong;

public final class AccurateSamplingStats {
    private AccurateSamplingStats() {
    }

    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong baseHeightSamples = new AtomicLong(0);

    private static volatile long lastSnapshotTimeMs = 0;
    private static volatile long lastSnapshotSamples = 0;
    private static volatile double samplesPerSecond = 0.0;

    public static void recordCacheHit() {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    public static void recordCacheMiss() {
        totalRequests.incrementAndGet();
        baseHeightSamples.incrementAndGet();
    }

    public static int getCacheHitRatePercent() {
        long total = totalRequests.get();
        if (total == 0) return 0;
        return (int) Math.round((cacheHits.get() * 100.0) / total);
    }

    public static long getTotalBaseHeightSamples() {
        return baseHeightSamples.get();
    }

    public static double updateAndGetSamplesPerSecond() {
        long now = System.currentTimeMillis();
        long currentSamples = baseHeightSamples.get();

        if (lastSnapshotTimeMs == 0) {
            lastSnapshotTimeMs = now;
            lastSnapshotSamples = currentSamples;
            return 0.0;
        }

        long elapsedMs = now - lastSnapshotTimeMs;
        if (elapsedMs >= 500) {
            long delta = currentSamples - lastSnapshotSamples;
            samplesPerSecond = (delta * 1000.0) / elapsedMs;
            lastSnapshotTimeMs = now;
            lastSnapshotSamples = currentSamples;
        }

        return samplesPerSecond;
    }

    public static void reset() {
        totalRequests.set(0);
        cacheHits.set(0);
        baseHeightSamples.set(0);
        lastSnapshotTimeMs = 0;
        lastSnapshotSamples = 0;
        samplesPerSecond = 0.0;
    }
}
