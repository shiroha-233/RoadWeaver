package net.shiroha233.roadweaver.features.roadlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.decoration.system.SurfacePlacementUtil;
import net.shiroha233.roadweaver.features.roadlogic.surface.RoadHeightInterpolator;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

/**
 * 路面铺设器（重构版）
 * 核心改进：每个方块根据其在中心线上的投影位置独立计算高度，消除斜向爬坡锯齿问题。
 */
public final class SegmentPaver {
    private SegmentPaver() {}

    /**
     * 铺设单个路段（新版：每方块独立高度）
     * 
     * @param world 世界
     * @param seg 路段数据（中心点+覆盖方块列表）
     * @param segmentIndex 当前路段在中心线中的索引
     * @param centers 完整的道路中心点列表
     * @param targetY 每个中心点的目标高度数组
     * @param roadType 道路类型（0=人工，1=自然）
     * @param materials 路面材质列表
     * @param slabMaterials 半砖材质列表（可为空）
     * @param random 随机源
     * @param cfg 模组配置
     */
    public static void paveSegment(WorldGenLevel world,
                                   Records.RoadSegmentPlacement seg,
                                   int segmentIndex,
                                   List<BlockPos> centers,
                                   int[] targetY,
                                   int roadType,
                                   List<BlockState> materials,
                                   List<BlockState> slabMaterials,
                                   RandomSource random,
                                   ModConfig cfg) {
        List<BlockPos> positions = seg.positions();
        if (positions.isEmpty()) return;
        
        // 批量计算每个方块的插值高度
        int[] heights = RoadHeightInterpolator.batchInterpolate(positions, segmentIndex, centers, targetY);
        
        for (int i = 0; i < positions.size(); i++) {
            BlockPos widthBlock = positions.get(i);
            int y = heights[i];
            BlockPos pos = new BlockPos(widthBlock.getX(), y, widthBlock.getZ());
            
            // 结构避让：跳过位于结构边界框内的方块
            if (StructureAvoidanceService.shouldAvoid(world, pos)) {
                continue;
            }
            
            // 选择材质
            List<BlockState> baseMats;
            if (roadType == 1) {
                baseMats = net.shiroha233.roadweaver.features.decoration.material.surface.BiomeRoadMaterialSelector.forBiome(world, pos);
            } else {
                baseMats = materials;
            }
            
            // 放置路面方块
            SurfacePlacementUtil.placeOnSurface(world, pos, baseMats, 0, random, cfg);
            
            // 人工道路且配置了半砖：检查是否需要平滑过渡
            if (roadType == 0 && slabMaterials != null && !slabMaterials.isEmpty()) {
                if (shouldPlaceSlab(widthBlock.getX(), widthBlock.getZ(), y, centers, targetY)) {
                    BlockState slabState = slabMaterials.get(random.nextInt(slabMaterials.size()));
                    if (slabState.getBlock() instanceof SlabBlock) {
                        slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    }
                    world.setBlock(pos, slabState, 3);
                }
            }
        }
    }

    /**
     * 判断是否需要放置半砖进行平滑过渡
     * 原理：检查当前位置的前后方向是否有高度变化
     */
    private static boolean shouldPlaceSlab(int x, int z, int currentY, 
                                           List<BlockPos> centers, int[] targetY) {
        // 沿道路方向采样前后各1格的高度
        int yAhead = RoadHeightInterpolator.getInterpolatedY(x + 1, z, centers, targetY);
        int yBehind = RoadHeightInterpolator.getInterpolatedY(x - 1, z, centers, targetY);
        int yLeft = RoadHeightInterpolator.getInterpolatedY(x, z + 1, centers, targetY);
        int yRight = RoadHeightInterpolator.getInterpolatedY(x, z - 1, centers, targetY);
        
        // 如果当前位置比某个相邻位置低，且那个方向是"上坡"，则放置半砖缓冲
        boolean needsSlabX = (yAhead > currentY && yBehind >= currentY) 
                          || (yBehind > currentY && yAhead >= currentY);
        boolean needsSlabZ = (yLeft > currentY && yRight >= currentY) 
                          || (yRight > currentY && yLeft >= currentY);
        
        return needsSlabX || needsSlabZ;
    }
}
