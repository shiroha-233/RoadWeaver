package net.shiroha233.roadweaver.generation.progress;

import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 初始生成进度状态总线。
 */
public final class InitialGenerationProgressTracker {
    private InitialGenerationProgressTracker() {}

    private static final AtomicReference<InitialGenerationStage> STAGE = new AtomicReference<>(InitialGenerationStage.FINISHED);
    private static final AtomicLong STARTED_AT = new AtomicLong(0L);
    private static final AtomicInteger OVERALL_PERCENT_FLOOR = new AtomicInteger();

    private static final AtomicInteger CONNECTIONS_TOTAL = new AtomicInteger();
    private static final AtomicInteger CONNECTIONS_GENERATING = new AtomicInteger();
    private static final AtomicInteger CONNECTIONS_DONE = new AtomicInteger();
    private static final AtomicInteger CONNECTIONS_FAILED = new AtomicInteger();

    private static final AtomicInteger TILES_TOTAL = new AtomicInteger();
    private static final AtomicInteger TILES_LOADED = new AtomicInteger();
    private static final AtomicInteger TILES_SAMPLED = new AtomicInteger();
    private static final AtomicInteger TILES_FROM_MEMORY = new AtomicInteger();
    private static final AtomicInteger TILES_FROM_DISK = new AtomicInteger();
    private static final AtomicInteger COARSE_PATHS_TOTAL = new AtomicInteger();
    private static final AtomicInteger COARSE_PATHS_DONE = new AtomicInteger();
    private static final AtomicLong EXACT_SAMPLES_TOTAL = new AtomicLong();
    private static final AtomicLong EXACT_SAMPLES_DONE = new AtomicLong();
    private static final AtomicInteger EXACT_PATHS_TOTAL = new AtomicInteger();
    private static final AtomicInteger EXACT_PATHS_DONE = new AtomicInteger();

    private static final AtomicLong SAMPLES_TOTAL = new AtomicLong();
    private static final AtomicLong SAMPLES_DONE = new AtomicLong();
    private static final AtomicLong LAST_SAMPLE_SNAPSHOT_AT = new AtomicLong();
    private static final AtomicLong LAST_SAMPLE_SNAPSHOT_COUNT = new AtomicLong();
    private static final AtomicReference<Double> SAMPLES_PER_SECOND = new AtomicReference<>(0.0);
    private static final AtomicInteger LAST_BATCH_SAMPLES = new AtomicInteger();
    private static final AtomicLong LAST_BATCH_MILLIS = new AtomicLong();

    private static final AtomicReference<String> BACKEND = new AtomicReference<>("-");
    private static final AtomicReference<String> DEVICE_NAME = new AtomicReference<>("-");
    private static final AtomicReference<String> DEVICE_PREFERENCE = new AtomicReference<>("-");
    private static final AtomicReference<String> FALLBACK_REASON = new AtomicReference<>("");
    private static final AtomicReference<String> CURRENT_OPERATION = new AtomicReference<>("");

    private static final AtomicInteger INITIAL_THREADS = new AtomicInteger();
    private static final AtomicInteger ACTIVE_WORKERS = new AtomicInteger();

    public static void reset() {
        STARTED_AT.set(System.currentTimeMillis());
        STAGE.set(InitialGenerationStage.PLANNING);
        OVERALL_PERCENT_FLOOR.set(0);
        CONNECTIONS_TOTAL.set(0);
        CONNECTIONS_GENERATING.set(0);
        CONNECTIONS_DONE.set(0);
        CONNECTIONS_FAILED.set(0);
        TILES_TOTAL.set(0);
        TILES_LOADED.set(0);
        TILES_SAMPLED.set(0);
        TILES_FROM_MEMORY.set(0);
        TILES_FROM_DISK.set(0);
        COARSE_PATHS_TOTAL.set(0);
        COARSE_PATHS_DONE.set(0);
        EXACT_SAMPLES_TOTAL.set(0L);
        EXACT_SAMPLES_DONE.set(0L);
        EXACT_PATHS_TOTAL.set(0);
        EXACT_PATHS_DONE.set(0);
        SAMPLES_TOTAL.set(0L);
        SAMPLES_DONE.set(0L);
        LAST_SAMPLE_SNAPSHOT_AT.set(0L);
        LAST_SAMPLE_SNAPSHOT_COUNT.set(0L);
        SAMPLES_PER_SECOND.set(0.0);
        LAST_BATCH_SAMPLES.set(0);
        LAST_BATCH_MILLIS.set(0L);
        BACKEND.set("-");
        DEVICE_NAME.set("-");
        DEVICE_PREFERENCE.set(resolveDevicePreference());
        FALLBACK_REASON.set("");
        CURRENT_OPERATION.set("initializing");
        INITIAL_THREADS.set(resolveInitialThreads());
        ACTIVE_WORKERS.set(0);
    }

    public static void finish() {
        STAGE.set(InitialGenerationStage.FINISHED);
        OVERALL_PERCENT_FLOOR.set(100);
        CURRENT_OPERATION.set("finished");
        ACTIVE_WORKERS.set(0);
    }

    public static void enterStage(InitialGenerationStage stage, String operation) {
        if (stage == null) return;
        STAGE.set(stage);
        if (operation != null && !operation.isBlank()) {
            CURRENT_OPERATION.set(operation);
        }
    }

    public static void updateConnections(int total, int generating, int done, int failed) {
        CONNECTIONS_TOTAL.set(Math.max(0, total));
        CONNECTIONS_GENERATING.set(Math.max(0, generating));
        CONNECTIONS_DONE.set(Math.max(0, done));
        CONNECTIONS_FAILED.set(Math.max(0, failed));
        ACTIVE_WORKERS.set(Math.max(0, generating));
    }

    public static void setTilePlan(int totalTiles, long totalSamples, String operation) {
        TILES_TOTAL.set(Math.max(TILES_TOTAL.get(), Math.max(0, totalTiles)));
        SAMPLES_TOTAL.set(Math.max(SAMPLES_TOTAL.get(), Math.max(0L, totalSamples)));
        if (operation != null && !operation.isBlank()) {
            CURRENT_OPERATION.set(operation);
        }
    }

    public static void recordTileLoaded() {
        TILES_LOADED.incrementAndGet();
    }

    public static void recordTileMemoryHit() {
        TILES_FROM_MEMORY.incrementAndGet();
    }

    public static void recordTileDiskHit() {
        TILES_FROM_DISK.incrementAndGet();
    }

    public static void recordTileSampled() {
        TILES_SAMPLED.incrementAndGet();
    }

    public static void setExactSamplingPlan(long totalSamples, String operation) {
        EXACT_SAMPLES_TOTAL.set(Math.max(0L, totalSamples));
        EXACT_SAMPLES_DONE.set(0L);
        SAMPLES_TOTAL.set(Math.max(0L, totalSamples));
        SAMPLES_DONE.set(0L);
        LAST_SAMPLE_SNAPSHOT_AT.set(0L);
        LAST_SAMPLE_SNAPSHOT_COUNT.set(0L);
        SAMPLES_PER_SECOND.set(0.0);
        LAST_BATCH_SAMPLES.set(0);
        LAST_BATCH_MILLIS.set(0L);
        if (operation != null && !operation.isBlank()) {
            CURRENT_OPERATION.set(operation);
        }
    }

    public static void recordExactSampleBatch(int sampleCount,
                                              long millis,
                                              String backend,
                                              String deviceName) {
        int safeSamples = Math.max(0, sampleCount);
        long previous = EXACT_SAMPLES_DONE.getAndUpdate(
                done -> Math.min(EXACT_SAMPLES_TOTAL.get(), done + safeSamples));
        int acceptedSamples = Math.toIntExact(Math.max(0L,
                Math.min(EXACT_SAMPLES_TOTAL.get(), previous + safeSamples) - previous));
        recordSampleBatch(backend, acceptedSamples, millis, deviceName, "");
    }

    public static void completeExactSampling() {
        EXACT_SAMPLES_DONE.set(EXACT_SAMPLES_TOTAL.get());
    }

    public static void setExactPathPlan(int totalPaths, String operation) {
        EXACT_PATHS_TOTAL.set(Math.max(0, totalPaths));
        EXACT_PATHS_DONE.set(0);
        if (operation != null && !operation.isBlank()) {
            CURRENT_OPERATION.set(operation);
        }
    }

    public static void recordExactPathDone() {
        EXACT_PATHS_DONE.incrementAndGet();
    }

    public static void setCoarsePathPlan(int totalPaths, String operation) {
        COARSE_PATHS_TOTAL.set(Math.max(0, totalPaths));
        COARSE_PATHS_DONE.set(0);
        if (operation != null && !operation.isBlank()) {
            CURRENT_OPERATION.set(operation);
        }
    }

    public static void recordCoarsePathDone() {
        COARSE_PATHS_DONE.incrementAndGet();
    }

    public static void recordSampleBatch(String backend, int sampleCount, long millis, String deviceName, String fallbackReason) {
        int safeSamples = Math.max(0, sampleCount);
        long safeMillis = Math.max(0L, millis);
        SAMPLES_DONE.addAndGet(safeSamples);
        LAST_BATCH_SAMPLES.set(safeSamples);
        LAST_BATCH_MILLIS.set(safeMillis);
        if (backend != null && !backend.isBlank()) {
            BACKEND.set(backend);
        }
        if (deviceName != null && !deviceName.isBlank()) {
            DEVICE_NAME.set(deviceName);
        }
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            FALLBACK_REASON.set(fallbackReason);
        }
        updateSampleRate();
    }

    public static void setBackend(String backend, String deviceName, String fallbackReason) {
        if (backend != null && !backend.isBlank()) {
            BACKEND.set(backend);
        }
        if (deviceName != null && !deviceName.isBlank()) {
            DEVICE_NAME.set(deviceName);
        }
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            FALLBACK_REASON.set(fallbackReason);
        }
    }

    public static InitialGenerationProgressSnapshot snapshot(boolean active) {
        InitialGenerationStage stage = STAGE.get();
        int stagePercent = computeStagePercent(stage);
        int computedOverallPercent = computeOverallPercent(stage, stagePercent, active);
        int overallPercent = active
                ? OVERALL_PERCENT_FLOOR.accumulateAndGet(computedOverallPercent, Math::max)
                : 100;
        long startedAt = STARTED_AT.get();
        long elapsed = startedAt == 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAt);
        return new InitialGenerationProgressSnapshot(
                active,
                stage,
                stage.labelKey(),
                startedAt,
                elapsed,
                overallPercent,
                stagePercent,
                CONNECTIONS_TOTAL.get(),
                CONNECTIONS_GENERATING.get(),
                CONNECTIONS_DONE.get(),
                CONNECTIONS_FAILED.get(),
                TILES_TOTAL.get(),
                TILES_LOADED.get(),
                TILES_SAMPLED.get(),
                TILES_FROM_MEMORY.get(),
                TILES_FROM_DISK.get(),
                SAMPLES_TOTAL.get(),
                SAMPLES_DONE.get(),
                EXACT_PATHS_TOTAL.get(),
                EXACT_PATHS_DONE.get(),
                SAMPLES_PER_SECOND.get(),
                LAST_BATCH_SAMPLES.get(),
                LAST_BATCH_MILLIS.get(),
                safe(BACKEND.get()),
                safe(DEVICE_NAME.get()),
                safe(DEVICE_PREFERENCE.get()),
                safe(FALLBACK_REASON.get()),
                INITIAL_THREADS.get(),
                ACTIVE_WORKERS.get(),
                safe(CURRENT_OPERATION.get()),
                TerrainSamplingStats.getCacheHitRatePercent(),
                TerrainSamplingStats.getTotalNoiseSamples(),
                TerrainSamplingStats.updateAndGetSamplesPerSecond(),
                AccurateSamplingStats.getCacheHitRatePercent(),
                AccurateSamplingStats.getTotalBaseHeightSamples(),
                AccurateSamplingStats.updateAndGetSamplesPerSecond()
        );
    }

    private static int computeStagePercent(InitialGenerationStage stage) {
        return switch (stage) {
            case PLANNING -> percent(CONNECTIONS_TOTAL.get(), Math.max(1, CONNECTIONS_TOTAL.get()));
            case EXACT_SAMPLING -> percent(EXACT_SAMPLES_DONE.get(), EXACT_SAMPLES_TOTAL.get());
            case EXACT_PATHING -> percent(EXACT_PATHS_DONE.get(), EXACT_PATHS_TOTAL.get());
            case COARSE_SAMPLING -> percent(TILES_LOADED.get(), TILES_TOTAL.get());
            case COARSE_PATHING -> percent(COARSE_PATHS_DONE.get(), COARSE_PATHS_TOTAL.get());
            case ROAD_GENERATION -> percent(CONNECTIONS_DONE.get() + CONNECTIONS_FAILED.get(), CONNECTIONS_TOTAL.get());
            case POST_PROCESSING -> 50;
            case FINISHED -> 100;
        };
    }

    private static int computeOverallPercent(InitialGenerationStage stage, int stagePercent, boolean active) {
        if (!active || stage == InitialGenerationStage.FINISHED) return 100;
        int raw = stage.basePercent() + Math.round(stage.weightPercent() * (stagePercent / 100.0f));
        return Math.max(0, Math.min(99, raw));
    }

    private static int percent(long value, long total) {
        if (total <= 0L) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(value * 100.0 / total)));
    }

    private static void updateSampleRate() {
        long now = System.currentTimeMillis();
        long done = SAMPLES_DONE.get();
        long previousAt = LAST_SAMPLE_SNAPSHOT_AT.get();
        if (previousAt == 0L) {
            LAST_SAMPLE_SNAPSHOT_AT.set(now);
            LAST_SAMPLE_SNAPSHOT_COUNT.set(done);
            return;
        }
        long elapsed = now - previousAt;
        if (elapsed < 500L) return;
        long previousDone = LAST_SAMPLE_SNAPSHOT_COUNT.get();
        SAMPLES_PER_SECOND.set((done - previousDone) * 1000.0 / Math.max(1L, elapsed));
        LAST_SAMPLE_SNAPSHOT_AT.set(now);
        LAST_SAMPLE_SNAPSHOT_COUNT.set(done);
    }

    private static int resolveInitialThreads() {
        try {
            ModConfig config = ConfigService.get();
            return Math.max(1, config.performance().initialGenerationThreads());
        } catch (Throwable ignored) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    private static String resolveDevicePreference() {
        try {
            ModConfig config = ConfigService.get();
            return config.performance().openclDevicePreference().toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "AUTO";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
