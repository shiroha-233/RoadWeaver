package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;

final class OpenCLAccurateWorkspace implements AutoCloseable {
    private final ReusableBuffer params = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer chunks = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer columns = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer latticeReferences = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer aquiferPositions = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer aquiferPointIndices = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer preliminaryPositions = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer pointPreliminaryIndices = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_ONLY);
    private final ReusableBuffer lattice = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer preliminarySurface = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer aquiferStatus = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer scratch = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer worldSurface = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer oceanFloor = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);
    private final ReusableBuffer motionBlocking = new ReusableBuffer(OpenCLBridge.BufferAccess.READ_WRITE);

    BatchBuffers prepare(OpenCLRuntime runtime,
                         int[] params,
                         int[] chunkCoordinates,
                         int[] columnReferences,
                         int[] latticeReferences,
                         int[] aquiferPositions,
                         int[] aquiferPointIndices,
                         int[] preliminaryPositions,
                         int[] pointPreliminaryIndices,
                         long latticeBytes,
                         long preliminarySurfaceBytes,
                         long aquiferStatusBytes,
                         long scratchBytes,
                         long outputBytes) {
        return new BatchBuffers(
                this.params.write(runtime, params),
                this.chunks.write(runtime, chunkCoordinates),
                this.columns.write(runtime, columnReferences),
                this.latticeReferences.write(runtime, latticeReferences),
                this.aquiferPositions.write(runtime, aquiferPositions),
                this.aquiferPointIndices.write(runtime, aquiferPointIndices),
                this.preliminaryPositions.write(runtime, preliminaryPositions),
                this.pointPreliminaryIndices.write(runtime, pointPreliminaryIndices),
                this.lattice.allocate(runtime, latticeBytes),
                this.preliminarySurface.allocate(runtime, preliminarySurfaceBytes),
                this.aquiferStatus.allocate(runtime, aquiferStatusBytes),
                this.scratch.allocate(runtime, scratchBytes),
                this.worldSurface.allocate(runtime, outputBytes),
                this.oceanFloor.allocate(runtime, outputBytes),
                this.motionBlocking.allocate(runtime, outputBytes));
    }

    @Override
    public void close() {
        params.close();
        chunks.close();
        columns.close();
        latticeReferences.close();
        aquiferPositions.close();
        aquiferPointIndices.close();
        preliminaryPositions.close();
        pointPreliminaryIndices.close();
        lattice.close();
        preliminarySurface.close();
        aquiferStatus.close();
        scratch.close();
        worldSurface.close();
        oceanFloor.close();
        motionBlocking.close();
    }

    record BatchBuffers(OpenCLBridge.DeviceBuffer params,
                        OpenCLBridge.DeviceBuffer chunks,
                        OpenCLBridge.DeviceBuffer columns,
                        OpenCLBridge.DeviceBuffer latticeReferences,
                        OpenCLBridge.DeviceBuffer aquiferPositions,
                        OpenCLBridge.DeviceBuffer aquiferPointIndices,
                        OpenCLBridge.DeviceBuffer preliminaryPositions,
                        OpenCLBridge.DeviceBuffer pointPreliminaryIndices,
                        OpenCLBridge.DeviceBuffer lattice,
                        OpenCLBridge.DeviceBuffer preliminarySurface,
                        OpenCLBridge.DeviceBuffer aquiferStatus,
                        OpenCLBridge.DeviceBuffer scratch,
                        OpenCLBridge.DeviceBuffer worldSurface,
                        OpenCLBridge.DeviceBuffer oceanFloor,
                        OpenCLBridge.DeviceBuffer motionBlocking) {}

    private static final class ReusableBuffer implements AutoCloseable {
        private final OpenCLBridge.BufferAccess access;
        private OpenCLBridge.DeviceBuffer buffer;
        private long capacity;

        private ReusableBuffer(OpenCLBridge.BufferAccess access) {
            this.access = access;
        }

        private OpenCLBridge.DeviceBuffer write(OpenCLRuntime runtime, int[] values) {
            int[] safe = values == null || values.length == 0 ? new int[]{0} : values;
            OpenCLBridge.DeviceBuffer target = ensure(runtime, Math.multiplyExact((long) safe.length, Integer.BYTES));
            runtime.writeInts(target, safe);
            return target;
        }

        private OpenCLBridge.DeviceBuffer allocate(OpenCLRuntime runtime, long bytes) {
            return ensure(runtime, bytes);
        }

        private OpenCLBridge.DeviceBuffer ensure(OpenCLRuntime runtime, long bytes) {
            long required = Math.max(1L, bytes);
            if (buffer == null || capacity < required) {
                if (buffer != null) {
                    buffer.close();
                }
                buffer = runtime.allocate(access, required);
                capacity = required;
            }
            return buffer;
        }

        @Override
        public void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
                capacity = 0L;
            }
        }
    }
}
