package net.shiroha233.roadweaver.features.decoration;

import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.features.decoration.util.BiomeWoodAware;
import net.shiroha233.roadweaver.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.shiroha233.roadweaver.registry.ModBlocks;

public class LamppostDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean leftRoadSide;
    private Records.WoodAssets wood;

    public LamppostDecoration(BlockPos pos, Vec3i direction, WorldGenLevel world, boolean leftRoadSide) {
        super(pos, direction, world);
        this.leftRoadSide = leftRoadSide;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        BlockPos basePos = this.getPos();
        WorldGenLevel world = this.getWorld();

        buildNewLamppost(basePos, world);
    }

    private void buildNewLamppost(BlockPos basePos, WorldGenLevel world) {
        world.setBlock(basePos, Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState(), 3);
        world.setBlock(basePos.above(1), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
        world.setBlock(basePos.above(2), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
        world.setBlock(basePos.above(3), Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState(), 3);

        BlockPos lampPos = basePos.above(4);
        world.setBlock(lampPos, ModBlocks.REDSTONE_LAMP_OF_THE_ROAD.get().defaultBlockState(), 3);

        world.setBlock(basePos.above(5), Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
                .setValue(BlockStateProperties.INVERTED, true), 3);

        placeTrapdoorsAroundLamp(lampPos, world);
    }

    private void placeTrapdoorsAroundLamp(BlockPos lampPos, WorldGenLevel world) {
        world.setBlock(lampPos.east(),
                Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HALF, Half.TOP), 3);
        world.setBlock(lampPos.west(),
                Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.WEST)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HALF, Half.TOP), 3);
        world.setBlock(lampPos.south(),
                Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HALF, Half.TOP), 3);
        world.setBlock(lampPos.north(),
                Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(BlockStateProperties.OPEN, false)
                        .setValue(BlockStateProperties.HALF, Half.TOP), 3);
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
