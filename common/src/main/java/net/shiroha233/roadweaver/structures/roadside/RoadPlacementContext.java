package net.shiroha233.roadweaver.structures.roadside;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路放置上下文
 * 
 * 跟踪单条道路上已放置的结构信息，用于：
 * - 限制每条道路的最大结构数
 * - 检查结构之间的最小间距
 * - 记录已放置结构的位置
 * 
 * 每条道路生成时创建一个新的上下文实例。
 */
public final class RoadPlacementContext {
    
    /** 已放置的结构位置列表 */
    private final List<BlockPos> placedPositions = new ArrayList<>();
    
    /** 道路总长度（路段数） */
    private final int roadLength;
    
    /**
     * 创建放置上下文
     * 
     * @param roadLength 道路总长度（路段数）
     */
    public RoadPlacementContext(int roadLength) {
        this.roadLength = Math.max(0, roadLength);
    }
    
    /**
     * 获取道路长度
     */
    public int roadLength() {
        return roadLength;
    }
    
    /**
     * 获取已放置的结构数量
     */
    public int placedCount() {
        return placedPositions.size();
    }
    
    /**
     * 检查是否已达到最大结构数限制
     * 
     * @param maxStructures 最大结构数限制
     * @return 如果已达到限制返回 true
     */
    public boolean isMaxReached(int maxStructures) {
        return placedPositions.size() >= maxStructures;
    }
    
    /**
     * 检查指定位置与已放置结构的最小间距
     * 
     * @param pos         待检查的位置
     * @param minSpacing  最小间距（方块）
     * @return 如果与所有已放置结构的距离都大于最小间距返回 true
     */
    public boolean checkSpacing(BlockPos pos, int minSpacing) {
        if (minSpacing <= 0) {
            return true;
        }
        
        double minSpacingSq = (double) minSpacing * minSpacing;
        
        for (BlockPos placed : placedPositions) {
            // 使用水平距离（忽略 Y 轴）
            double dx = pos.getX() - placed.getX();
            double dz = pos.getZ() - placed.getZ();
            double distSq = dx * dx + dz * dz;
            
            if (distSq < minSpacingSq) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 记录已放置的结构位置
     * 
     * @param pos 放置位置
     */
    public void recordPlacement(BlockPos pos) {
        placedPositions.add(pos.immutable());
    }
    
    /**
     * 获取已放置的结构位置列表（不可修改）
     */
    public List<BlockPos> getPlacedPositions() {
        return List.copyOf(placedPositions);
    }
    
    /**
     * 重置上下文（清除所有记录）
     */
    public void reset() {
        placedPositions.clear();
    }
}
