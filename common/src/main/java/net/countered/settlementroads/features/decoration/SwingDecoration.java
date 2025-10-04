package net.countered.settlementroads.features.decoration;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;

/**
 * 秋千装饰
 * 尺寸：宽4格，高10格
 * 优先使用NBT文件，失败时使用代码生成备用结构
 */
public class SwingDecoration extends StructureDecoration {
    
    public SwingDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world) {
        super(pos, direction, world, "swing", new Vec3i(4, 10, 4));
    }
    
    /**
     * 检查秋千特殊的放置条件
     */
    protected boolean checkSwingPlacement() {
        if (!super.placeAllowed()) return false;
        
        // 秋千需要更多的垂直空间检查
        BlockPos basePos = getPos();
        StructureWorldAccess world = getWorld();
        
        // 检查上方10格空间是否足够
        for (int y = 1; y <= 10; y++) {
            BlockPos checkPos = basePos.up(y);
            BlockState state = world.getBlockState(checkPos);
            // 如果上方有实心方块（不是空气、草、花等），不适合放置秋千
            if (!state.isAir() && 
                !state.getBlock().equals(Blocks.SHORT_GRASS) &&
                !state.getBlock().equals(Blocks.TALL_GRASS) &&
                !state.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) &&
                !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void place() {
        if (!checkSwingPlacement()) return;
        super.place();
    }
    
    @Override
    protected void placeFallbackStructure() {
        // 不生成备用结构，只使用NBT文件
        // 如果NBT文件加载失败，则不生成任何内容
    }
    
}
