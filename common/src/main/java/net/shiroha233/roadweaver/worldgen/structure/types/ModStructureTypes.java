package net.shiroha233.roadweaver.worldgen.structure.types;

import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * 模组结构类型注册中心
 */
public final class ModStructureTypes {
    private ModStructureTypes() {}
    
    public static StructureType<RoadsideStructure> ROADSIDE;
    public static StructureType<RoadsideVillageStructure> ROADSIDE_VILLAGE;
    public static StructureType<SpawnCabinStructure> SPAWN_CABIN;
    public static StructureType<BridgeTemplateStructure> BRIDGE_TEMPLATE;
    
    public static void setRoadside(StructureType<RoadsideStructure> type) {
        ROADSIDE = type;
    }

    public static void setRoadsideVillage(StructureType<RoadsideVillageStructure> type) {
        ROADSIDE_VILLAGE = type;
    }
    
    public static void setSpawnCabin(StructureType<SpawnCabinStructure> type) {
        SPAWN_CABIN = type;
    }

    public static void setBridge(StructureType<BridgeTemplateStructure> type) {
        BRIDGE_TEMPLATE = type;
    }
}
