package net.shiroha233.roadweaver.terrain.opencl;

import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenCL 粗采样支持状态。
 */
public final class OpenCLWorldSupport {
    private static final AtomicReference<String> UNSUPPORTED_REASON = new AtomicReference<>();

    private OpenCLWorldSupport() {}

    public static boolean isUnsupported() {
        return UNSUPPORTED_REASON.get() != null;
    }

    public static String unsupportedReason() {
        return UNSUPPORTED_REASON.get();
    }

    public static boolean markUnsupported(String reason) {
        return UNSUPPORTED_REASON.compareAndSet(null, reason == null || reason.isBlank() ? "unknown" : reason);
    }

    public static void clear() {
        UNSUPPORTED_REASON.set(null);
    }
}
