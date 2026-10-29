package net.shiroha233.roadweaver.worldgen.road.decoration.types;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.OrientedDecoration;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.worldgen.road.decoration.material.BiomeWoodAware;
import net.shiroha233.roadweaver.worldgen.road.decoration.text.SignTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 海域问号路牌
 */
public class SeaQuestionSignDecoration extends OrientedDecoration implements BiomeWoodAware {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final WoodAssets DEFAULT_WOOD = new WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN,
            Blocks.OAK_PLANKS);
    private final boolean isStart;
    private WoodAssets wood;

    public SeaQuestionSignDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, boolean isStart) {
        super(pos, direction, world);
        this.isStart = isStart;
    }

    @Override
    public void place() {
        if (wood == null) wood = DEFAULT_WOOD;
        if (!placeAllowed()) return;

        int rotation = getCardinalRotationFromVector(getOrthogonalVector(), isStart);
        DirectionProperties props = getDirectionProperties(rotation);

        BlockPos basePos = this.getPos();
        WorldGenLevel world = this.getWorld();
        ensureFoundation(basePos, world);

        BlockPos signPos = basePos.above(2).relative(props.offsetDirection.getOpposite());
        int signRotation = (rotation + 8) % 16;
        world.setBlock(signPos,
                wood.hangingSign().defaultBlockState()
                        .setValue(BlockStateProperties.ROTATION_16, signRotation)
                        .setValue(BlockStateProperties.ATTACHED, true),
                3);
        placeFenceStructure(basePos, props);
        try {
            updateSigns(world, signPos);
        } catch (Throwable t) {
            LOGGER.warn("Sea question sign text write failed at {}", signPos, t);
        }
    }

    private void placeFenceStructure(BlockPos pos, DirectionProperties props) {
        WorldGenLevel world = this.getWorld();
        world.setBlock(pos.above(3).relative(props.offsetDirection.getOpposite()), wood.fence().defaultBlockState().setValue(props.directionProperty, true), 3);
        world.setBlock(pos.above(0), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(1), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(2), wood.fence().defaultBlockState(), 3);
        world.setBlock(pos.above(3), wood.fence().defaultBlockState().setValue(props.reverseDirectionProperty, true), 3);
    }

    private void updateSigns(WorldGenLevel level, BlockPos pos) {
        SignTextService.writeSeaQuestionSign(level, pos);
    }

    @Override
    public void setWoodType(WoodAssets assets) {
        this.wood = assets;
    }

    private void ensureFoundation(BlockPos basePos, WorldGenLevel world) {
        BlockPos belowPos = basePos.below();
        BlockState below = world.getBlockState(belowPos);
        if (!below.isFaceSturdy(world, belowPos, Direction.UP)
                || below.is(Blocks.WATER)
                || below.is(Blocks.LAVA)) {
            world.setBlock(belowPos, wood.planks().defaultBlockState(), 3);
        }
    }
}
