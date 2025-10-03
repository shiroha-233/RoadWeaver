package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;

public class LamppostDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean leftRoadSide;
    private Records.WoodAssets wood;

    public LamppostDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world, boolean leftRoadSide) {
        super(pos, direction, world);
        this.leftRoadSide = leftRoadSide;
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        BlockPos basePos = this.getPos();
        StructureWorldAccess world = this.getWorld();

        // 新的路灯设计
        buildNewLamppost(basePos, world);
    }

    /**
     * 建造新的路灯设计
     * 从下到上：深板岩墙 → 云杉栏杆×2 → 深板岩墙 → 红石灯 → 阳光检测器
     * 红石灯四周围绕云杉活板门
     */
    private void buildNewLamppost(BlockPos basePos, StructureWorldAccess world) {
        // Y=0: 底部深板岩墙
        world.setBlockState(basePos, Blocks.COBBLED_DEEPSLATE_WALL.getDefaultState(), 3);
        
        // Y=1-2: 两个云杉木栏杆
        world.setBlockState(basePos.up(1), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
        world.setBlockState(basePos.up(2), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
        
        // Y=3: 上部深板岩墙
        world.setBlockState(basePos.up(3), Blocks.COBBLED_DEEPSLATE_WALL.getDefaultState(), 3);
        
        // Y=4: 红石灯
        BlockPos lampPos = basePos.up(4);
        world.setBlockState(lampPos, Blocks.REDSTONE_LAMP.getDefaultState(), 3);
        
        // Y=5: 阳光检测器（反相模式，夜晚激活）
        world.setBlockState(basePos.up(5), Blocks.DAYLIGHT_DETECTOR.getDefaultState()
            .with(Properties.INVERTED, true), 3);
        
        // 红石灯四周的云杉活板门（Y=4位置）
        placeTrapdoorsAroundLamp(lampPos, world);
    }
    
    /**
     * 在红石灯四周放置云杉木活板门
     * 活板门水平放置在方块上半部分，关闭状态，围绕红石灯形成装饰
     */
    private void placeTrapdoorsAroundLamp(BlockPos lampPos, StructureWorldAccess world) {
        // 东面活板门 - 水平放置在上半部分，朝向东
        world.setBlockState(lampPos.east(), 
            Blocks.SPRUCE_TRAPDOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.EAST)
                .with(Properties.OPEN, false)
                .with(net.minecraft.state.property.Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP), 3);
                
        // 西面活板门 - 水平放置在上半部分，朝向西
        world.setBlockState(lampPos.west(), 
            Blocks.SPRUCE_TRAPDOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.WEST)
                .with(Properties.OPEN, false)
                .with(net.minecraft.state.property.Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP), 3);
                
        // 南面活板门 - 水平放置在上半部分，朝向南
        world.setBlockState(lampPos.south(), 
            Blocks.SPRUCE_TRAPDOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.SOUTH)
                .with(Properties.OPEN, false)
                .with(net.minecraft.state.property.Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP), 3);
                
        // 北面活板门 - 水平放置在上半部分，朝向北
        world.setBlockState(lampPos.north(), 
            Blocks.SPRUCE_TRAPDOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.NORTH)
                .with(Properties.OPEN, false)
                .with(net.minecraft.state.property.Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP), 3);
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
