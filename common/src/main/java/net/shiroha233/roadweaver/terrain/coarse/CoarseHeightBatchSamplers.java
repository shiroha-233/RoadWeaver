package net.shiroha233.roadweaver.terrain.coarse;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.terrain.api.CoarseHeightBatchRequest;
import net.shiroha233.roadweaver.terrain.api.CoarseHeightBatchSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 粗高度批量采样器入口。
 *
 * <p>26.3 重写了噪声与密度体系，OpenCL 粗采样所用的密度图与噪声表提取暂未移植，
 * 因此这里统一返回 CPU 实现，并在配置开启 GPU 加速时提示一次。</p>
 */
public final class CoarseHeightBatchSamplers {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean DEFERRED_LOGGED = new AtomicBoolean();

    private CoarseHeightBatchSamplers() {
    }

    public static CoarseHeightBatchSampler create(ServerLevel level, CoarseHeightBatchRequest request) {
        if (ConfigService.get().performance().openclCoarseSamplingEnabled()
                && DEFERRED_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("OpenCL 粗采样在 26.3 暂不可用，使用 CPU 粗采样");
        }
        InitialGenerationProgressTracker.setBackend("CPU", "CPU", "opencl_backend_deferred");
        return CpuCoarseHeightBatchSampler.create(level);
    }
}
