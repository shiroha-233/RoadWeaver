package net.shiroha233.roadweaver.worldgen.road.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.OrientedDecoration;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.BiomeWoodAware;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.WoodTrapdoorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路灯装饰
 */
public class LamppostDecoration extends OrientedDecoration implements BiomeWoodAware {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final WoodAssets DEFAULT_WOOD = new WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN,
            Blocks.OAK_PLANKS);

    private final BlockPos originPos;
    private WoodAssets wood;

    public LamppostDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world) {
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
            BlockPos basePos = this.getPos();
            placeLampStructure(basePos, world);
        } catch (Throwable t) {
            LOGGER.warn("Lamppost placement failed at {}", this.getPos(), t);
            placeFallbackLantern(world, fallbackPos);
        }
    }

    private void placeLampStructure(BlockPos pos, WorldGenLevel world) {
        world.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        world.setBlock(pos.above(1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        world.setBlock(pos.above(2), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(3), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(4), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);

        BlockPos lampPos = pos.above(5);
        world.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);

        world.setBlock(lampPos.above(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
                .setValue(BlockStateProperties.INVERTED, true), 3);

        Block trap = WoodTrapdoorMapper.trapdoorForWood(wood);
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos tpos = lampPos.relative(dir);
            BlockState st = trap.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, dir)
                    .setValue(BlockStateProperties.OPEN, false)
                    .setValue(BlockStateProperties.HALF, Half.TOP);
            if (st.hasProperty(BlockStateProperties.WATERLOGGED)) {
                st = st.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            world.setBlock(tpos, st, 3);
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
