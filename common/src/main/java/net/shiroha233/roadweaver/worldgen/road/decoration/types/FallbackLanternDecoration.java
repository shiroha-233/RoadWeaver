package net.shiroha233.roadweaver.worldgen.road.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.Decoration;

/**
 * 路灯失败时的强制灯笼回退装饰
 */
public class FallbackLanternDecoration extends Decoration {
    private final BlockPos originPos;

    public FallbackLanternDecoration(BlockPos placePos, WorldGenLevel world) {
        super(placePos, world);
        this.originPos = placePos;
    }

    @Override
    public void place() {
        WorldGenLevel world = getWorld();
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originPos.getX(), originPos.getZ());
        BlockPos lanternPos = new BlockPos(originPos.getX(), y, originPos.getZ());
        BlockPos supportPos = lanternPos.below();
        BlockState support = world.getBlockState(supportPos);
        if (!support.isFaceSturdy(world, supportPos, Direction.UP)) {
            world.setBlock(supportPos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        world.setBlock(lanternPos, Blocks.LANTERN.defaultBlockState(), 3);
    }
}
