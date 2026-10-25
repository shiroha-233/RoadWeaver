package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 精确采样仅关心三类 Heightmap 谓词与 aquifer 流体身份。
 */
final class HeightmapMaterialClassifier {
    static final int WORLD_SURFACE = 1;
    static final int OCEAN_FLOOR = 1 << 1;
    static final int MOTION_BLOCKING = 1 << 2;
    static final int REQUIRED_SOLID_MASK = WORLD_SURFACE | OCEAN_FLOOR | MOTION_BLOCKING;

    private HeightmapMaterialClassifier() {}

    static int mask(BlockState state) {
        int mask = 0;
        if (Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)) {
            mask |= WORLD_SURFACE;
        }
        if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
            mask |= OCEAN_FLOOR;
        }
        if (Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque().test(state)) {
            mask |= MOTION_BLOCKING;
        }
        return mask;
    }

    static int fluidKind(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return 1;
        }
        if (state.is(Blocks.LAVA)) {
            return 2;
        }
        return state.isAir() ? 0 : 3;
    }

    static boolean oreVeinOutputsMatch(int expectedMask) {
        return mask(Blocks.COPPER_ORE.defaultBlockState()) == expectedMask
                && mask(Blocks.RAW_COPPER_BLOCK.defaultBlockState()) == expectedMask
                && mask(Blocks.GRANITE.defaultBlockState()) == expectedMask
                && mask(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState()) == expectedMask
                && mask(Blocks.RAW_IRON_BLOCK.defaultBlockState()) == expectedMask
                && mask(Blocks.TUFF.defaultBlockState()) == expectedMask;
    }
}
