package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

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

        // 新的路灯设计
        buildNewLamppost(basePos, world);
    }

    /**
     * 建造新的路灯设计
     * 从下到上：深板岩墙 → 云杉栏杆×2 → 深板岩墙 → 红石灯 → 阳光检测器
     * 红石灯四周围绕云杉活板门
     */
    private void buildNewLamppost(BlockPos basePos, WorldGenLevel world) {
        // Y=0: 底部深板岩墙
        this.setBlock(basePos, Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());

        // Y=1-2: 两个云杉木栏杆
        this.setBlock(basePos.above(1), Blocks.SPRUCE_FENCE.defaultBlockState());
        this.setBlock(basePos.above(2), Blocks.SPRUCE_FENCE.defaultBlockState());

        // Y=3: 上部深板岩墙
        this.setBlock(basePos.above(3), Blocks.COBBLED_DEEPSLATE_WALL.defaultBlockState());

        // Y=4: 红石灯
        BlockPos lampPos = basePos.above(4);
        this.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());

        // Y=5: 阳光检测器（反相模式，夜晚激活）
        this.setBlock(basePos.above(5), Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
            .setValue(BlockStateProperties.INVERTED, true));

        // 红石灯四周的云杉木活板门（Y=4位置）
        placeTrapdoorsAroundLamp(lampPos, world);
    }

    /**
     * 在红石灯四周放置云杉木活板门
     * 活板门水平放置在方块上半部分，关闭状态，围绕红石灯形成装饰
     */
    private void placeTrapdoorsAroundLamp(BlockPos lampPos, WorldGenLevel world) {
        // 东面活板门 - 水平放置在上半部分，朝向东
        this.setBlock(lampPos.east(),
            Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.HALF, Half.TOP));

        // 西面活板门 - 水平放置在上半部分，朝向西
        this.setBlock(lampPos.west(),
            Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.HALF, Half.TOP));

        // 南面活板门 - 水平放置在上半部分，朝向南
        this.setBlock(lampPos.south(),
            Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.HALF, Half.TOP));

        // 北面活板门 - 水平放置在上半部分，朝向北
        this.setBlock(lampPos.north(),
            Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.HALF, Half.TOP));
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
