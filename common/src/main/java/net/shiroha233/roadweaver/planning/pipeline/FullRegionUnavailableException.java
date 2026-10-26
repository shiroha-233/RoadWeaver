package net.shiroha233.roadweaver.planning.pipeline;

final class FullRegionUnavailableException extends RuntimeException {
    FullRegionUnavailableException(String message) {
        super(message);
    }

    FullRegionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
