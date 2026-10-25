package net.shiroha233.roadweaver.pathfinding.cache.opencl;

/**
 * OpenCL 精采样一次编译共享的 density 输出。
 */
public enum DensityGraphRoot {
    FINAL_DENSITY,
    INITIAL_DENSITY_WITHOUT_JAGGEDNESS,
    BARRIER,
    FLUID_FLOODEDNESS,
    FLUID_SPREAD,
    LAVA,
    EROSION,
    DEPTH
}
