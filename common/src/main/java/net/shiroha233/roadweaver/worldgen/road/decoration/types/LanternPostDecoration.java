package net.shiroha233.roadweaver.worldgen.road.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.OrientedDecoration;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.BiomeWoodAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 灯笼柱装饰
 */
public class LanternPostDecoration extends OrientedDecoration implements BiomeWoodAware {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final WoodAssets DEFAULT_WOOD = new WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN,
            Blocks.OAK_PLANKS);

    private final BlockPos originPos;
    private WoodAssets wood;

    public LanternPostDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world) {
        super(pos, direction, world);
        this.originPos = pos;
    }

    @Override
    public void place() {
        WorldGenLevel world = this.getWorld();
        BlockPos fallbackPos = fallbackPos(world);
        try {
            if (wood == null) wood = DEFAULT_WOOD;
            if (!placeAllowed()) {
                placeFallbackLantern(world, fallbackPos);
                return;
            }
            BlockPos base = this.getPos();
            world.setBlock(base, wood.fence().defaultBlockState(), 3);
            world.setBlock(base.above(1), wood.fence().defaultBlockState(), 3);
            world.setBlock(base.above(2), wood.fence().defaultBlockState(), 3);
            world.setBlock(base.above(3), Blocks.LANTERN.defaultBlockState(), 3);
        } catch (Throwable t) {
            LOGGER.warn("Lantern post placement failed at {}", this.getPos(), t);
            placeFallbackLantern(world, fallbackPos);
        }
    }

    @Override
    public void setWoodType(WoodAssets assets) {
        this.wood = assets;
    }

    private BlockPos fallbackPos(WorldGenLevel world) {
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originPos.getX(), originPos.getZ());
        return new BlockPos(originPos.getX(), y, originPos.getZ());
    }

    private static void placeFallbackLantern(WorldGenLevel world, BlockPos lanternPos) {
        BlockPos supportPos = lanternPos.below();
        BlockState support = world.getBlockState(supportPos);
        if (!support.isFaceSturdy(world, supportPos, Direction.UP)) {
            world.setBlock(supportPos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        world.setBlock(lanternPos, Blocks.LANTERN.defaultBlockState(), 3);
    }
}
