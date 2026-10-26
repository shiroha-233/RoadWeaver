package net.shiroha233.roadweaver.terrain.opencl;

import java.util.List;

/**
 * OpenCL 侧 NormalNoise 描述。
 */
public record OpenCLNormalNoise(
        int firstPerlinIndex,
        int secondPerlinIndex,
        double valueFactor,
        double maxValue
) {}