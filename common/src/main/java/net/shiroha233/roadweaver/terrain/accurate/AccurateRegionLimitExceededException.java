package net.shiroha233.roadweaver.terrain.accurate;

/**
 * 精确区域无法在安全预算内物化。
 */
public final class AccurateRegionLimitExceededException extends RuntimeException {
    public AccurateRegionLimitExceededException(long sampleCount, long limit) {
        super("accurate region sample count exceeds limit: " + sampleCount + " > " + limit);
    }
}
