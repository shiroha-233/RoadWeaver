package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.BiomeWoodAware;
import net.countered.settlementroads.helpers.Records;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.StructureWorldAccess;

/**
 * 路边间断栏杆装饰
 * 在道路两侧生成短段栏杆，增加道路美观性
 */
public class RoadFenceDecoration extends OrientedDecoration implements BiomeWoodAware {
    private final boolean leftRoadSide;
    private final int fenceLength;
    private Records.WoodAssets wood;

    /**
     * @param pos 放置位置
     * @param direction 道路方向向量
     * @param world 世界访问器
     * @param leftRoadSide 是否在道路左侧
     * @param fenceLength 栏杆长度（1-3个方块）
     */
    public RoadFenceDecoration(BlockPos pos, Vec3i direction, StructureWorldAccess world, boolean leftRoadSide, int fenceLength) {
        super(pos, direction, world);
        this.leftRoadSide = leftRoadSide;
        this.fenceLength = Math.min(3, Math.max(1, fenceLength)); // 限制长度在1-3之间
    }

    @Override
    public void place() {
        if (!placeAllowed()) return;

        BlockPos basePos = this.getPos();
        StructureWorldAccess world = this.getWorld();
        Vec3i roadDirection = getOrthogonalVector();

        // 计算栏杆延伸方向（沿着道路方向）
        Vec3i fenceDirection = new Vec3i(roadDirection.getZ(), 0, -roadDirection.getX());
        
        // 放置栏杆段
        for (int i = 0; i < fenceLength; i++) {
            BlockPos fencePos = basePos.add(fenceDirection.multiply(i));
            
            // 检查该位置是否适合放置
            BlockPos surfacePos = fencePos.withY(world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, fencePos.getX(), fencePos.getZ()));
            
            // 高度差检查：如果与基础位置高度差超过1格，跳过
            if (Math.abs(surfacePos.getY() - basePos.getY()) > 1) {
                continue;
            }
            
            // 放置栏杆
            world.setBlockState(surfacePos, wood.fence().getDefaultState(), 3);
        }
    }

    @Override
    public void setWoodType(Records.WoodAssets assets) {
        this.wood = assets;
    }
}
