package net.shiroha233.roadweaver.config.sub;

/**
 * 规划阶段的地形采样策略。
 */
public enum TerrainSamplingMode {

    LEGACY_DIRECT,

    /**
     * 使用与原版 preliminary surface 同源的粗地形高度场直接规划，不重复构造 NoiseChunk。
     */
    COARSE_CORRIDOR,

    /**
     * 先为整个规划区域构建精确地形场；若会话内判定不可用，可降级为 COARSE_CORRIDOR。
     */
    FULL_REGION
}
