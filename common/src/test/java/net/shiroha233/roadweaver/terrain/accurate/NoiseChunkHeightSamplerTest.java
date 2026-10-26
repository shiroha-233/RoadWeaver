/* 文件职责：验证 CPU 稀疏精采列与完整 NoiseChunk 高度图严格一致。 */
package net.shiroha233.roadweaver.terrain.accurate;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoiseChunkHeightSamplerTest {
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void sparseColumnsMatchCompleteChunkAtNegativeCoordinates() {
        NoiseGeneratorSettings settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD)
                .value();
        RandomState randomState = RandomState.create(
                settings,
                registries.lookupOrThrow(Registries.NOISE),
                0x5EED_1234_ABCDL);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(
                settings.noiseSettings().minY(),
                settings.noiseSettings().height());
        NoiseChunkHeightSampler sampler = NoiseChunkHeightSampler.create(heightAccessor, settings, randomState);
        int[] requested = {0, 8, 8 << 4, 8 + (8 << 4), 255, 0};

        AccurateHeightChunk complete = sampler.sampleChunk(-3, 5);
        AccurateHeightChunk sparse = sampler.sampleChunkColumns(-3, 5, requested);

        for (int index : requested) {
            assertEquals(complete.worldSurfaceWgAt(index), sparse.worldSurfaceWgAt(index),
                    "WORLD_SURFACE_WG column " + index);
            assertEquals(complete.oceanFloorWgAt(index), sparse.oceanFloorWgAt(index),
                    "OCEAN_FLOOR_WG column " + index);
            assertEquals(complete.motionBlockingNoLeavesAt(index), sparse.motionBlockingNoLeavesAt(index),
                    "MOTION_BLOCKING_NO_LEAVES column " + index);
        }
    }
}
