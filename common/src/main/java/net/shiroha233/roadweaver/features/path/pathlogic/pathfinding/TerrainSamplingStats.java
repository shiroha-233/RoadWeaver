package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局地形采样统计：线程安全地汇总所有 TerrainSamplingCache 实例的缓存命中率和噪声采样数。
 * 用于在初始道路生成界面显示实时性能数据。
 */
public final class TerrainSamplingStats {
    private TerrainSamplingStats() {}

    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong noiseSamples = new AtomicLong(0);
    
    private static volatile long lastSnapshotTimeMs = 0;
    private static volatile long lastSnapshotNoiseSamples = 0;
    private static volatile double samplesPerSecond = 0.0;

    public static void recordCacheHit() {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    public static void recordCacheMiss() {
        totalRequests.incrementAndGet();
        noiseSamples.incrementAndGet();
    }

    public static double getCacheHitRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (double) cacheHits.get() / total;
    }

    public static int getCacheHitRatePercent() {
        return (int) Math.round(getCacheHitRate() * 100);
    }

    public static long getTotalNoiseSamples() {
        return noiseSamples.get();
    }

    public static double updateAndGetSamplesPerSecond() {
        long now = System.currentTimeMillis();
        long currentSamples = noiseSamples.get();
        
        if (lastSnapshotTimeMs == 0) {
            lastSnapshotTimeMs = now;
            lastSnapshotNoiseSamples = currentSamples;
            return 0.0;
        }
        
        long elapsedMs = now - lastSnapshotTimeMs;
        if (elapsedMs >= 500) {
            long samplesDelta = currentSamples - lastSnapshotNoiseSamples;
            samplesPerSecond = (samplesDelta * 1000.0) / elapsedMs;
            lastSnapshotTimeMs = now;
            lastSnapshotNoiseSamples = currentSamples;
        }
        
        return samplesPerSecond;
    }

    public static double getSamplesPerSecond() {
        return samplesPerSecond;
    }

    public static void reset() {
        totalRequests.set(0);
        cacheHits.set(0);
        noiseSamples.set(0);
        lastSnapshotTimeMs = 0;
        lastSnapshotNoiseSamples = 0;
        samplesPerSecond = 0.0;
    }
}
