/* 文件职责：在真实 FP64 GPU 上逐列验证原版与 OpenCL 精采高度图一致性。 */
package net.shiroha233.roadweaver.terrain.opencl;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightChunk;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightGrid;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightGridRequest;
import net.shiroha233.roadweaver.terrain.accurate.AccurateHeightSample;
import net.shiroha233.roadweaver.terrain.opencl.OpenCLBridge;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "ROADWEAVER_TEST_OPENCL_ACCURATE", matches = "true")
class OpenCLAccurateGpuIntegrationTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @AfterAll
    static void closeRuntime() {
        OpenCLRuntime.closeAll();
    }

    @Test
    void overworldChunkMatchesCpuNoiseChunkExactly() throws Exception {
        verifyChunks(overworldSettings(), List.of(
                ChunkPos.asLong(-3, 5),
                ChunkPos.asLong(0, 0),
                ChunkPos.asLong(12, -9),
                ChunkPos.asLong(-20, -20)));
    }

    @Test
    void overworldDensityAndInterpolationMatchWithoutAquifers() throws Exception {
        NoiseGeneratorSettings source = overworldSettings();
        NoiseGeneratorSettings withoutAquifers = new NoiseGeneratorSettings(
                source.noiseSettings(),
                source.defaultBlock(),
                source.defaultFluid(),
                source.noiseRouter(),
                source.surfaceRule(),
                source.spawnTarget(),
                source.seaLevel(),
                source.disableMobGeneration(),
                false,
                source.oreVeinsEnabled(),
                source.useLegacyRandomSource());
        verifyChunks(withoutAquifers, List.of(ChunkPos.asLong(-3, 5)));
    }

    @Test
    void adjacentOverworldSparseRegionMatchesCpuWithSharedAquiferPoints() throws Exception {
        NoiseGeneratorSettings settings = overworldSettings();
        RandomState randomState = RandomState.create(
                settings,
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                settings.noiseSettings().minY(),
                settings.noiseSettings().height());
        OpenCLAccurateProgram.CompileResult compiled = OpenCLAccurateProgram.compile(
                heightAccessor, settings, randomState);
        assertTrue(compiled.supported(), compiled.unsupportedReason());
        OpenCLRuntime runtime = OpenCLRuntime.tryCreateAccurateGpu();
        assertNotNull(runtime, "an FP64 OpenCL GPU is required for this opt-in integration test");

        AccurateHeightGridRequest request = new AccurateHeightGridRequest(0, 0, 48, 48, 8);

        try (OpenCLAccurateProgram program = compiled.program()) {
            AtomicLong completedColumns = new AtomicLong();
            AccurateHeightGrid gpu = program.sampleGrid(
                    runtime, request, batch -> completedColumns.set(batch.completedColumns()));
            List<Long> representativeChunks = List.of(
                    ChunkPos.asLong(0, 0),
                    ChunkPos.asLong(12, 12),
                    ChunkPos.asLong(23, 23));
            Map<Long, AccurateHeightChunk> cpu = sampleCpuChunks(
                    heightAccessor, settings, randomState, representativeChunks);
            assertEquals(request.sampleCount(), completedColumns.get());
            for (int index = 0; index < request.sampleCount(); index++) {
                int blockX = request.blockX(index);
                int blockZ = request.blockZ(index);
                AccurateHeightChunk expected = cpu.get(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
                if (expected == null) {
                    continue;
                }
                assertEquals(expected.worldSurfaceWg(blockX, blockZ), gpu.worldSurface()[index]);
                assertEquals(expected.oceanFloorWg(blockX, blockZ), gpu.oceanFloor()[index]);
                assertEquals(expected.motionBlockingNoLeaves(blockX, blockZ), gpu.motionBlocking()[index]);
            }
        }
    }

    @Test
    void netherChunksMatchCpuNoiseChunkExactly() throws Exception {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.NETHER)
                .value();
        verifyChunks(settings, List.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(-11, 7)));
    }

    @Test
    void endChunksMatchCpuNoiseChunkExactly() throws Exception {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.END)
                .value();
        verifyChunks(settings, List.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(40, -32)));
    }

    @Test
    void alternativeVanillaNoiseSettingsMatchCpuExactly() throws Exception {
        List<ResourceKey<NoiseGeneratorSettings>> settingsKeys = List.of(
                NoiseGeneratorSettings.LARGE_BIOMES,
                NoiseGeneratorSettings.AMPLIFIED,
                NoiseGeneratorSettings.CAVES,
                NoiseGeneratorSettings.FLOATING_ISLANDS);
        int index = 0;
        for (ResourceKey<NoiseGeneratorSettings> settingsKey : settingsKeys) {
            NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(settingsKey)
                    .value();
            verifyChunks(settings, List.of(ChunkPos.asLong(-7 - index * 3, 9 + index * 5)));
            index++;
        }
    }

    @Test
    void coarseInitialDensityKernelStillMatchesCpu() {
        NoiseGeneratorSettings settings = overworldSettings();
        RandomState randomState = RandomState.create(
                settings,
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL);
        DensityFunction root = randomState.router().initialDensityWithoutJaggedness();
        DensityGraphCompileResult compiled = DensityGraphCompiler.compile(root);
        assertTrue(compiled.supported(), compiled.unsupportedReason());
        DensityGraphProgram graph = compiled.program();
        OpenCLRuntime runtime = OpenCLRuntime.tryCreateAccurateGpu();
        assertNotNull(runtime);

        int width = 8;
        int count = width * width;
        int minX = -128;
        int minZ = 64;
        int step = 16;
        int minY = settings.noiseSettings().minY();
        int maxY = minY + settings.noiseSettings().height();
        int cellHeight = settings.noiseSettings().getCellHeight();
        int[] params = {
                count, minY, maxY, cellHeight, minX, minZ, step, width,
                graph.rootNode(), graph.nodes().size(),
                graph.noiseTables().normalNoises().size(),
                graph.noiseTables().perlinNoises().size(),
                graph.noiseTables().improvedNoises().size(),
                graph.splines().size()
        };
        OpenCLDensityProgramPayload payload = OpenCLDensityProgramPayload.from(graph);
        synchronized (runtime.operationLock()) {
            try (OpenCLDensityProgramBuffers graphBuffers = OpenCLDensityProgramBuffers.upload(runtime, payload);
                 OpenCLBridge.DeviceBuffer paramsBuffer = runtime.upload(params);
                 OpenCLBridge.DeviceBuffer scratch = runtime.allocate(
                         OpenCLBridge.BufferAccess.READ_WRITE, (long) count * graph.nodes().size() * Double.BYTES);
                 OpenCLBridge.DeviceBuffer output = runtime.allocate(
                         OpenCLBridge.BufferAccess.WRITE_ONLY, (long) count * Integer.BYTES)) {
                ArrayList<OpenCLBridge.DeviceBuffer> arguments = new ArrayList<>();
                arguments.add(paramsBuffer);
                arguments.addAll(graphBuffers.arguments());
                arguments.add(scratch);
                arguments.add(output);
                runtime.execute(OpenCLRuntime.COARSE_HEIGHT_KERNEL, arguments, count);
                int[] gpu = runtime.readInts(output, count);
                for (int index = 0; index < count; index++) {
                    int x = minX + (index % width) * step;
                    int z = minZ + (index / width) * step;
                    int expected = minY;
                    for (int y = maxY; y >= minY; y -= cellHeight) {
                        if (root.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.390625) {
                            expected = y;
                            break;
                        }
                    }
                    assertEquals(expected, gpu[index], "coarse initial density at [" + x + "," + z + "]");
                }
            }
        }
    }

    private static NoiseGeneratorSettings overworldSettings() {
        return registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                .value();
    }

    private static void verifyChunks(NoiseGeneratorSettings settings, List<Long> chunkKeys) throws Exception {
        RandomState randomState = RandomState.create(
                settings,
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                settings.noiseSettings().minY(),
                settings.noiseSettings().height());
        OpenCLAccurateProgram.CompileResult compiled = OpenCLAccurateProgram.compile(
                heightAccessor, settings, randomState);
        assertTrue(compiled.supported(), compiled.unsupportedReason());
        OpenCLRuntime runtime = OpenCLRuntime.tryCreateAccurateGpu();
        assertNotNull(runtime, "an FP64 OpenCL GPU is required for this opt-in integration test");

        try (OpenCLAccurateProgram program = compiled.program()) {
            Map<Long, AccurateHeightChunk> gpuChunks = program.sample(runtime, chunkKeys);
            Map<Long, AccurateHeightChunk> cpuChunks = sampleCpuChunks(
                    heightAccessor, settings, randomState, chunkKeys);
            for (long chunkKey : chunkKeys) {
                AccurateHeightChunk gpu = gpuChunks.get(chunkKey);
                AccurateHeightChunk cpu = cpuChunks.get(chunkKey);
                assertNotNull(gpu);
                assertNotNull(cpu);
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                for (int index = 0; index < AccurateHeightChunk.COLUMN_COUNT; index++) {
                    int x = (chunkX << 4) + (index & 15);
                    int z = (chunkZ << 4) + (index >> 4);
                    assertEquals(cpu.worldSurfaceWgAt(index), gpu.worldSurfaceWgAt(index),
                            "WORLD_SURFACE_WG at [" + x + "," + z + "]");
                    assertEquals(cpu.oceanFloorWgAt(index), gpu.oceanFloorWgAt(index),
                            "OCEAN_FLOOR_WG at [" + x + "," + z + "]");
                    assertEquals(cpu.motionBlockingNoLeavesAt(index), gpu.motionBlockingNoLeavesAt(index),
                            "MOTION_BLOCKING_NO_LEAVES at [" + x + "," + z + "]");
                }
            }

            List<BlockPos> sparsePositions = new ArrayList<>(chunkKeys.size() * 4);
            for (long chunkKey : chunkKeys) {
                int chunkX = ChunkPos.getX(chunkKey) << 4;
                int chunkZ = ChunkPos.getZ(chunkKey) << 4;
                sparsePositions.add(new BlockPos(chunkX + 1, 0, chunkZ + 2));
                sparsePositions.add(new BlockPos(chunkX + 7, 0, chunkZ + 11));
                sparsePositions.add(new BlockPos(chunkX + 12, 0, chunkZ + 4));
                sparsePositions.add(new BlockPos(chunkX + 15, 0, chunkZ + 15));
            }
            Map<Long, AccurateHeightSample> sparseGpu = program.samplePositions(runtime, sparsePositions);
            for (BlockPos position : sparsePositions) {
                long chunkKey = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
                AccurateHeightChunk cpu = cpuChunks.get(chunkKey);
                AccurateHeightSample gpu = sparseGpu.get(AccurateHeightSample.key(position.getX(), position.getZ()));
                assertNotNull(cpu);
                assertNotNull(gpu);
                assertEquals(cpu.worldSurfaceWg(position.getX(), position.getZ()), gpu.worldSurfaceWg(),
                        "sparse WORLD_SURFACE_WG at [" + position.getX() + "," + position.getZ() + "]");
                assertEquals(cpu.oceanFloorWg(position.getX(), position.getZ()), gpu.oceanFloorWg(),
                        "sparse OCEAN_FLOOR_WG at [" + position.getX() + "," + position.getZ() + "]");
                assertEquals(cpu.motionBlockingNoLeaves(position.getX(), position.getZ()), gpu.motionBlockingNoLeaves(),
                        "sparse MOTION_BLOCKING_NO_LEAVES at [" + position.getX() + "," + position.getZ() + "]");
            }
        }
    }

    private static Map<Long, AccurateHeightChunk> sampleCpuChunks(LevelHeightAccessor heightAccessor,
                                                                  NoiseGeneratorSettings settings,
                                                                  RandomState randomState,
                                                                  List<Long> chunkKeys) throws Exception {
        Class<?> samplerType = Class.forName(
                "net.shiroha233.roadweaver.terrain.accurate.NoiseChunkHeightSampler");
        Method create = samplerType.getDeclaredMethod(
                "create", LevelHeightAccessor.class, NoiseGeneratorSettings.class, RandomState.class);
        create.setAccessible(true);
        Object sampler = create.invoke(null, heightAccessor, settings, randomState);
        Method sampleChunk = samplerType.getDeclaredMethod("sampleChunk", int.class, int.class);
        sampleChunk.setAccessible(true);
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
        for (long chunkKey : chunkKeys) {
            result.put(chunkKey, (AccurateHeightChunk) sampleChunk.invoke(
                    sampler, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        }
        return result;
    }
}
