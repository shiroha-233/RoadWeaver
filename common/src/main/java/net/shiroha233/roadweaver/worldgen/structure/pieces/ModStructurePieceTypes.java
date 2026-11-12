package net.shiroha233.roadweaver.worldgen.structure.pieces;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 模组结构片段类型注册
 */
public final class ModStructurePieceTypes {
    private ModStructurePieceTypes() {}
    
    public static StructurePieceType SIMPLE_TEMPLATE;
    
    public static void setSimpleTemplate(StructurePieceType type) {
        SIMPLE_TEMPLATE = type;
    }
}
