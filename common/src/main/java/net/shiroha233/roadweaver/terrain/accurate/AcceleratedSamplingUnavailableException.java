package net.shiroha233.roadweaver.terrain.accurate;

/**
 * accelerated-only 采样失败。
 */
public final class AcceleratedSamplingUnavailableException extends RuntimeException {
    public AcceleratedSamplingUnavailableException(String message) {
        super(message);
    }

    public AcceleratedSamplingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
