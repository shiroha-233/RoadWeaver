/* 文件职责：保存线程调度与 OpenCL 地形采样性能配置。 */
package net.shiroha233.roadweaver.config.sub;

import net.shiroha233.roadweaver.config.SubConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

/**
 * 性能与线程配置
 */
public final class PerformanceConfig implements SubConfig {
    private int sharedWorkerThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    private int initialGenerationThreads = defaultInitialGenerationThreads();
    private int maxConcurrentGenerations = Math.max(1, Math.min(2, sharedWorkerThreads));
    private int threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
    private boolean idleGenerationEnabled = false;
    private int idleMaxConcurrentGenerations = RoadConstants.DEFAULT_IDLE_MAX_CONCURRENT_GENERATIONS;
    private int idleThreadDutyCycle = 20;
    private boolean openclCoarseSamplingEnabled = true;
    private boolean openclAccurateSamplingEnabled = true;
    private String openclDevicePreference = "AUTO";
    private int openclMinSamples = 1024;
    private boolean openclValidateSamples = false;

    @Override
    public void sanitize() {
        sharedWorkerThreads = Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, sharedWorkerThreads));
        initialGenerationThreads = sanitizeInitialGenerationThreads(initialGenerationThreads);
        maxConcurrentGenerations = Math.max(1, Math.min(128, maxConcurrentGenerations));
        if (threadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || threadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            threadDutyCycle = RoadConstants.DEFAULT_DUTY_CYCLE;
        }
        idleMaxConcurrentGenerations = sanitizeIdleMaxConcurrentGenerations(idleMaxConcurrentGenerations);
        if (idleThreadDutyCycle < RoadConstants.DUTY_CYCLE_MIN || idleThreadDutyCycle > RoadConstants.DUTY_CYCLE_MAX) {
            idleThreadDutyCycle = 20;
        }
        openclDevicePreference = normalizeOpenCLDevicePreference(openclDevicePreference);
        openclMinSamples = Math.max(0, Math.min(RoadConstants.COARSE_REGION_MAX_SAMPLES, openclMinSamples));
    }

    @Override
    public PerformanceConfig snapshot() {
        PerformanceConfig copy = new PerformanceConfig();
        copy.sharedWorkerThreads = this.sharedWorkerThreads;
        copy.initialGenerationThreads = this.initialGenerationThreads;
        copy.maxConcurrentGenerations = this.maxConcurrentGenerations;
        copy.threadDutyCycle = this.threadDutyCycle;
        copy.idleGenerationEnabled = this.idleGenerationEnabled;
        copy.idleMaxConcurrentGenerations = this.idleMaxConcurrentGenerations;
        copy.idleThreadDutyCycle = this.idleThreadDutyCycle;
        copy.openclCoarseSamplingEnabled = this.openclCoarseSamplingEnabled;
        copy.openclAccurateSamplingEnabled = this.openclAccurateSamplingEnabled;
        copy.openclDevicePreference = this.openclDevicePreference;
        copy.openclMinSamples = this.openclMinSamples;
        copy.openclValidateSamples = this.openclValidateSamples;
        return copy;
    }

    public int sharedWorkerThreads() { return sharedWorkerThreads; }
    public void setSharedWorkerThreads(int v) { this.sharedWorkerThreads = Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, v)); }
    public int initialGenerationThreads() { return initialGenerationThreads; }
    public void setInitialGenerationThreads(int v) { this.initialGenerationThreads = sanitizeInitialGenerationThreads(v); }
    public int maxConcurrentGenerations() { return maxConcurrentGenerations; }
    public void setMaxConcurrentGenerations(int v) { this.maxConcurrentGenerations = Math.max(1, Math.min(128, v)); }
    public int threadDutyCycle() { return threadDutyCycle; }
    public void setThreadDutyCycle(int v) { this.threadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }
    public boolean idleGenerationEnabled() { return idleGenerationEnabled; }
    public void setIdleGenerationEnabled(boolean v) { this.idleGenerationEnabled = v; }
    public int idleMaxConcurrentGenerations() { return idleMaxConcurrentGenerations; }
    public void setIdleMaxConcurrentGenerations(int v) { this.idleMaxConcurrentGenerations = sanitizeIdleMaxConcurrentGenerations(v); }
    public int idleThreadDutyCycle() { return idleThreadDutyCycle; }
    public void setIdleThreadDutyCycle(int v) { this.idleThreadDutyCycle = Math.max(RoadConstants.DUTY_CYCLE_MIN, Math.min(RoadConstants.DUTY_CYCLE_MAX, v)); }
    public boolean openclCoarseSamplingEnabled() { return openclCoarseSamplingEnabled; }
    public void setOpenclCoarseSamplingEnabled(boolean v) { this.openclCoarseSamplingEnabled = v; }
    public boolean openclAccurateSamplingEnabled() { return openclAccurateSamplingEnabled; }
    public void setOpenclAccurateSamplingEnabled(boolean v) { this.openclAccurateSamplingEnabled = v; }
    public String openclDevicePreference() { return normalizeOpenCLDevicePreference(openclDevicePreference); }
    public void setOpenclDevicePreference(String v) { this.openclDevicePreference = normalizeOpenCLDevicePreference(v); }
    public int openclMinSamples() { return Math.max(0, openclMinSamples); }
    public void setOpenclMinSamples(int v) { this.openclMinSamples = Math.max(0, Math.min(RoadConstants.COARSE_REGION_MAX_SAMPLES, v)); }
    public boolean openclValidateSamples() { return openclValidateSamples; }
    public void setOpenclValidateSamples(boolean v) { this.openclValidateSamples = v; }

    public boolean shouldThrottle() { return threadDutyCycle < RoadConstants.DUTY_CYCLE_MAX; }

    private static int defaultInitialGenerationThreads() {
        return Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, Runtime.getRuntime().availableProcessors()));
    }

    private static int sanitizeInitialGenerationThreads(int value) {
        if (value <= 0) {
            return defaultInitialGenerationThreads();
        }
        return Math.max(1, Math.min(RoadConstants.COMPUTE_THREADS_MAX, value));
    }

    private static int sanitizeIdleMaxConcurrentGenerations(int value) {
        return Math.max(RoadConstants.IDLE_MAX_CONCURRENT_GENERATIONS_MIN,
                Math.min(RoadConstants.IDLE_MAX_CONCURRENT_GENERATIONS_MAX, value));
    }

    private static String normalizeOpenCLDevicePreference(String value) {
        if (value == null || value.isBlank()) {
            return "AUTO";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "AUTO", "GPU", "CPU" -> normalized;
            default -> "AUTO";
        };
    }
}
