package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public abstract class Decoration {
    private BlockPos placePos;
    private final WorldGenLevel world;

    public Decoration(BlockPos placePos, WorldGenLevel world) {
        this.placePos = placePos;
        this.world = world;
    }

    public abstract void place();

    protected final boolean placeAllowed() {
        BlockPos placePos = getPos();
        BlockPos surfacePos = new BlockPos(placePos.getX(), getSurfaceY(placePos.getX(), placePos.getZ()), placePos.getZ());
        this.placePos = surfacePos;
        BlockState blockStateBelow = world.getBlockState(surfacePos.below());

        boolean belowInvalid = blockStateBelow.is(Blocks.WATER)
                || blockStateBelow.is(Blocks.LAVA)
                || blockStateBelow.is(net.minecraft.tags.BlockTags.LOGS)
                || RoadFeature.DONT_PLACE_HERE.contains(blockStateBelow.getBlock());

        if (belowInvalid) {
            return false;
        }
        return true;
    }

    public BlockPos getPos() {
        return placePos;
    }

    public WorldGenLevel getWorld() {
        return world;
    }

    protected int getSurfaceY(int x, int z) {
        return world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    protected void setBlock(BlockPos pos, BlockState state) {
        world.setBlock(pos, state, 3);
    }
}
