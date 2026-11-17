package net.shiroha233.roadweaver.terrain;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightBackend;
import net.shiroha233.roadweaver.terrain.accurate.CpuAccurateHeightBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 精确高度后端工厂。
 */
public final class AccurateHeightBackends {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean DEFERRED_LOGGED = new AtomicBoolean();

    private AccurateHeightBackends() {
    }

    public static AccurateHeightBackend create(ServerLevel level, CpuAccurateHeightBackend cpu) {
        if (ConfigService.get().performance().openclAccurateSamplingEnabled()
                && DEFERRED_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("OpenCL 精确采样在 26.3 暂不可用，使用 CPU 精确采样后端");
        }
        return cpu;
    }
}
