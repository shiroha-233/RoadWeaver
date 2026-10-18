package net.shiroha233.roadweaver.features.path.decoration.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.features.path.decoration.compat.RoadFeatureCompat;

/**
 * 装饰基类
 */
public abstract class Decoration {
    private static final int MAX_PLANT_DEPTH = 4;

    private BlockPos placePos;
    private final WorldGenLevel world;

    public Decoration(BlockPos placePos, WorldGenLevel world) {
        this.placePos = placePos;
        this.world = world;
    }

    public abstract void place();

    public BlockPos computeSurfacePos() {
        BlockPos pos = new BlockPos(placePos.getX(), world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, placePos.getX(), placePos.getZ()), placePos.getZ());
        // The worldgen heightmap counts grass, flowers and petals as surface; step down through them (but not into
        // water) so posts stand on the ground instead of a block above it.
        for (int d = 0; d < MAX_PLANT_DEPTH; d++) {
            BlockState below = world.getBlockState(pos.below());
            if (!below.getFluidState().isEmpty()) break;
            if (!below.isAir() && !below.canBeReplaced()) break;
            pos = pos.below();
        }
        return pos;
    }

    public boolean isPlaceAllowedAt(BlockPos surfacePos) {
        BlockState below = world.getBlockState(surfacePos.below());
        boolean belowInvalid = below.is(Blocks.WATER) || below.is(Blocks.LAVA) || below.is(BlockTags.LOGS) || RoadFeatureCompat.dontPlaceHere(below.getBlock());
        return !belowInvalid;
    }

    protected final boolean placeAllowed() {
        BlockPos surfacePos = computeSurfacePos();
        this.placePos = surfacePos;
        return isPlaceAllowedAt(surfacePos);
    }

    public BlockPos getPos() { return placePos; }

    public WorldGenLevel getWorld() { return world; }
}
