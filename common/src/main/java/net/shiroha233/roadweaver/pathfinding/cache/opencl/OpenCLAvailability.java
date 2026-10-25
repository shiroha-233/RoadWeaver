package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenCL 可用性状态。
 */
public final class OpenCLAvailability {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicReference<String> DISABLED_REASON = new AtomicReference<>();
    private static final AtomicBoolean LOGGED_DISABLED = new AtomicBoolean();

    private OpenCLAvailability() {}

    public static boolean isAvailable() {
        return DISABLED_REASON.get() == null;
    }

    public static String disabledReason() {
        return DISABLED_REASON.get();
    }

    public static void disable(String reason) {
        String safeReason = (reason == null || reason.isBlank()) ? "unknown" : reason;
        DISABLED_REASON.compareAndSet(null, safeReason);
    }

    public static void disable(String reason, Throwable cause) {
        disable(reason);
        logDisabledOnce(cause);
    }

    public static void logDisabledOnce(Throwable cause) {
        String reason = DISABLED_REASON.get();
        if (reason == null || !LOGGED_DISABLED.compareAndSet(false, true)) {
            return;
        }
        if (cause == null) {
            LOGGER.info("OpenCL 粗采样不可用，回退到 CPU: {}", reason);
        } else {
            LOGGER.info("OpenCL 粗采样不可用，回退到 CPU: {}", reason, cause);
        }
    }

    public static void reset() {
        DISABLED_REASON.set(null);
        LOGGED_DISABLED.set(false);
    }

    public static void resetForTests() {
        reset();
    }
}
