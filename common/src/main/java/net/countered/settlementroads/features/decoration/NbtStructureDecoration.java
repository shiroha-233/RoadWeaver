package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;

/**
 * 通用NBT结构装饰
 * 支持加载和放置任意NBT结构文件
 */
public class NbtStructureDecoration extends StructureDecoration implements BiomeWoodAware {
    private Records.WoodAssets wood;
    
    public NbtStructureDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world, 
                                  String structureName, Vec3i structureSize) {
        super(pos, direction, world, structureName, structureSize);
    }
    
    @Override
    protected void placeFallbackStructure() {
        // 不生成备用结构，只使用NBT文件
        // 如果NBT文件加载失败，则不生成任何内容
    }
    
    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
