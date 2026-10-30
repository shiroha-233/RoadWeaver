package net.shiroha233.roadweaver.structures.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideVillageStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/** NeoForge 平台结构注册。 */
public final class StructureRegistry {
    private StructureRegistry() {}
    
    // 结构片段类型注册器。
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, RoadWeaver.MOD_ID);
    
    // 结构类型注册器。
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, RoadWeaver.MOD_ID);
    
    // 简单模板片段类型（需要 StructureTemplateManager）。
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SIMPLE_TEMPLATE =
            STRUCTURE_PIECE_TYPES.register("simple_template",
                () -> (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new);
    
    // 路边结构类型。
    public static final DeferredHolder<StructureType<?>, StructureType<RoadsideStructure>> ROADSIDE =
            STRUCTURE_TYPES.register("roadside",
                () -> () -> RoadsideStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<RoadsideVillageStructure>> ROADSIDE_VILLAGE =
            STRUCTURE_TYPES.register("roadside_village",
                () -> () -> RoadsideVillageStructure.CODEC);
    
    // 初始小屋结构类型。
    public static final DeferredHolder<StructureType<?>, StructureType<SpawnCabinStructure>> SPAWN_CABIN =
            STRUCTURE_TYPES.register("spawn_cabin",
                () -> () -> SpawnCabinStructure.CODEC);

    // 桥模板结构类型。
    public static final DeferredHolder<StructureType<?>, StructureType<BridgeTemplateStructure>> BRIDGE_TEMPLATE =
            STRUCTURE_TYPES.register("bridge",
                    () -> () -> BridgeTemplateStructure.CODEC);
    
    /** 注册到模组事件总线。 */
    public static void register(IEventBus modBus) {
        STRUCTURE_PIECE_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        
        // 注册完成后设置跨平台静态引用。
        modBus.addListener((FMLCommonSetupEvent event) -> {
            event.enqueueWork(() -> {
                ModStructurePieceTypes.setSimpleTemplate(SIMPLE_TEMPLATE.get());
                ModStructureTypes.setRoadside(ROADSIDE.get());
                ModStructureTypes.setRoadsideVillage(ROADSIDE_VILLAGE.get());
                ModStructureTypes.setSpawnCabin(SPAWN_CABIN.get());
                ModStructureTypes.setBridge(BRIDGE_TEMPLATE.get());
            });
        });
    }
}

