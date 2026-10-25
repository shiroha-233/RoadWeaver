package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * Density graph 节点类型。
 */
public enum DensityGraphNodeType {
    CONSTANT,
    ADD,
    MUL,
    MIN,
    MAX,
    CLAMP,
    ABS,
    SQUARE,
    CUBE,
    HALF_NEGATIVE,
    QUARTER_NEGATIVE,
    SQUEEZE,
    INVERT,
    Y_CLAMPED_GRADIENT,
    RANGE_CHOICE,
    NOISE,
    SHIFTED_NOISE,
    SHIFT_A,
    SHIFT_B,
    SHIFT,
    SPLINE,
    WEIRD_SCALED_SAMPLER,
    CLAMP_TO_NEAREST_UNIT,
    MARKER,
    INTERPOLATED,
    BLENDED_NOISE,
    END_ISLAND
}
