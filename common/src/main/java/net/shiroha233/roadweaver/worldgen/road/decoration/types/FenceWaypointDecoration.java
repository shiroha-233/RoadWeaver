package net.shiroha233.roadweaver.worldgen.road.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.Decoration;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.BiomeWoodAware;

/**
 * 围栏路标装饰
 */
public class FenceWaypointDecoration extends Decoration implements BiomeWoodAware {
    private WoodAssets wood;

    public FenceWaypointDecoration(BlockPos placePos, WorldGenLevel world) {
        super(placePos, world);
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;
        BlockPos surfacePos = this.getPos();
        WorldGenLevel world = this.getWorld();
        world.setBlock(surfacePos, wood.fence().defaultBlockState(), 3);
        world.setBlock(surfacePos.above(), Blocks.TORCH.defaultBlockState(), 3);
    }

    @Override
    public void setWoodType(WoodAssets assets) {
        this.wood = assets;
    }
}
