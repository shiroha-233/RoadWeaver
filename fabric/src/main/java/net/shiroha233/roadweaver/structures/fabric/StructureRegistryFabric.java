package net.shiroha233.roadweaver.structures.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideVillageStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * Fabric 结构注册
 */
public final class StructureRegistryFabric {
    private StructureRegistryFabric() {}
    
    public static void register() {
        registerStructurePieceTypes();
        registerStructureTypes();
    }
    
    private static void registerStructurePieceTypes() {
        StructurePieceType simpleTemplate = (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new;
        
        Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            new ResourceLocation(RoadWeaver.MOD_ID, "simple_template"),
            simpleTemplate
        );
        
        ModStructurePieceTypes.setSimpleTemplate(simpleTemplate);
    }
    
    private static void registerStructureTypes() {
        StructureType<RoadsideStructure> roadsideType = () -> RoadsideStructure.CODEC.codec();
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            new ResourceLocation(RoadWeaver.MOD_ID, "roadside"),
            roadsideType
        );
        ModStructureTypes.setRoadside(roadsideType);

        StructureType<RoadsideVillageStructure> roadsideVillageType = () -> RoadsideVillageStructure.CODEC.codec();
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            new ResourceLocation(RoadWeaver.MOD_ID, "roadside_village"),
            roadsideVillageType
        );
        ModStructureTypes.setRoadsideVillage(roadsideVillageType);
        
        StructureType<SpawnCabinStructure> spawnCabinType = () -> SpawnCabinStructure.CODEC.codec();
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            new ResourceLocation(RoadWeaver.MOD_ID, "spawn_cabin"),
            spawnCabinType
        );
        ModStructureTypes.setSpawnCabin(spawnCabinType);

        StructureType<BridgeTemplateStructure> bridgeTemplateType = () -> BridgeTemplateStructure.CODEC.codec();
        Registry.register(
                BuiltInRegistries.STRUCTURE_TYPE,
                new ResourceLocation(RoadWeaver.MOD_ID, "bridge"),
                bridgeTemplateType
        );
        ModStructureTypes.setBridge(bridgeTemplateType);
    }
}
