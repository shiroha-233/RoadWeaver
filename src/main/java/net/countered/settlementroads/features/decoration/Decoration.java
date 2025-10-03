package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.RoadFeature;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

public abstract class Decoration {
    private BlockPos placePos;
    private final StructureWorldAccess world;

    public Decoration(BlockPos placePos, StructureWorldAccess world) {
        this.placePos = placePos;
        this.world = world;
    }

    public abstract void place();

    protected final boolean placeAllowed() {
        BlockPos placePos = getPos();
        BlockPos surfacePos = placePos.withY(world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, placePos.getX(), placePos.getZ()));
        this.placePos = surfacePos;
        BlockState blockStateBelow = world.getBlockState(surfacePos.down());

        boolean belowInvalid = blockStateBelow.isOf(Blocks.WATER)
                || blockStateBelow.isOf(Blocks.LAVA)
                || blockStateBelow.isIn(BlockTags.LOGS)
                || RoadFeature.dontPlaceHere.contains(blockStateBelow.getBlock());

        if (belowInvalid) {
            return false;
        }
        return true;
    }

    public BlockPos getPos() {
        return placePos;
    }

    public StructureWorldAccess getWorld() {
        return world;
    }
}
