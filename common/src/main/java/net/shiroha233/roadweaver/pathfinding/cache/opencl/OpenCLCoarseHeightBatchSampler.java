package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchRequest;
import net.shiroha233.roadweaver.pathfinding.cache.CoarseHeightBatchSampler;
import net.shiroha233.roadweaver.pathfinding.cache.CpuCoarseHeightBatchSampler;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenCL 粗高度批量采样器。
 */
public final class OpenCLCoarseHeightBatchSampler implements CoarseHeightBatchSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final AtomicBoolean ENABLED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean KERNEL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DISABLED_FALLBACK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RETRYABLE_COMPILE_LOGGED = new AtomicBoolean();
    private static final Object SESSION_VALIDATION_LOCK = new Object();
    private static final Object PROGRAM_CACHE_LOCK = new Object();
    private static final IdentityHashMap<Object, CachedDensityProgram> PROGRAM_CACHE = new IdentityHashMap<>();
    private static boolean sessionValidationStarted;
    private static boolean sessionValidationFinished;

    private final ServerLevel level;
    private final OpenCLRuntime runtime;
    private final CachedDensityProgram cachedProgram;
    private final DensityGraphProgram program;
    private final int minY;
    private final int maxY;
    private final int cellHeight;
    private CpuCoarseHeightBatchSampler fallback;

    private OpenCLCoarseHeightBatchSampler(ServerLevel level,
                                            OpenCLRuntime runtime,
                                            CachedDensityProgram cachedProgram,
                                            NoiseSettings settings) {
        this.level = level;
        this.runtime = runtime;
        this.cachedProgram = cachedProgram;
        this.program = cachedProgram.program();
        this.minY = settings.minY();
        this.maxY = settings.minY() + settings.height();
        this.cellHeight = settings.getCellHeight();
    }

    public static CoarseHeightBatchSampler tryCreate(ServerLevel level,
                                                     OpenCLDevicePreference devicePreference) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            return null;
        }
        if (OpenCLWorldSupport.isUnsupported()) {
            return null;
        }
        CachedCompileResult graph = compileInitialDensity(level);
        if (!graph.supported()) {
            if (graph.retryable() && RETRYABLE_COMPILE_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样暂时不可用，主世界本次回退到 CPU，后续继续重试: {}",
                        graph.unsupportedReason());
            }
            return null;
        }
        OpenCLRuntime runtime = OpenCLRuntime.tryCreate(devicePreference);
        if (runtime == null) {
            return null;
        }
        try {
            OpenCLCoarseHeightBatchSampler sampler = new OpenCLCoarseHeightBatchSampler(
                    level,
                    runtime,
                    graph.cachedProgram(),
                    getNoiseSettings(level));
            InitialGenerationProgressTracker.setBackend("OPENCL", runtime.deviceName(), null);
            if (ENABLED_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已启用 preference={} nodes={} normalNoises={} splines={}",
                        runtime.devicePreference(),
                        graph.cachedProgram().program().nodes().size(),
                        graph.cachedProgram().program().noiseTables().normalNoises().size(),
                        graph.cachedProgram().program().splines().size());
            }
            return sampler;
        } catch (Throwable t) {
            String reason = "OpenCL 粗采样器创建失败: " + t.getClass().getSimpleName();
            if (OpenCLWorldSupport.markUnsupported(reason)) {
                LOGGER.info("OpenCL 粗采样器创建失败，主世界回退到 CPU: {}", t.getMessage(), t);
            }
            return null;
        }
    }

    public static void clearProgramCache() {
        synchronized (PROGRAM_CACHE_LOCK) {
            for (CachedDensityProgram program : PROGRAM_CACHE.values()) {
                program.close();
            }
            PROGRAM_CACHE.clear();
        }
        synchronized (SESSION_VALIDATION_LOCK) {
            sessionValidationStarted = false;
            sessionValidationFinished = false;
            SESSION_VALIDATION_LOCK.notifyAll();
        }
    }

    private static CachedCompileResult compileInitialDensity(ServerLevel level) {
        try {
            var randomState = level.getChunkSource().getGeneratorState().randomState();
            synchronized (PROGRAM_CACHE_LOCK) {
                if (OpenCLWorldSupport.isUnsupported()) {
                    return CachedCompileResult.unsupported(OpenCLWorldSupport.unsupportedReason());
                }
                CachedDensityProgram cached = PROGRAM_CACHE.get(randomState);
                if (cached != null) {
                    return CachedCompileResult.supported(cached);
                }
                var router = randomState.router();
                DensityGraphCompileResult result = DensityGraphCompiler.compile(router.initialDensityWithoutJaggedness());
                if (!result.supported()) {
                    CachedCompileResult failed = CachedCompileResult.from(result);
                    if (!failed.retryable() && OpenCLWorldSupport.markUnsupported(failed.unsupportedReason())) {
                        LOGGER.info("OpenCL 粗采样暂不支持主世界，回退到 CPU: {}", failed.unsupportedReason());
                    }
                    return failed;
                }
                cached = new CachedDensityProgram(result.program());
                PROGRAM_CACHE.put(randomState, cached);
                return CachedCompileResult.supported(cached);
            }
        } catch (Throwable t) {
            String message = t.getMessage();
            return CachedCompileResult.retryable("initial density graph unavailable: "
                    + t.getClass().getSimpleName()
                    + (message == null || message.isBlank() ? "" : ": " + message));
        }
    }

    @Override
    public int[] sampleHeights(CoarseHeightBatchRequest request) {
        if (request == null) {
            return null;
        }
        if (OpenCLWorldSupport.isUnsupported()) {
            InitialGenerationProgressTracker.setBackend("CPU", "CPU",
                    OpenCLWorldSupport.unsupportedReason());
            return fallback().sampleHeights(request);
        }
        if (program.isEmpty()) {
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", "opencl_program_empty");
            return fallback().sampleHeights(request);
        }
        if (!OpenCLAvailability.isAvailable()) {
            if (DISABLED_FALLBACK_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样已禁用，当前请求直接使用 CPU: {}", OpenCLAvailability.disabledReason());
            }
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", OpenCLAvailability.disabledReason());
            return fallback().sampleHeights(request);
        }
        long startedAt = System.currentTimeMillis();
        try {
            ValidationMode validationMode = validationMode();
            if (validationMode == ValidationMode.NONE && OpenCLWorldSupport.isUnsupported()) {
                InitialGenerationProgressTracker.setBackend("CPU", "CPU",
                        OpenCLWorldSupport.unsupportedReason());
                return fallback().sampleHeights(request);
            }
            int[] heights = sampleHeightsOpenCL(request);
            try {
                if (validationMode.shouldValidate()) {
                    int[] expected = fallback().sampleHeights(request);
                    if (expected == null) {
                        return null;
                    }
                    if (!Arrays.equals(expected, heights)) {
                        String mismatch = firstMismatch(expected, heights);
                        String reason = "OpenCL 粗采样结果校验失败: " + mismatch;
                        if (OpenCLWorldSupport.markUnsupported(reason)) {
                            LOGGER.info("OpenCL 粗采样结果校验失败，主世界回退到 CPU: {} {}",
                                    mismatch,
                                    heightSummary(expected, heights));
                        }
                        InitialGenerationProgressTracker.setBackend("CPU", "CPU", mismatch);
                        return expected;
                    }
                    if (validationMode == ValidationMode.SESSION) {
                        LOGGER.info("OpenCL 粗采样会话首批校验通过: samples={} {}", request.sampleCount(), heightSummary(expected, heights));
                    }
                }
            } finally {
                finishSessionValidation(validationMode);
            }
            if (KERNEL_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("OpenCL 粗采样 kernel 已执行 samples={} nodes={}", request.sampleCount(), program.nodes().size());
            }
            InitialGenerationProgressTracker.recordSampleBatch("OPENCL", request.sampleCount(),
                    System.currentTimeMillis() - startedAt, runtime.deviceName(), null);
            return heights;
        } catch (Throwable t) {
            OpenCLAvailability.disable("OpenCL kernel 执行失败", t);
            InitialGenerationProgressTracker.setBackend("CPU", "CPU", OpenCLAvailability.disabledReason());
            return fallback().sampleHeights(request);
        }
    }

    @Override
    public boolean isAccelerated() {
        return OpenCLAvailability.isAvailable() && !OpenCLWorldSupport.isUnsupported();
    }

    @Override
    public void close() {
        if (fallback != null) {
            fallback.close();
        }
    }

    private CpuCoarseHeightBatchSampler fallback() {
        if (fallback == null) {
            fallback = CpuCoarseHeightBatchSampler.create(level);
        }
        return fallback;
    }

    private int[] sampleHeightsOpenCL(CoarseHeightBatchRequest request) {
        return runtime.submit(OpenCLSubmissionPriority.COARSE,
                () -> sampleHeightsOpenCLLocked(request));
    }

    private int[] sampleHeightsOpenCLLocked(CoarseHeightBatchRequest request) {
        synchronized (cachedProgram) {
            return sampleHeightsOpenCLWithProgram(request);
        }
    }

    private int[] sampleHeightsOpenCLWithProgram(CoarseHeightBatchRequest request) {
        RequestKernelPayload payload = RequestKernelPayload.from(program, request, minY, maxY, cellHeight);
        OpenCLDensityProgramBuffers staticBuffers = cachedProgram.buffers(runtime);
        try (OpenCLBridge.DeviceBuffer params = runtime.upload(payload.params());
             OpenCLBridge.DeviceBuffer scratch = runtime.allocate(OpenCLBridge.BufferAccess.READ_WRITE, payload.scratchBytes());
             OpenCLBridge.DeviceBuffer heights = runtime.allocate(OpenCLBridge.BufferAccess.WRITE_ONLY, intBytes(request.sampleCount()))) {
            List<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(17);
            arguments.add(params);
            arguments.addAll(staticBuffers.arguments());
            arguments.add(scratch);
            arguments.add(heights);
            runtime.execute(OpenCLRuntime.COARSE_HEIGHT_KERNEL, arguments, request.sampleCount());
            return runtime.readInts(heights, request.sampleCount());
        }
    }

    private static long intBytes(int count) {
        return Math.multiplyExact((long) Math.max(1, count), (long) Integer.BYTES);
    }

    private static ValidationMode validationMode() {
        if (shouldValidateSamples()) {
            return ValidationMode.CONFIG;
        }
        synchronized (SESSION_VALIDATION_LOCK) {
            while (sessionValidationStarted && !sessionValidationFinished) {
                try {
                    SESSION_VALIDATION_LOCK.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ValidationMode.NONE;
                }
            }
            if (sessionValidationFinished) {
                return ValidationMode.NONE;
            }
            sessionValidationStarted = true;
            return ValidationMode.SESSION;
        }
    }

    private static void finishSessionValidation(ValidationMode mode) {
        if (mode != ValidationMode.SESSION) {
            return;
        }
        synchronized (SESSION_VALIDATION_LOCK) {
            sessionValidationFinished = true;
            SESSION_VALIDATION_LOCK.notifyAll();
        }
    }

    private static boolean shouldValidateSamples() {
        try {
            return ConfigService.get().performance().openclValidateSamples();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private enum ValidationMode {
        NONE,
        SESSION,
        CONFIG;

        private boolean shouldValidate() {
            return this != NONE;
        }
    }

    private static String firstMismatch(int[] expected, int[] actual) {
        int limit = Math.min(expected.length, actual.length);
        for (int i = 0; i < limit; i++) {
            if (expected[i] != actual[i]) {
                return "index=" + i + " cpu=" + expected[i] + " gpu=" + actual[i];
            }
        }
        return "length cpu=" + expected.length + " gpu=" + actual.length;
    }

    private static String heightSummary(int[] expected, int[] actual) {
        return "cpuRange=" + range(expected) + " gpuRange=" + range(actual);
    }

    private static String range(int[] values) {
        if (values == null || values.length == 0) {
            return "empty";
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min + ".." + max;
    }

    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        return NoiseSettings.create(-64, 384, 1, 2);
    }

    private record CachedCompileResult(
            CachedDensityProgram cachedProgram,
            String unsupportedReason,
            boolean retryable
    ) {
        private static CachedCompileResult supported(CachedDensityProgram program) {
            return new CachedCompileResult(program, null, false);
        }

        private static CachedCompileResult unsupported(String reason) {
            return new CachedCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, false);
        }

        private static CachedCompileResult retryable(String reason) {
            return new CachedCompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason, true);
        }

        private static CachedCompileResult from(DensityGraphCompileResult result) {
            if (result.retryable()) {
                return retryable(result.unsupportedReason());
            }
            return unsupported(result.unsupportedReason());
        }

        private boolean supported() {
            return cachedProgram != null && cachedProgram.program() != null && !cachedProgram.program().isEmpty() && unsupportedReason == null;
        }
    }

    private static final class CachedDensityProgram {
        private final DensityGraphProgram program;
        private final OpenCLDensityProgramPayload payload;
        private final IdentityHashMap<OpenCLRuntime, OpenCLDensityProgramBuffers> buffers = new IdentityHashMap<>();
        private boolean closed;

        private CachedDensityProgram(DensityGraphProgram program) {
            this.program = program;
            this.payload = OpenCLDensityProgramPayload.from(program);
        }

        private DensityGraphProgram program() {
            return program;
        }

        private synchronized OpenCLDensityProgramBuffers buffers(OpenCLRuntime runtime) {
            if (closed) {
                throw new IllegalStateException("coarse OpenCL program is closed");
            }
            return buffers.computeIfAbsent(runtime, key -> OpenCLDensityProgramBuffers.upload(key, payload));
        }

        private void close() {
            IdentityHashMap<OpenCLRuntime, OpenCLDensityProgramBuffers> closing;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                closing = new IdentityHashMap<>(buffers);
                buffers.clear();
            }
            for (var entry : closing.entrySet()) {
                synchronized (entry.getKey().operationLock()) {
                    entry.getValue().close();
                }
            }
        }
    }

    private record RequestKernelPayload(
            int[] params,
            long scratchBytes
    ) {
        private static RequestKernelPayload from(DensityGraphProgram program,
                                                CoarseHeightBatchRequest request,
                                                int minY,
                                                int maxY,
                                                int cellHeight) {
            int sampleCount = request.sampleCount();
            int nodeCount = program.nodes().size();
            int[] params = {
                    sampleCount,
                    minY,
                    maxY,
                    cellHeight,
                    request.minBlockX(),
                    request.minBlockZ(),
                    request.step(),
                    request.sampleWidth(),
                    program.rootNode(),
                    nodeCount,
                    program.noiseTables().normalNoises().size(),
                    program.noiseTables().perlinNoises().size(),
                    program.noiseTables().improvedNoises().size(),
                    program.splines().size()
            };
            long scratchBytes = Math.multiplyExact(
                    Math.multiplyExact((long) sampleCount, (long) Math.max(1, nodeCount)),
                    (long) Double.BYTES);
            return new RequestKernelPayload(params, scratchBytes);
        }
    }

}
