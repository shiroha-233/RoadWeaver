package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

public class FenceWaypointDecoration extends Decoration implements BiomeWoodAware {
    private Records.WoodAssets wood;

    public FenceWaypointDecoration(BlockPos placePos, StructureWorldAccess world) {
        super(placePos, world);
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        BlockPos surfacePos = this.getPos();
        StructureWorldAccess world = this.getWorld();

        world.setBlockState(surfacePos, wood.fence().getDefaultState(), 3);
        world.setBlockState(surfacePos.up(), Blocks.TORCH.getDefaultState(), 3);
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
