package net.shiroha233.roadweaver.terrain.opencl;

/**
 * Density graph 编译结果。
 */
public record DensityGraphCompileResult(
        DensityGraphProgram program,
        String unsupportedReason,
        boolean retryable
) {
    public static DensityGraphCompileResult supported(DensityGraphProgram program) {
        return new DensityGraphCompileResult(program, null, false);
    }

    public static DensityGraphCompileResult unsupported(String reason) {
        return new DensityGraphCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, false);
    }

    public static DensityGraphCompileResult retryable(String reason) {
        return new DensityGraphCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, true);
    }

    public boolean supported() {
        return program != null && !program.isEmpty() && unsupportedReason == null;
    }
}