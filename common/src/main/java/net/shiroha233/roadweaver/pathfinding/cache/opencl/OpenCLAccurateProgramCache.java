package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 精采程序生命周期缓存，数值失败不会污染其他设备或生成设置。
 */
public final class OpenCLAccurateProgramCache {
    private static final Object CACHE_LOCK = new Object();
    private static final Map<ProgramKey, ProgramState> PROGRAMS = new HashMap<>();

    private OpenCLAccurateProgramCache() {}

    static ProgramState getOrCompile(ServerLevel level,
                                     NoiseBasedChunkGenerator generator,
                                     RandomState randomState,
                                     OpenCLBridge.DeviceInfo device) {
        synchronized (CACHE_LOCK) {
            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            ProgramKey key = new ProgramKey(randomState, settings, device);
            ProgramState cached = PROGRAMS.get(key);
            if (cached != null) {
                return cached;
            }
            OpenCLAccurateProgram.CompileResult compiled = OpenCLAccurateProgram.compile(level, generator, randomState);
            ProgramState state = compiled.supported()
                    ? ProgramState.supported(compiled.program())
                    : ProgramState.unsupported(compiled.unsupportedReason());
            PROGRAMS.put(key, state);
            return state;
        }
    }

    public static void clear(RandomState randomState) {
        if (randomState == null) {
            return;
        }
        synchronized (CACHE_LOCK) {
            Iterator<Map.Entry<ProgramKey, ProgramState>> iterator = PROGRAMS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ProgramKey, ProgramState> entry = iterator.next();
                if (entry.getKey().randomState == randomState) {
                    entry.getValue().close();
                    iterator.remove();
                }
            }
        }
    }

    public static void clear() {
        synchronized (CACHE_LOCK) {
            for (ProgramState state : PROGRAMS.values()) {
                state.close();
            }
            PROGRAMS.clear();
        }
    }

    static final class ProgramState implements AutoCloseable {
        private final OpenCLAccurateProgram program;
        private final Object validationLock = new Object();
        private volatile String unsupportedReason;
        private boolean validationInProgress;
        private boolean validationFinished;

        private ProgramState(OpenCLAccurateProgram program, String unsupportedReason) {
            this.program = program;
            this.unsupportedReason = unsupportedReason;
        }

        static ProgramState supported(OpenCLAccurateProgram program) {
            return new ProgramState(program, null);
        }

        static ProgramState unsupported(String reason) {
            return new ProgramState(null, reason == null || reason.isBlank() ? "unknown" : reason);
        }

        boolean supported() {
            return program != null && unsupportedReason == null;
        }

        String unsupportedReason() {
            return unsupportedReason;
        }

        OpenCLAccurateProgram program() {
            return program;
        }

        ValidationMode acquireValidation(boolean validateEveryBatch) {
            if (validateEveryBatch) {
                return ValidationMode.EVERY_BATCH;
            }
            synchronized (validationLock) {
                while (validationInProgress && !validationFinished) {
                    try {
                        validationLock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return ValidationMode.NONE;
                    }
                }
                if (validationFinished || unsupportedReason != null) {
                    return ValidationMode.NONE;
                }
                validationInProgress = true;
                return ValidationMode.FIRST_PROGRAM_BATCH;
            }
        }

        void finishValidation(ValidationMode mode, boolean completed) {
            if (mode != ValidationMode.FIRST_PROGRAM_BATCH) {
                return;
            }
            synchronized (validationLock) {
                validationInProgress = false;
                if (completed) {
                    validationFinished = true;
                }
                validationLock.notifyAll();
            }
        }

        void markUnsupported(String reason) {
            unsupportedReason = reason == null || reason.isBlank() ? "unknown" : reason;
            synchronized (validationLock) {
                validationInProgress = false;
                validationFinished = true;
                validationLock.notifyAll();
            }
            if (program != null) {
                program.close();
            }
        }

        @Override
        public void close() {
            markUnsupported("program cache cleared");
        }
    }

    enum ValidationMode {
        NONE,
        FIRST_PROGRAM_BATCH,
        EVERY_BATCH
    }

    private static final class ProgramKey {
        private final RandomState randomState;
        private final NoiseGeneratorSettings settings;
        private final OpenCLBridge.DeviceInfo device;

        private ProgramKey(RandomState randomState,
                           NoiseGeneratorSettings settings,
                           OpenCLBridge.DeviceInfo device) {
            this.randomState = randomState;
            this.settings = settings;
            this.device = device;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProgramKey key)) {
                return false;
            }
            return randomState == key.randomState && settings == key.settings && device.equals(key.device);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(randomState);
            result = 31 * result + System.identityHashCode(settings);
            result = 31 * result + device.hashCode();
            return result;
        }
    }
}
