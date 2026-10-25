package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightChunk;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightGrid;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightGridRequest;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingProgress;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与一个 RandomState 绑定的精确采样 GPU 程序。
 */
final class OpenCLAccurateProgram implements AutoCloseable {
    private static final int HEIGHT_PARALLEL_LANES = 64;

    private final DensityGraphProgram graph;
    private final OpenCLDensityProgramPayload payload;
    private final NoiseSettings noiseSettings;
    private final PositionalRandomFactory aquiferRandom;
    private final boolean aquifersEnabled;
    private final int seaLevel;
    private final int defaultFluidKind;
    private final int defaultBlockMask;
    private final int defaultFluidMask;
    private final int lavaMask;
    private final IdentityHashMap<OpenCLRuntime, OpenCLDensityProgramBuffers> buffers = new IdentityHashMap<>();
    private final IdentityHashMap<OpenCLRuntime, OpenCLAccurateWorkspace> workspaces = new IdentityHashMap<>();
    private boolean closed;

    private OpenCLAccurateProgram(DensityGraphProgram graph,
                                  NoiseSettings noiseSettings,
                                  PositionalRandomFactory aquiferRandom,
                                  NoiseGeneratorSettings settings,
                                  int defaultBlockMask,
                                  int defaultFluidMask,
                                  int lavaMask) {
        this.graph = graph;
        this.payload = OpenCLDensityProgramPayload.from(graph);
        this.noiseSettings = noiseSettings;
        this.aquiferRandom = aquiferRandom;
        this.aquifersEnabled = settings.isAquifersEnabled();
        this.seaLevel = settings.seaLevel();
        this.defaultFluidKind = HeightmapMaterialClassifier.fluidKind(settings.defaultFluid());
        this.defaultBlockMask = defaultBlockMask;
        this.defaultFluidMask = defaultFluidMask;
        this.lavaMask = lavaMask;
    }

    static CompileResult compile(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState) {
        return compile(level, generator.generatorSettings().value(), randomState);
    }

    static CompileResult compile(LevelHeightAccessor level,
                                 NoiseGeneratorSettings settings,
                                 RandomState randomState) {
        try {
            int defaultBlockMask = HeightmapMaterialClassifier.mask(settings.defaultBlock());
            if (defaultBlockMask != HeightmapMaterialClassifier.REQUIRED_SOLID_MASK) {
                return CompileResult.unsupported("default block heightmap predicates are not solid");
            }
            if (settings.oreVeinsEnabled() && !HeightmapMaterialClassifier.oreVeinOutputsMatch(defaultBlockMask)) {
                return CompileResult.unsupported("ore vein material predicates differ from default block");
            }

            NoiseRouter router = randomState.router();
            EnumMap<DensityGraphRoot, net.minecraft.world.level.levelgen.DensityFunction> roots =
                    new EnumMap<>(DensityGraphRoot.class);
            roots.put(DensityGraphRoot.FINAL_DENSITY, router.finalDensity());
            roots.put(DensityGraphRoot.INITIAL_DENSITY_WITHOUT_JAGGEDNESS, router.initialDensityWithoutJaggedness());
            roots.put(DensityGraphRoot.BARRIER, router.barrierNoise());
            roots.put(DensityGraphRoot.FLUID_FLOODEDNESS, router.fluidLevelFloodednessNoise());
            roots.put(DensityGraphRoot.FLUID_SPREAD, router.fluidLevelSpreadNoise());
            roots.put(DensityGraphRoot.LAVA, router.lavaNoise());
            roots.put(DensityGraphRoot.EROSION, router.erosion());
            roots.put(DensityGraphRoot.DEPTH, router.depth());
            DensityGraphCompileResult compiled = DensityGraphCompiler.compile(roots);
            if (!compiled.supported()) {
                return CompileResult.unsupported(compiled.unsupportedReason());
            }
            NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
            return CompileResult.supported(new OpenCLAccurateProgram(
                    compiled.program(),
                    noiseSettings,
                    randomState.aquiferRandom(),
                    settings,
                    defaultBlockMask,
                    HeightmapMaterialClassifier.mask(settings.defaultFluid()),
                    HeightmapMaterialClassifier.mask(Blocks.LAVA.defaultBlockState())));
        } catch (Throwable failure) {
            return CompileResult.unsupported("accurate OpenCL program compile failed: "
                    + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        }
    }

    synchronized Map<Long, AccurateHeightChunk> sample(OpenCLRuntime runtime, Collection<Long> requestedKeys) {
        List<Long> keys = List.copyOf(requestedKeys);
        if (keys.isEmpty()) {
            return Map.of();
        }
        int cellCountXZ = 16 / noiseSettings.getCellWidth();
        int cellCountY = Math.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
        int latticePoints = OpenCLAccurateLayout.latticePointCount(cellCountXZ, cellCountY);
        int minGridY = Math.floorDiv(noiseSettings.minY(), 12) - 1;
        int gridYSize = Math.floorDiv(noiseSettings.minY() + noiseSettings.height(), 12) + 1 - minGridY + 1;
        int aquiferPoints = aquifersEnabled ? Math.multiplyExact(9, gridYSize) : 0;
        int maxChunks = OpenCLAccurateBatchPlanner.maxChunks(
                runtime.deviceInfo(),
                graph.nodes().size(),
                graph.interpolatedNodes().size(),
                latticePoints,
                aquiferPoints);
        if (maxChunks <= 0) {
            throw new IllegalStateException("OpenCL device memory cannot fit one accurate chunk batch");
        }

        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>(keys.size());
        long startedAt = System.nanoTime();
        long[] kernelNanos = {0L};
        runtime.submit(OpenCLSubmissionPriority.ACCURATE, () -> {
            for (List<Long> batchChunks : OpenCLAccurateBatchPlanner.spatialBatches(keys, maxChunks)) {
                BatchResult batch = sampleBatch(runtime, batchChunks, latticePoints);
                result.putAll(batch.chunks());
                kernelNanos[0] += batch.kernelNanos();
            }
            return null;
        });
        AccurateSamplingStats.recordGpuBatch(result.size(), System.nanoTime() - startedAt, kernelNanos[0]);
        return result;
    }

    Map<Long, AccurateHeightSample> samplePositions(OpenCLRuntime runtime, Collection<BlockPos> requestedPositions) {
        return samplePositions(runtime, requestedPositions, AccurateSamplingProgress.NONE);
    }

    synchronized Map<Long, AccurateHeightSample> samplePositions(OpenCLRuntime runtime,
                                                                 Collection<BlockPos> requestedPositions,
                                                                 AccurateSamplingProgress progress) {
        LinkedHashMap<Long, ColumnRequest> requested = uniqueColumns(requestedPositions);
        if (requested.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<Long, List<ColumnRequest>> columnsByChunk = groupColumnsByChunk(requested.values());
        List<Long> chunkKeys = new ArrayList<>(columnsByChunk.keySet());
        int latticePoints = latticePoints();
        int maxChunks = maxChunks(runtime, latticePoints);
        if (maxChunks <= 0) {
            throw new IllegalStateException("OpenCL device memory cannot fit one accurate chunk batch");
        }

        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>(requested.size());
        long startedAt = System.nanoTime();
        long[] kernelNanos = {0L};
        long[] completedColumns = {0L};
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        runtime.submit(OpenCLSubmissionPriority.ACCURATE, () -> {
            for (List<Long> batchChunks : OpenCLAccurateBatchPlanner.spatialBatches(chunkKeys, maxChunks)) {
                long batchStartedAt = System.nanoTime();
                LinkedHashMap<Long, Integer> localChunkIndexes = new LinkedHashMap<>();
                for (int index = 0; index < batchChunks.size(); index++) {
                    localChunkIndexes.put(batchChunks.get(index), index);
                }
                int batchColumnCount = 0;
                for (long chunkKey : batchChunks) {
                    batchColumnCount += columnsByChunk.get(chunkKey).size();
                }
                List<ColumnRequest> batchColumns = new ArrayList<>(batchColumnCount);
                for (long chunkKey : batchChunks) {
                    batchColumns.addAll(columnsByChunk.get(chunkKey));
                }
                HeightColumns heights = sampleColumnsBatch(
                        runtime,
                        batchChunks,
                        latticePoints,
                        columnReferences(batchColumns, localChunkIndexes));
                kernelNanos[0] += heights.kernelNanos();
                for (int index = 0; index < batchColumns.size(); index++) {
                    ColumnRequest column = batchColumns.get(index);
                    result.put(column.key(), new AccurateHeightSample(
                            heights.worldSurface()[index],
                            heights.oceanFloor()[index],
                            heights.motionBlocking()[index]));
                }
                completedColumns[0] += batchColumns.size();
                reportProgress(sink, new AccurateSamplingProgress.Batch(
                        completedColumns[0], requested.size(), batchColumns.size(),
                        System.nanoTime() - startedAt, System.nanoTime() - batchStartedAt,
                        heights.kernelNanos()));
            }
            return null;
        });
        AccurateSamplingStats.recordGpuColumnBatch(result.size(), System.nanoTime() - startedAt, kernelNanos[0]);
        return result;
    }

    synchronized AccurateHeightGrid sampleGrid(OpenCLRuntime runtime,
                                               AccurateHeightGridRequest request,
                                               AccurateSamplingProgress progress) {
        LinkedHashMap<Long, GridIndexList> indicesByChunk = new LinkedHashMap<>();
        for (int index = 0; index < request.sampleCount(); index++) {
            long chunkKey = ChunkPos.asLong(request.blockX(index) >> 4, request.blockZ(index) >> 4);
            indicesByChunk.computeIfAbsent(chunkKey, ignored -> new GridIndexList()).add(index);
        }
        List<Long> chunkKeys = new ArrayList<>(indicesByChunk.keySet());
        int latticePoints = latticePoints();
        int maxChunks = maxChunks(runtime, latticePoints);
        if (maxChunks <= 0) {
            throw new IllegalStateException("OpenCL device memory cannot fit one accurate chunk batch");
        }

        int[] worldSurface = new int[request.sampleCount()];
        int[] oceanFloor = new int[request.sampleCount()];
        int[] motionBlocking = new int[request.sampleCount()];
        long startedAt = System.nanoTime();
        long[] kernelNanos = {0L};
        long[] completedColumns = {0L};
        AccurateSamplingProgress sink = progress == null ? AccurateSamplingProgress.NONE : progress;
        runtime.submit(OpenCLSubmissionPriority.ACCURATE, () -> {
            for (List<Long> batchChunks : OpenCLAccurateBatchPlanner.spatialBatches(chunkKeys, maxChunks)) {
                long batchStartedAt = System.nanoTime();
                LinkedHashMap<Long, Integer> localChunkIndexes = new LinkedHashMap<>();
                int batchColumnCount = 0;
                for (int index = 0; index < batchChunks.size(); index++) {
                    long chunkKey = batchChunks.get(index);
                    localChunkIndexes.put(chunkKey, index);
                    batchColumnCount += indicesByChunk.get(chunkKey).size();
                }

                int[] columnReferences = new int[Math.multiplyExact(batchColumnCount, 3)];
                int[] outputIndices = new int[batchColumnCount];
                int columnIndex = 0;
                for (long chunkKey : batchChunks) {
                    int localChunkIndex = localChunkIndexes.get(chunkKey);
                    GridIndexList gridIndices = indicesByChunk.get(chunkKey);
                    for (int offset = 0; offset < gridIndices.size(); offset++) {
                        int gridIndex = gridIndices.get(offset);
                        int blockX = request.blockX(gridIndex);
                        int blockZ = request.blockZ(gridIndex);
                        int referenceOffset = columnIndex * 3;
                        columnReferences[referenceOffset] = localChunkIndex;
                        columnReferences[referenceOffset + 1] = blockX & 15;
                        columnReferences[referenceOffset + 2] = blockZ & 15;
                        outputIndices[columnIndex] = gridIndex;
                        columnIndex++;
                    }
                }

                HeightColumns heights = sampleColumnsBatch(
                        runtime, batchChunks, latticePoints, columnReferences);
                kernelNanos[0] += heights.kernelNanos();
                for (int index = 0; index < outputIndices.length; index++) {
                    int outputIndex = outputIndices[index];
                    worldSurface[outputIndex] = heights.worldSurface()[index];
                    oceanFloor[outputIndex] = heights.oceanFloor()[index];
                    motionBlocking[outputIndex] = heights.motionBlocking()[index];
                }
                completedColumns[0] += batchColumnCount;
                reportProgress(sink, new AccurateSamplingProgress.Batch(
                        completedColumns[0], request.sampleCount(), batchColumnCount,
                        System.nanoTime() - startedAt, System.nanoTime() - batchStartedAt,
                        heights.kernelNanos()));
            }
            return null;
        });
        AccurateSamplingStats.recordGpuColumnBatch(
                request.sampleCount(), System.nanoTime() - startedAt, kernelNanos[0]);
        return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
    }

    private BatchResult sampleBatch(OpenCLRuntime runtime,
                                    List<Long> chunkKeys,
                                    int latticePointsPerChunk) {
        HeightColumns heights = sampleColumnsBatch(
                runtime,
                chunkKeys,
                latticePointsPerChunk,
                fullColumnReferences(chunkKeys.size()));
        return new BatchResult(unpack(chunkKeys, heights.worldSurface(), heights.oceanFloor(), heights.motionBlocking()),
                heights.kernelNanos());
    }

    private HeightColumns sampleColumnsBatch(OpenCLRuntime runtime,
                                             List<Long> chunkKeys,
                                             int latticePointsPerChunk,
                                             int[] columnReferences) {
        AquiferPositionPlanner.Plan aquiferPlan = aquifersEnabled
                ? AquiferPositionPlanner.plan(chunkKeys, aquiferRandom, noiseSettings.minY(), noiseSettings.height())
                : emptyAquiferPlan(chunkKeys);
        int[] params = params(chunkKeys.size(), latticePointsPerChunk, aquiferPlan);
        int chunkCount = chunkKeys.size();
        int nodeCount = graph.nodes().size();
        int interpolatorCount = graph.interpolatedNodes().size();
        SparseLatticePlanner.Plan latticePlan = SparseLatticePlanner.plan(
                chunkCount,
                columnReferences,
                noiseSettings.getCellWidth(),
                16 / noiseSettings.getCellWidth(),
                noiseSettings.height() / noiseSettings.getCellHeight());
        params[33] = latticePlan.sparse() ? 1 : 0;
        int latticeWorkItems = interpolatorCount == 0
                ? 0
                : latticePlan.workItemCount();
        int preliminaryPointCount = aquifersEnabled ? aquiferPlan.preliminaryPointCount() : 0;
        int preliminaryWorkItems = Math.multiplyExact(
                preliminaryPointCount,
                noiseSettings.height() / noiseSettings.getCellHeight() + 1);
        int aquiferWorkItems = aquifersEnabled ? aquiferPlan.uniquePointCount() : 0;
        int columnWorkItems = Math.floorDiv(columnReferences.length, 3);
        int parallelHeightWorkItems = Math.multiplyExact(columnWorkItems, HEIGHT_PARALLEL_LANES);
        boolean parallelHeight = shouldParallelizeHeight(
                runtime, parallelHeightWorkItems, latticeWorkItems, nodeCount, interpolatorCount,
                latticePointsPerChunk, chunkCount);
        int heightScratchItems = parallelHeight ? parallelHeightWorkItems : columnWorkItems;
        int scratchItems = Math.max(heightScratchItems,
                Math.max(latticeWorkItems, Math.max(preliminaryWorkItems, aquiferWorkItems)));
        long scratchBytes = Math.multiplyExact(
                Math.multiplyExact((long) Math.max(1, scratchItems), (long) Math.max(1, nodeCount)),
                Double.BYTES);
        long latticeBytes = Math.multiplyExact(
                Math.multiplyExact(
                        Math.multiplyExact((long) chunkCount, (long) Math.max(1, interpolatorCount)),
                        (long) latticePointsPerChunk),
                Double.BYTES);
        long aquiferStatusBytes = Math.multiplyExact(
                Math.multiplyExact((long) Math.max(1, aquiferWorkItems), 2L),
                Integer.BYTES);
        long preliminarySurfaceBytes = Math.multiplyExact(
                (long) Math.max(1, preliminaryPointCount), Integer.BYTES);
        long outputBytes = Math.multiplyExact((long) columnWorkItems, Integer.BYTES);

        OpenCLDensityProgramBuffers graphBuffers = buffers(runtime);
        OpenCLAccurateWorkspace.BatchBuffers batchBuffers = workspace(runtime).prepare(
                runtime,
                params,
                aquiferPlan.chunkCoordinates(),
                columnReferences,
                latticePlan.references(),
                aquiferPlan.positions(),
                aquiferPlan.chunkPointIndices(),
                aquiferPlan.preliminaryCoordinates(),
                aquiferPlan.pointPreliminaryIndices(),
                latticeBytes,
                preliminarySurfaceBytes,
                aquiferStatusBytes,
                scratchBytes,
                outputBytes);
        OpenCLBridge.DeviceBuffer paramsBuffer = batchBuffers.params();
        OpenCLBridge.DeviceBuffer chunksBuffer = batchBuffers.chunks();
        OpenCLBridge.DeviceBuffer columnsBuffer = batchBuffers.columns();
        OpenCLBridge.DeviceBuffer latticeReferencesBuffer = batchBuffers.latticeReferences();
        OpenCLBridge.DeviceBuffer aquiferPositionsBuffer = batchBuffers.aquiferPositions();
        OpenCLBridge.DeviceBuffer aquiferPointIndicesBuffer = batchBuffers.aquiferPointIndices();
        OpenCLBridge.DeviceBuffer preliminaryPositionsBuffer = batchBuffers.preliminaryPositions();
        OpenCLBridge.DeviceBuffer pointPreliminaryIndicesBuffer = batchBuffers.pointPreliminaryIndices();
        OpenCLBridge.DeviceBuffer latticeBuffer = batchBuffers.lattice();
        OpenCLBridge.DeviceBuffer preliminarySurfaceBuffer = batchBuffers.preliminarySurface();
        OpenCLBridge.DeviceBuffer aquiferStatusBuffer = batchBuffers.aquiferStatus();
        OpenCLBridge.DeviceBuffer scratchBuffer = batchBuffers.scratch();
        OpenCLBridge.DeviceBuffer worldSurfaceBuffer = batchBuffers.worldSurface();
        OpenCLBridge.DeviceBuffer oceanFloorBuffer = batchBuffers.oceanFloor();
        OpenCLBridge.DeviceBuffer motionBlockingBuffer = batchBuffers.motionBlocking();

        ArrayList<OpenCLBridge.CommandEvent> events = new ArrayList<>(6);
        ArrayList<OpenCLBridge.CommandEvent> latticeEvents = new ArrayList<>(1);
        ArrayList<OpenCLBridge.CommandEvent> preliminaryEvents = new ArrayList<>(2);
        ArrayList<OpenCLBridge.CommandEvent> aquiferEvents = new ArrayList<>(1);
        ArrayList<OpenCLBridge.CommandEvent> heightEvents = new ArrayList<>(2);
        try {
                if (latticeWorkItems > 0) {
                    ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(20);
                    arguments.add(paramsBuffer);
                    arguments.add(chunksBuffer);
                    arguments.add(latticeReferencesBuffer);
                    arguments.addAll(graphBuffers.arguments());
                    arguments.add(graphBuffers.interpolatedNodes());
                    arguments.add(latticeBuffer);
                    arguments.add(scratchBuffer);
                    OpenCLBridge.CommandEvent event = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_LATTICE_KERNEL, arguments, latticeWorkItems);
                    events.add(event);
                    latticeEvents.add(event);
                }
                if (preliminaryWorkItems > 0) {
                    ArrayList<OpenCLBridge.DeviceBuffer> initArguments = new ArrayList<>(2);
                    initArguments.add(paramsBuffer);
                    initArguments.add(preliminarySurfaceBuffer);
                    OpenCLBridge.CommandEvent initEvent = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_PRELIMINARY_INIT_KERNEL,
                            initArguments,
                            preliminaryPointCount);
                    events.add(initEvent);
                    preliminaryEvents.add(initEvent);

                    ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(19);
                    arguments.add(paramsBuffer);
                    arguments.add(preliminaryPositionsBuffer);
                    arguments.addAll(graphBuffers.arguments());
                    arguments.add(scratchBuffer);
                    arguments.add(preliminarySurfaceBuffer);
                    OpenCLBridge.CommandEvent preliminaryEvent = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_PRELIMINARY_KERNEL, arguments, preliminaryWorkItems);
                    events.add(preliminaryEvent);
                    preliminaryEvents.add(preliminaryEvent);
                }
                if (aquiferWorkItems > 0) {
                    ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(21);
                    arguments.add(paramsBuffer);
                    arguments.add(chunksBuffer);
                    arguments.add(aquiferPositionsBuffer);
                    arguments.add(pointPreliminaryIndicesBuffer);
                    arguments.add(preliminarySurfaceBuffer);
                    arguments.addAll(graphBuffers.arguments());
                    arguments.add(graphBuffers.interpolatedNodes());
                    arguments.add(latticeBuffer);
                    arguments.add(aquiferStatusBuffer);
                    arguments.add(scratchBuffer);
                    OpenCLBridge.CommandEvent event = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_AQUIFER_KERNEL, arguments, aquiferWorkItems);
                    events.add(event);
                    aquiferEvents.add(event);
                }

                if (parallelHeight) {
                    ArrayList<OpenCLBridge.DeviceBuffer> initArguments = new ArrayList<>(4);
                    initArguments.add(paramsBuffer);
                    initArguments.add(worldSurfaceBuffer);
                    initArguments.add(oceanFloorBuffer);
                    initArguments.add(motionBlockingBuffer);
                    OpenCLBridge.CommandEvent initEvent = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_HEIGHT_INIT_KERNEL, initArguments, columnWorkItems);
                    events.add(initEvent);
                    heightEvents.add(initEvent);

                    ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(27);
                    arguments.add(paramsBuffer);
                    arguments.add(chunksBuffer);
                    arguments.add(columnsBuffer);
                    arguments.add(aquiferPositionsBuffer);
                    arguments.add(aquiferPointIndicesBuffer);
                    arguments.addAll(graphBuffers.arguments());
                    arguments.add(latticeBuffer);
                    arguments.add(aquiferStatusBuffer);
                    arguments.add(scratchBuffer);
                    arguments.add(worldSurfaceBuffer);
                    arguments.add(oceanFloorBuffer);
                    arguments.add(motionBlockingBuffer);
                    OpenCLBridge.CommandEvent parallelEvent = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_HEIGHT_PARALLEL_KERNEL,
                            arguments,
                            parallelHeightWorkItems);
                    events.add(parallelEvent);
                    heightEvents.add(parallelEvent);
                } else {
                    ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>(27);
                    arguments.add(paramsBuffer);
                    arguments.add(chunksBuffer);
                    arguments.add(columnsBuffer);
                    arguments.add(aquiferPositionsBuffer);
                    arguments.add(aquiferPointIndicesBuffer);
                    arguments.addAll(graphBuffers.arguments());
                    arguments.add(graphBuffers.interpolatedNodes());
                    arguments.add(latticeBuffer);
                    arguments.add(aquiferStatusBuffer);
                    arguments.add(scratchBuffer);
                    arguments.add(worldSurfaceBuffer);
                    arguments.add(oceanFloorBuffer);
                    arguments.add(motionBlockingBuffer);
                    OpenCLBridge.CommandEvent event = runtime.enqueue(
                            OpenCLRuntime.ACCURATE_HEIGHT_KERNEL, arguments, columnWorkItems);
                    events.add(event);
                    heightEvents.add(event);
                }

                int[] worldSurface = runtime.readInts(worldSurfaceBuffer, columnWorkItems);
                int[] oceanFloor = runtime.readInts(oceanFloorBuffer, columnWorkItems);
                int[] motionBlocking = runtime.readInts(motionBlockingBuffer, columnWorkItems);
                long latticeKernelNanos = awaitEvents(latticeEvents);
                long preliminaryKernelNanos = awaitEvents(preliminaryEvents);
                long aquiferKernelNanos = awaitEvents(aquiferEvents);
                long heightKernelNanos = awaitEvents(heightEvents);
                long kernelNanos = latticeKernelNanos + preliminaryKernelNanos
                        + aquiferKernelNanos + heightKernelNanos;
                AccurateSamplingStats.recordGpuKernelStages(
                        latticeKernelNanos, preliminaryKernelNanos, aquiferKernelNanos, heightKernelNanos);
                return new HeightColumns(worldSurface, oceanFloor, motionBlocking, kernelNanos);
        } finally {
            events.forEach(OpenCLBridge.CommandEvent::close);
        }
    }

    private static long awaitEvents(List<OpenCLBridge.CommandEvent> events) {
        long nanos = 0L;
        for (OpenCLBridge.CommandEvent event : events) {
            nanos += event.awaitNanos();
        }
        return nanos;
    }

    private static void reportProgress(AccurateSamplingProgress progress, AccurateSamplingProgress.Batch batch) {
        try {
            progress.onBatch(batch);
        } catch (RuntimeException ignored) {}
    }

    private int latticePoints() {
        int cellCountXZ = 16 / noiseSettings.getCellWidth();
        int cellCountY = Math.floorDiv(noiseSettings.height(), noiseSettings.getCellHeight());
        return OpenCLAccurateLayout.latticePointCount(cellCountXZ, cellCountY);
    }

    private static boolean shouldParallelizeHeight(OpenCLRuntime runtime,
                                                   int parallelWorkItems,
                                                   int latticeWorkItems,
                                                   int nodeCount,
                                                   int interpolatorCount,
                                                   int latticePointsPerChunk,
                                                   int chunkCount) {
        long parallelScratchBytes = Math.multiplyExact(
                Math.multiplyExact((long) parallelWorkItems, (long) Math.max(1, nodeCount)),
                Double.BYTES);
        long latticeBytes = Math.multiplyExact(
                Math.multiplyExact((long) chunkCount, (long) latticePointsPerChunk),
                Math.multiplyExact((long) Math.max(1, interpolatorCount), Double.BYTES));
        OpenCLBridge.DeviceInfo device = runtime.deviceInfo();
        long maxAllocation = device.maxAllocationBytes() > 0L
                ? device.maxAllocationBytes()
                : 128L * 1024L * 1024L;
        long globalBudget = device.globalMemoryBytes() > 0L
                ? device.globalMemoryBytes() / 3L
                : 512L * 1024L * 1024L;
        long usefulParallelismLimit = Math.max(262_144L, Math.multiplyExact((long) latticeWorkItems, 2L));
        return parallelWorkItems <= usefulParallelismLimit
                && parallelScratchBytes <= maxAllocation
                && parallelScratchBytes + latticeBytes <= globalBudget;
    }

    private int maxChunks(OpenCLRuntime runtime, int latticePoints) {
        int minGridY = Math.floorDiv(noiseSettings.minY(), 12) - 1;
        int gridYSize = Math.floorDiv(noiseSettings.minY() + noiseSettings.height(), 12) + 1 - minGridY + 1;
        int aquiferPoints = aquifersEnabled ? Math.multiplyExact(9, gridYSize) : 0;
        return OpenCLAccurateBatchPlanner.maxChunks(
                runtime.deviceInfo(),
                graph.nodes().size(),
                graph.interpolatedNodes().size(),
                latticePoints,
                aquiferPoints);
    }

    private int[] params(int chunkCount,
                         int latticePointsPerChunk,
                         AquiferPositionPlanner.Plan aquiferPlan) {
        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int cellCountXZ = 16 / cellWidth;
        int cellCountY = Math.floorDiv(noiseSettings.height(), cellHeight);
        return new int[]{
                chunkCount,
                noiseSettings.minY(),
                noiseSettings.height(),
                cellWidth,
                cellHeight,
                cellCountXZ,
                cellCountY,
                Math.floorDiv(noiseSettings.minY(), cellHeight),
                graph.nodes().size(),
                graph.interpolatedNodes().size(),
                latticePointsPerChunk,
                aquiferPlan.gridYSize(),
                aquiferPlan.pointsPerChunk(),
                aquiferPlan.minGridY(),
                aquifersEnabled ? 1 : 0,
                seaLevel,
                -54,
                Math.min(-54, seaLevel),
                DimensionType.WAY_BELOW_MIN_Y,
                defaultFluidKind,
                defaultBlockMask,
                defaultFluidMask,
                lavaMask,
                graph.root(DensityGraphRoot.FINAL_DENSITY),
                graph.root(DensityGraphRoot.INITIAL_DENSITY_WITHOUT_JAGGEDNESS),
                graph.root(DensityGraphRoot.BARRIER),
                graph.root(DensityGraphRoot.FLUID_FLOODEDNESS),
                graph.root(DensityGraphRoot.FLUID_SPREAD),
                graph.root(DensityGraphRoot.LAVA),
                graph.root(DensityGraphRoot.EROSION),
                graph.root(DensityGraphRoot.DEPTH),
                aquiferPlan.uniquePointCount(),
                aquiferPlan.preliminaryPointCount(),
                0
        };
    }

    private synchronized OpenCLDensityProgramBuffers buffers(OpenCLRuntime runtime) {
        if (closed) {
            throw new IllegalStateException("accurate OpenCL program is closed");
        }
        return buffers.computeIfAbsent(runtime, key -> OpenCLDensityProgramBuffers.upload(key, payload));
    }

    private synchronized OpenCLAccurateWorkspace workspace(OpenCLRuntime runtime) {
        if (closed) {
            throw new IllegalStateException("accurate OpenCL program is closed");
        }
        return workspaces.computeIfAbsent(runtime, ignored -> new OpenCLAccurateWorkspace());
    }

    private static AquiferPositionPlanner.Plan emptyAquiferPlan(List<Long> chunkKeys) {
        int[] coordinates = new int[Math.multiplyExact(chunkKeys.size(), 2)];
        for (int i = 0; i < chunkKeys.size(); i++) {
            coordinates[i * 2] = ChunkPos.getX(chunkKeys.get(i));
            coordinates[i * 2 + 1] = ChunkPos.getZ(chunkKeys.get(i));
        }
        return new AquiferPositionPlanner.Plan(
                coordinates, new int[]{0}, new int[]{0}, new int[]{0}, new int[]{0},
                0, 0, 0, 0, 0);
    }

    private static Map<Long, AccurateHeightChunk> unpack(List<Long> keys,
                                                         int[] worldSurface,
                                                         int[] oceanFloor,
                                                         int[] motionBlocking) {
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>(keys.size());
        for (int index = 0; index < keys.size(); index++) {
            int from = index * AccurateHeightChunk.COLUMN_COUNT;
            int to = from + AccurateHeightChunk.COLUMN_COUNT;
            long key = keys.get(index);
            result.put(key, new AccurateHeightChunk(
                    ChunkPos.getX(key),
                    ChunkPos.getZ(key),
                    Arrays.copyOfRange(worldSurface, from, to),
                    Arrays.copyOfRange(oceanFloor, from, to),
                    Arrays.copyOfRange(motionBlocking, from, to)));
        }
        return result;
    }

    private static LinkedHashMap<Long, ColumnRequest> uniqueColumns(Collection<BlockPos> positions) {
        LinkedHashMap<Long, ColumnRequest> result = new LinkedHashMap<>();
        if (positions == null) {
            return result;
        }
        for (BlockPos position : positions) {
            if (position == null) {
                continue;
            }
            long key = AccurateHeightSample.key(position.getX(), position.getZ());
            result.putIfAbsent(key, new ColumnRequest(key, position.getX(), position.getZ()));
        }
        return result;
    }

    private static LinkedHashMap<Long, List<ColumnRequest>> groupColumnsByChunk(
            Collection<ColumnRequest> columns) {
        LinkedHashMap<Long, List<ColumnRequest>> grouped = new LinkedHashMap<>();
        for (ColumnRequest column : columns) {
            grouped.computeIfAbsent(column.chunkKey(), ignored -> new ArrayList<>()).add(column);
        }
        return grouped;
    }

    private static int[] fullColumnReferences(int chunkCount) {
        int[] result = new int[Math.multiplyExact(Math.multiplyExact(chunkCount, AccurateHeightChunk.COLUMN_COUNT), 3)];
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            for (int column = 0; column < AccurateHeightChunk.COLUMN_COUNT; column++) {
                int offset = (chunk * AccurateHeightChunk.COLUMN_COUNT + column) * 3;
                result[offset] = chunk;
                result[offset + 1] = column & 15;
                result[offset + 2] = column >> 4;
            }
        }
        return result;
    }

    private static int[] columnReferences(List<ColumnRequest> columns,
                                          Map<Long, Integer> chunkIndexes) {
        int[] result = new int[Math.multiplyExact(columns.size(), 3)];
        for (int index = 0; index < columns.size(); index++) {
            ColumnRequest column = columns.get(index);
            Integer chunkIndex = chunkIndexes.get(column.chunkKey());
            if (chunkIndex == null) {
                throw new IllegalArgumentException("Column chunk is missing from batch");
            }
            int offset = index * 3;
            result[offset] = chunkIndex;
            result[offset + 1] = column.blockX() & 15;
            result[offset + 2] = column.blockZ() & 15;
        }
        return result;
    }

    @Override
    public void close() {
        Map<OpenCLRuntime, OpenCLDensityProgramBuffers> closing;
        Map<OpenCLRuntime, OpenCLAccurateWorkspace> closingWorkspaces;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closing = new IdentityHashMap<>(buffers);
            buffers.clear();
            closingWorkspaces = new IdentityHashMap<>(workspaces);
            workspaces.clear();
        }
        for (Map.Entry<OpenCLRuntime, OpenCLDensityProgramBuffers> entry : closing.entrySet()) {
            synchronized (entry.getKey().operationLock()) {
                entry.getValue().close();
            }
        }
        for (Map.Entry<OpenCLRuntime, OpenCLAccurateWorkspace> entry : closingWorkspaces.entrySet()) {
            synchronized (entry.getKey().operationLock()) {
                entry.getValue().close();
            }
        }
    }

    record CompileResult(OpenCLAccurateProgram program, String unsupportedReason) {
        static CompileResult supported(OpenCLAccurateProgram program) {
            return new CompileResult(program, null);
        }

        static CompileResult unsupported(String reason) {
            return new CompileResult(null, reason == null || reason.isBlank() ? "unknown" : reason);
        }

        boolean supported() {
            return program != null && unsupportedReason == null;
        }
    }

    private record BatchResult(Map<Long, AccurateHeightChunk> chunks, long kernelNanos) {}

    private record HeightColumns(int[] worldSurface,
                                 int[] oceanFloor,
                                 int[] motionBlocking,
                                 long kernelNanos) {}

    private record ColumnRequest(long key, int blockX, int blockZ) {
        private long chunkKey() {
            return ChunkPos.asLong(blockX >> 4, blockZ >> 4);
        }
    }

    private static final class GridIndexList {
        private int[] values = new int[4];
        private int size;

        private void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        private int get(int index) {
            return values[index];
        }

        private int size() {
            return size;
        }
    }
}
