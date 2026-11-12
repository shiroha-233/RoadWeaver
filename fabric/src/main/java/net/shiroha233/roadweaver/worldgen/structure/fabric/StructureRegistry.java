package net.shiroha233.roadweaver.worldgen.structure.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.worldgen.structure.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.worldgen.structure.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.worldgen.structure.types.ModStructureTypes;
import net.shiroha233.roadweaver.worldgen.structure.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.worldgen.structure.types.RoadsideStructure;
import net.shiroha233.roadweaver.worldgen.structure.types.RoadsideVillageStructure;
import net.shiroha233.roadweaver.worldgen.structure.types.SpawnCabinStructure;

/**
 * Fabric 结构注册
 */
public final class StructureRegistry {
    private StructureRegistry() {}
    
    public static void register() {
        registerStructurePieceTypes();
        registerStructureTypes();
    }
    
    private static void registerStructurePieceTypes() {
        StructurePieceType simpleTemplate = (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new;
        
        Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "simple_template"),
            simpleTemplate
        );
        
        ModStructurePieceTypes.setSimpleTemplate(simpleTemplate);
    }
    
    private static void registerStructureTypes() {
        StructureType<RoadsideStructure> roadsideType = () -> RoadsideStructure.CODEC;
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadside"),
            roadsideType
        );
        ModStructureTypes.setRoadside(roadsideType);

        StructureType<RoadsideVillageStructure> roadsideVillageType = () -> RoadsideVillageStructure.CODEC;
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadside_village"),
            roadsideVillageType
        );
        ModStructureTypes.setRoadsideVillage(roadsideVillageType);
        
        StructureType<SpawnCabinStructure> spawnCabinType = () -> SpawnCabinStructure.CODEC;
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "spawn_cabin"),
            spawnCabinType
        );
        ModStructureTypes.setSpawnCabin(spawnCabinType);

        StructureType<BridgeTemplateStructure> bridgeTemplateType = () -> BridgeTemplateStructure.CODEC;
        Registry.register(
                BuiltInRegistries.STRUCTURE_TYPE,
                ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "bridge"),
                bridgeTemplateType
        );
        ModStructureTypes.setBridge(bridgeTemplateType);
    }
}
