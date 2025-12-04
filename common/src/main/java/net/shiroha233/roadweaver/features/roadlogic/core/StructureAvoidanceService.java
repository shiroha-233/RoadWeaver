package net.shiroha233.roadweaver.features.roadlogic.core;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.config.ConfigService;

import java.util.Map;

/**
 * 结构避让服务
 * 
 * 职责：在道路放置阶段检测方块位置是否在结构边界框内，
 * 如果是则跳过放置，避免道路破坏结构建筑。
 * 
 * 原理：使用 Minecraft 原生的 StructureManager API，
 * 遍历该位置所有结构引用，精确检查每个结构片段(StructurePiece)的边界框。
 */
public final class StructureAvoidanceService {
    private StructureAvoidanceService() {}
    
    // 海平面高度，只有 minY >= 此值的结构才会被避让
    private static final int SEA_LEVEL = 63;
    
    /**
     * 检测给定位置是否应该避让（在任意结构片段的边界框内）
     * 
     * @param world 世界生成级别
     * @param pos   要检测的位置
     * @return true 表示应该跳过此位置的道路放置
     */
    public static boolean shouldAvoid(WorldGenLevel world, BlockPos pos) {
        if (!ConfigService.get().structureAvoidanceEnabled()) {
            return false;
        }
        
        ServerLevel level = world.getLevel();
        if (level == null) return false;
        
        StructureManager sm = level.structureManager();
        
        // 快速检查：该位置是否有任何结构引用
        if (!sm.hasAnyStructureAt(pos)) {
            return false;
        }
        
        // 获取该位置所有结构的引用
        Map<Structure, LongSet> allStructures = sm.getAllStructuresAt(pos);
        if (allStructures.isEmpty()) {
            return false;
        }
        
        // 遍历每个结构类型
        for (Map.Entry<Structure, LongSet> entry : allStructures.entrySet()) {
            Structure structure = entry.getKey();
            LongSet chunkRefs = entry.getValue();
            
            // 遍历引用该结构的所有区块
            for (long chunkLong : chunkRefs) {
                ChunkPos chunkPos = new ChunkPos(chunkLong);
                SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
                
                // 获取该区块的结构起点
                StructureStart start = sm.getStartForStructure(
                        sectionPos, 
                        structure, 
                        level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS)
                );
                
                if (start == null || !start.isValid()) continue;
                
                // 检查每个结构片段的边界框
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox bb = piece.getBoundingBox();
                    
                    // 忽略地下结构（如地牢），只处理海平面以上的结构
                    if (bb.minY() < SEA_LEVEL) {
                        continue;
                    }
                    
                    // 只在XZ平面检查，因为道路是2D的
                    if (pos.getX() >= bb.minX() && pos.getX() <= bb.maxX()
                            && pos.getZ() >= bb.minZ() && pos.getZ() <= bb.maxZ()) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 批量检测：检测一组位置中是否有任何一个应该避让
     * 用于检测整个路段是否需要跳过
     */
    public static boolean shouldAvoidAny(WorldGenLevel world, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (shouldAvoid(world, pos)) {
                return true;
            }
        }
        return false;
    }
}
