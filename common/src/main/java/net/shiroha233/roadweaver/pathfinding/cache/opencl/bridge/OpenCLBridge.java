package net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge;

import net.shiroha233.roadweaver.pathfinding.cache.opencl.OpenCLDevicePreference;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenCL native bridge。桥外代码不得持有或操作原生 handle。
 */
public final class OpenCLBridge {
    private static final int CL_DEVICE_DOUBLE_FP_CONFIG = 0x1032;
    private static final Object INIT_LOCK = new Object();
    private static boolean initialized;

    private OpenCLBridge() {}

    public static Session open(OpenRequest request) {
        ensureInitialized();
        DeviceSelection selection = selectDevice(request.preference(), request.gpuOnly(), request.requireFp64());
        if (selection == null) {
            return null;
        }
        return createSession(request, selection);
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            try {
                CL.create();
            } catch (IllegalStateException alreadyCreated) {
                String message = alreadyCreated.getMessage();
                if (message == null || !message.contains("already been created")) {
                    throw alreadyCreated;
                }
            }
            initialized = true;
        }
    }

    private static Session createSession(OpenRequest request, DeviceSelection selection) {
        long context = 0L;
        long queue = 0L;
        long program = 0L;
        LinkedHashMap<String, Long> kernels = new LinkedHashMap<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            PointerBuffer properties = stack.mallocPointer(3);
            properties.put(CL10.CL_CONTEXT_PLATFORM).put(selection.platform()).put(0L).flip();
            PointerBuffer devices = stack.mallocPointer(1);
            devices.put(selection.device()).flip();

            context = CL10.clCreateContext(properties, devices, null, 0L, error);
            check(error.get(0), "clCreateContext");
            queue = CL10.clCreateCommandQueue(context, selection.device(), CL10.CL_QUEUE_PROFILING_ENABLE, error);
            check(error.get(0), "clCreateCommandQueue");
            program = CL10.clCreateProgramWithSource(context, request.source(), error);
            check(error.get(0), "clCreateProgramWithSource");

            PointerBuffer buildDevices = stack.mallocPointer(1);
            buildDevices.put(selection.device()).flip();
            int buildResult = CL10.clBuildProgram(program, buildDevices, "", null, 0L);
            if (buildResult != CL10.CL_SUCCESS) {
                throw new IllegalStateException("clBuildProgram failed: " + buildResult + buildLog(program, selection.device()));
            }
            for (String kernelName : request.kernelNames()) {
                long kernel = CL10.clCreateKernel(program, kernelName, error);
                check(error.get(0), "clCreateKernel(" + kernelName + ")");
                kernels.put(kernelName, kernel);
            }

            DeviceInfo info = deviceInfo(selection.device(), selection.type());
            return new Session(context, queue, program, kernels, info);
        } catch (Throwable failure) {
            releaseAll(kernels.values(), CL10::clReleaseKernel);
            release(program, CL10::clReleaseProgram);
            release(queue, CL10::clReleaseCommandQueue);
            release(context, CL10::clReleaseContext);
            throw failure;
        }
    }

    private static DeviceSelection selectDevice(OpenCLDevicePreference preference,
                                                boolean gpuOnly,
                                                boolean requireFp64) {
        OpenCLDevicePreference safePreference = preference == null ? OpenCLDevicePreference.AUTO : preference;
        long[] types = gpuOnly ? new long[]{CL10.CL_DEVICE_TYPE_GPU} : deviceTypes(safePreference);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            int platformResult = CL10.clGetPlatformIDs((PointerBuffer) null, count);
            if (platformResult != CL10.CL_SUCCESS || count.get(0) <= 0) {
                return null;
            }
            PointerBuffer platforms = stack.mallocPointer(count.get(0));
            if (CL10.clGetPlatformIDs(platforms, count) != CL10.CL_SUCCESS) {
                return null;
            }

            for (long type : types) {
                for (int platformIndex = 0; platformIndex < platforms.capacity(); platformIndex++) {
                    long platform = platforms.get(platformIndex);
                    count.put(0, 0);
                    int deviceResult = CL10.clGetDeviceIDs(platform, type, (PointerBuffer) null, count);
                    if (deviceResult != CL10.CL_SUCCESS || count.get(0) <= 0) {
                        continue;
                    }
                    PointerBuffer devices = stack.mallocPointer(count.get(0));
                    if (CL10.clGetDeviceIDs(platform, type, devices, (IntBuffer) null) != CL10.CL_SUCCESS) {
                        continue;
                    }
                    for (int deviceIndex = 0; deviceIndex < devices.capacity(); deviceIndex++) {
                        long device = devices.get(deviceIndex);
                        if (!requireFp64 || supportsFp64(device)) {
                            return new DeviceSelection(platform, device, type);
                        }
                    }
                }
            }
            return null;
        }
    }

    private static boolean supportsFp64(long device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer config = stack.mallocLong(1);
            int result = CL10.clGetDeviceInfo(device, CL_DEVICE_DOUBLE_FP_CONFIG, config, null);
            if (result == CL10.CL_SUCCESS && config.get(0) != 0L) {
                return true;
            }
        }
        String extensions = infoString((size, value) -> CL10.clGetDeviceInfo(device, CL10.CL_DEVICE_EXTENSIONS, value, size));
        return extensions.contains("cl_khr_fp64");
    }

    private static DeviceInfo deviceInfo(long device, long type) {
        String name = infoString((size, value) -> CL10.clGetDeviceInfo(device, CL10.CL_DEVICE_NAME, value, size)).trim();
        long globalMemory = infoLong(device, CL10.CL_DEVICE_GLOBAL_MEM_SIZE);
        long maxAllocation = infoLong(device, CL10.CL_DEVICE_MAX_MEM_ALLOC_SIZE);
        long maxWorkGroup = infoSize(device, CL10.CL_DEVICE_MAX_WORK_GROUP_SIZE);
        return new DeviceInfo(
                name.isBlank() ? "unknown" : name,
                type == CL10.CL_DEVICE_TYPE_GPU,
                supportsFp64(device),
                globalMemory,
                maxAllocation,
                maxWorkGroup);
    }

    private static long infoLong(long device, int parameter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer value = stack.mallocLong(1);
            return CL10.clGetDeviceInfo(device, parameter, value, null) == CL10.CL_SUCCESS ? value.get(0) : 0L;
        }
    }

    private static long infoSize(long device, int parameter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer value = stack.mallocPointer(1);
            return CL10.clGetDeviceInfo(device, parameter, value, null) == CL10.CL_SUCCESS ? value.get(0) : 0L;
        }
    }

    private static long[] deviceTypes(OpenCLDevicePreference preference) {
        return switch (preference) {
            case GPU -> new long[]{CL10.CL_DEVICE_TYPE_GPU};
            case CPU -> new long[]{CL10.CL_DEVICE_TYPE_CPU};
            case AUTO -> new long[]{CL10.CL_DEVICE_TYPE_GPU, CL10.CL_DEVICE_TYPE_CPU};
        };
    }

    private static String buildLog(long program, long device) {
        String log = infoString((size, value) -> CL10.clGetProgramBuildInfo(
                program, device, CL10.CL_PROGRAM_BUILD_LOG, value, size));
        return log.isBlank() ? "" : System.lineSeparator() + log;
    }

    private static String infoString(InfoQuery query) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.mallocPointer(1);
            if (query.query(size, null) != CL10.CL_SUCCESS || size.get(0) <= 0L) {
                return "";
            }
            ByteBuffer value = MemoryUtil.memAlloc(Math.toIntExact(size.get(0)));
            try {
                if (query.query(null, value) != CL10.CL_SUCCESS) {
                    return "";
                }
                int length = value.remaining();
                if (length > 0 && value.get(length - 1) == 0) {
                    length--;
                }
                byte[] bytes = new byte[length];
                value.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                MemoryUtil.memFree(value);
            }
        }
    }

    private static void check(int result, String operation) {
        if (result != CL10.CL_SUCCESS) {
            throw new IllegalStateException(operation + " failed: " + result);
        }
    }

    private static void release(long handle, ReleaseOperation operation) {
        if (handle != 0L) {
            try {
                operation.release(handle);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void releaseAll(Collection<Long> handles, ReleaseOperation operation) {
        for (long handle : handles) {
            release(handle, operation);
        }
    }

    public record OpenRequest(OpenCLDevicePreference preference,
                              boolean gpuOnly,
                              boolean requireFp64,
                              String source,
                              List<String> kernelNames) {
        public OpenRequest {
            preference = preference == null ? OpenCLDevicePreference.AUTO : preference;
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("OpenCL source must not be blank");
            }
            kernelNames = List.copyOf(kernelNames == null ? List.of() : kernelNames);
            if (kernelNames.isEmpty()) {
                throw new IllegalArgumentException("At least one OpenCL kernel is required");
            }
        }
    }

    public record DeviceInfo(String name,
                             boolean gpu,
                             boolean fp64,
                             long globalMemoryBytes,
                             long maxAllocationBytes,
                             long maxWorkGroupSize) {}

    public enum BufferAccess {
        READ_ONLY(CL10.CL_MEM_READ_ONLY),
        WRITE_ONLY(CL10.CL_MEM_WRITE_ONLY),
        READ_WRITE(CL10.CL_MEM_READ_WRITE);

        private final long flags;

        BufferAccess(long flags) {
            this.flags = flags;
        }
    }

    public static final class Session implements AutoCloseable {
        private final long context;
        private final long queue;
        private final long program;
        private final Map<String, Long> kernels;
        private final DeviceInfo deviceInfo;
        private final Set<DeviceBuffer> buffers = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Session(long context,
                        long queue,
                        long program,
                        Map<String, Long> kernels,
                        DeviceInfo deviceInfo) {
            this.context = context;
            this.queue = queue;
            this.program = program;
            this.kernels = Map.copyOf(kernels);
            this.deviceInfo = deviceInfo;
        }

        public DeviceInfo deviceInfo() {
            return deviceInfo;
        }

        public DeviceBuffer allocate(BufferAccess access, long bytes) {
            ensureOpen();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long handle = CL10.clCreateBuffer(context, access.flags, Math.max(1L, bytes), error);
                check(error.get(0), "clCreateBuffer");
                DeviceBuffer buffer = new DeviceBuffer(this, handle, Math.max(1L, bytes));
                buffers.add(buffer);
                return buffer;
            }
        }

        public DeviceBuffer upload(int[] values) {
            int[] safe = values == null || values.length == 0 ? new int[]{0} : values;
            DeviceBuffer buffer = allocate(BufferAccess.READ_ONLY,
                    Math.multiplyExact((long) safe.length, Integer.BYTES));
            try {
                writeInts(buffer, safe);
                return buffer;
            } catch (Throwable failure) {
                buffer.close();
                throw failure;
            }
        }

        public void writeInts(DeviceBuffer buffer, int[] values) {
            ensureOpen();
            if (buffer == null || buffer.owner != this) {
                throw new IllegalArgumentException("Device buffer belongs to another OpenCL session");
            }
            int[] safe = values == null || values.length == 0 ? new int[]{0} : values;
            long bytes = Math.multiplyExact((long) safe.length, Integer.BYTES);
            if (bytes > buffer.bytes) {
                throw new IllegalArgumentException("OpenCL buffer is too small for integer upload");
            }
            IntBuffer host = MemoryUtil.memAllocInt(safe.length);
            try {
                host.put(safe).flip();
                check(CL10.clEnqueueWriteBuffer(queue, buffer.handle, true, 0L, host, null, null), "clEnqueueWriteBuffer(int)");
            } finally {
                MemoryUtil.memFree(host);
            }
        }

        public DeviceBuffer upload(double[] values) {
            double[] safe = values == null || values.length == 0 ? new double[]{0.0D} : values;
            DeviceBuffer buffer = allocate(BufferAccess.READ_ONLY, Math.multiplyExact((long) safe.length, Double.BYTES));
            DoubleBuffer host = MemoryUtil.memAllocDouble(safe.length);
            try {
                host.put(safe).flip();
                check(CL10.clEnqueueWriteBuffer(queue, buffer.handle, true, 0L, host, null, null), "clEnqueueWriteBuffer(double)");
                return buffer;
            } catch (Throwable failure) {
                buffer.close();
                throw failure;
            } finally {
                MemoryUtil.memFree(host);
            }
        }

        public long execute(String kernelName, List<DeviceBuffer> arguments, long workItems) {
            try (CommandEvent event = enqueue(kernelName, arguments, workItems)) {
                return event.awaitNanos();
            }
        }

        public CommandEvent enqueue(String kernelName, List<DeviceBuffer> arguments, long workItems) {
            ensureOpen();
            Long kernel = kernels.get(kernelName);
            if (kernel == null) {
                throw new IllegalArgumentException("Unknown OpenCL kernel: " + kernelName);
            }
            for (int index = 0; index < arguments.size(); index++) {
                DeviceBuffer buffer = arguments.get(index);
                check(CL10.clSetKernelArg1p(kernel, index, buffer.handle), "clSetKernelArg(" + index + ")");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer globalWorkSize = stack.mallocPointer(1);
                PointerBuffer event = stack.mallocPointer(1);
                globalWorkSize.put(0, Math.max(1L, workItems));
                check(CL10.clEnqueueNDRangeKernel(
                        queue, kernel, 1, null, globalWorkSize, null, null, event), "clEnqueueNDRangeKernel");
                return new CommandEvent(this, event.get(0));
            }
        }

        public int[] readInts(DeviceBuffer buffer, int count) {
            ensureOpen();
            IntBuffer host = MemoryUtil.memAllocInt(Math.max(1, count));
            try {
                check(CL10.clEnqueueReadBuffer(queue, buffer.handle, true, 0L, host, null, null), "clEnqueueReadBuffer(int)");
                int[] result = new int[count];
                host.get(result);
                return result;
            } finally {
                MemoryUtil.memFree(host);
            }
        }

        private void release(DeviceBuffer buffer) {
            if (buffers.remove(buffer)) {
                OpenCLBridge.release(buffer.handle, CL10::clReleaseMemObject);
            }
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new IllegalStateException("OpenCL session is closed");
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (DeviceBuffer buffer : new ArrayList<>(buffers)) {
                buffer.close();
            }
            releaseAll(kernels.values(), CL10::clReleaseKernel);
            OpenCLBridge.release(program, CL10::clReleaseProgram);
            OpenCLBridge.release(queue, CL10::clReleaseCommandQueue);
            OpenCLBridge.release(context, CL10::clReleaseContext);
        }
    }

    public static final class CommandEvent implements AutoCloseable {
        private final Session owner;
        private final long handle;
        private final AtomicBoolean closed = new AtomicBoolean();
        private long durationNanos = -1L;

        private CommandEvent(Session owner, long handle) {
            this.owner = owner;
            this.handle = handle;
        }

        public synchronized long awaitNanos() {
            if (durationNanos >= 0L) {
                return durationNanos;
            }
            owner.ensureOpen();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer event = stack.mallocPointer(1);
                event.put(0, handle);
                check(CL10.clWaitForEvents(event), "clWaitForEvents");
                LongBuffer started = stack.mallocLong(1);
                LongBuffer finished = stack.mallocLong(1);
                check(CL10.clGetEventProfilingInfo(
                        handle, CL10.CL_PROFILING_COMMAND_START, started, null), "clGetEventProfilingInfo(start)");
                check(CL10.clGetEventProfilingInfo(
                        handle, CL10.CL_PROFILING_COMMAND_END, finished, null), "clGetEventProfilingInfo(end)");
                durationNanos = Math.max(0L, finished.get(0) - started.get(0));
                return durationNanos;
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                OpenCLBridge.release(handle, CL10::clReleaseEvent);
            }
        }
    }

    public static final class DeviceBuffer implements AutoCloseable {
        private final Session owner;
        private final long handle;
        private final long bytes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private DeviceBuffer(Session owner, long handle, long bytes) {
            this.owner = owner;
            this.handle = handle;
            this.bytes = bytes;
        }

        public long bytes() {
            return bytes;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(this);
            }
        }
    }

    private record DeviceSelection(long platform, long device, long type) {}

    @FunctionalInterface
    private interface ReleaseOperation {
        int release(long handle);
    }

    @FunctionalInterface
    private interface InfoQuery {
        int query(PointerBuffer size, ByteBuffer value);
    }
}
