package net.shiroha233.roadweaver.terrain.opencl;

/**
 * OpenCL 侧 ImprovedNoise 描述。
 */
public record OpenCLImprovedNoise(
        double xo,
        double yo,
        double zo,
        byte[] permutation
) {
    public OpenCLImprovedNoise {
        permutation = permutation == null ? new byte[0] : permutation.clone();
    }

    @Override
    public byte[] permutation() {
        return permutation.clone();
    }
}