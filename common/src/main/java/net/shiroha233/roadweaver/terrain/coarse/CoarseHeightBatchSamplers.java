package net.shiroha233.roadweaver.terrain.coarse;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.sub.PerformanceConfig;
import net.shiroha233.roadweaver.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.terrain.opencl.OpenCLAvailability;
import net.shiroha233.roadweaver.terrain.opencl.OpenCLCoarseHeightBatchSampler;
import net.shiroha233.roadweaver.terrain.opencl.OpenCLDevicePreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 粗高度批量采样器入口。
 */
public final class CoarseHeightBatchSamplers {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean CONFIG_DISABLED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MIN_SAMPLES_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FALLBACK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean ATTEMPT_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean NON_OVERWORLD_LOGGED = new AtomicBoolean();

    private CoarseHeightBatchSamplers() {}

    public static CoarseHeightBatchSampler create(ServerLevel level, CoarseHeightBatchRequest request) {
        CoarseHeightBatchSampler opencl = tryCreateOpenCL(level, request);
        if (opencl != null) {
            return opencl;
        }
        String reason = OpenCLAvailability.disabledReason();
        InitialGenerationProgressTracker.setBackend("CPU", "CPU", reason);
        if (FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("粗高度采样使用 CPU{}", reason == null ? "" : ": " + reason);
        }
        return CpuCoarseHeightBatchSampler.create(level);
    }

    private static CoarseHeightBatchSampler tryCreateOpenCL(ServerLevel level, CoarseHeightBatchRequest request) {
        try {
            PerformanceConfig performance = ConfigService.get().performance();
            if (performance == null || !performance.openclCoarseSamplingEnabled()) {
                if (CONFIG_DISABLED_LOGGED.compareAndSet(false, true)) {
                    LOGGER.info("OpenCL 粗采样配置未启用，使用 CPU");
                }
                return null;
            }
            if (request == null) {
                return null;
            }
            if (level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(level.dimension())) {
                if (NON_OVERWORLD_LOGGED.compareAndSet(false, true)) {
                    LOGGER.info("OpenCL 粗采样仅支持 Overworld，当前维度={}，使用 CPU",
                            level == null ? "null" : level.dimension().location());
                }
                return null;
            }
            if (request.sampleCount() < performance.openclMinSamples()) {
                if (MIN_SAMPLES_LOGGED.compareAndSet(false, true)) {
                    LOGGER.info("OpenCL 粗采样样本数低于阈值，使用 CPU: samples={} min={}",
                            request.sampleCount(), performance.openclMinSamples());
                }
                return null;
            }
            if (ATTEMPT_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样尝试启用: dimension={} samples={} preference={} validate={} minSamples={}",
                        level.dimension().location(),
                        request.sampleCount(),
                        performance.openclDevicePreference(),
                        performance.openclValidateSamples(),
                        performance.openclMinSamples());
            }
            return OpenCLCoarseHeightBatchSampler.tryCreate(
                    level,
                    OpenCLDevicePreference.valueOf(performance.openclDevicePreference()));
        } catch (Throwable t) {
            LOGGER.info("OpenCL 粗采样入口失败，使用 CPU: {}", t.getMessage(), t);
            return null;
        }
    }
}