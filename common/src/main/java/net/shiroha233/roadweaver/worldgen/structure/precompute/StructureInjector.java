package net.shiroha233.roadweaver.worldgen.structure.precompute;

import net.minecraft.core.Registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.worldgen.structure.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.worldgen.structure.types.RoadsideStructure;
import net.shiroha233.roadweaver.worldgen.structure.types.RoadsideVillageStructure;
import net.shiroha233.roadweaver.worldgen.structure.types.SpawnCabinStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构注入器
 */
public final class StructureInjector {
    private StructureInjector() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureInjector");
    
    public static void injectPendingStructures(ServerLevel level, ChunkAccess chunk) {
        if (level == null || chunk == null || !Level.OVERWORLD.equals(level.dimension())) return;
        injectSimplePendingStructures(level, chunk);
        injectRoadsideVillages(level, chunk);
    }

    private static void injectSimplePendingStructures(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<PendingRoadsideStructure> pending = PendingStructureStorage.getPendingStructures(level, chunkPos);
        if (pending.isEmpty()) {
            return;
        }
        
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        StructureManager structureManager = level.structureManager();
        StructureTemplateManager templateManager = level.getStructureTemplateManager();
        
        int injectedCount = 0;
        
        for (PendingRoadsideStructure pendingStructure : pending) {
            try {
                Structure structure = structureRegistry.getValue(pendingStructure.structureId());
                if (structure == null) {
                    LOGGER.warn("Structure {} not found in registry, skipping", pendingStructure.structureId());
                    continue;
                }
                
                SimpleTemplatePiece piece;
                if (structure instanceof RoadsideStructure roadsideStructure) {
                    piece = new SimpleTemplatePiece(
                        templateManager,
                        roadsideStructure.templateId(),
                        pendingStructure.anchor(),
                        pendingStructure.rotation(),
                        Mirror.NONE,
                        roadsideStructure.mobSpawns(),
                        roadsideStructure.lootConfigs()
                    );
                } else if (structure instanceof SpawnCabinStructure spawnCabin) {
                    piece = new SimpleTemplatePiece(
                        templateManager,
                        spawnCabin.templateId(),
                        pendingStructure.anchor(),
                        pendingStructure.rotation(),
                        Mirror.NONE
                    );
                } else {
                    LOGGER.warn("Structure {} is not a supported type, skipping", pendingStructure.structureId());
                    continue;
                }
                
                StructureStart start = new StructureStart(
                    structure,
                    chunkPos,
                    0,
                    new PiecesContainer(List.of(piece))
                );
                
                structureManager.setStartForStructure(structure, start, chunk);
                injectedCount++;
                
            } catch (Exception e) {
                LOGGER.error("Failed to inject structure {} at {}", 
                    pendingStructure.structureId(), pendingStructure.anchor(), e);
            }
        }
        
        if (injectedCount > 0) {
            LOGGER.debug("Injected {} roadside structures into chunk [{}, {}]", 
                injectedCount, chunkPos.x(), chunkPos.z());
        }
        
        PendingStructureStorage.markAsInjected(level, chunkPos);
    }

    private static void injectRoadsideVillages(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<PendingRoadsideVillage> villages = PendingRoadsideVillageStorage.getPendingVillages(level, chunkPos);
        if (villages.isEmpty()) {
            return;
        }

        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        StructureManager structureManager = level.structureManager();
        StructureTemplateManager templateManager = level.getStructureTemplateManager();

        int injectedCount = 0;

        for (PendingRoadsideVillage village : villages) {
            try {
                Structure structure = structureRegistry.getValue(village.structureId());
                if (!(structure instanceof RoadsideVillageStructure)) {
                    LOGGER.warn("Roadside village structure {} is not available", village.structureId());
                    continue;
                }

                List<PoolElementStructurePiece> pieces = RoadsideVillageJigsawPlanner.createPieces(level, village, templateManager);
                if (pieces.isEmpty()) {
                    LOGGER.debug("Roadside village {} produced no pieces", village.placementId());
                    continue;
                }

                StructureStart start = new StructureStart(
                    structure,
                    chunkPos,
                    0,
                    new PiecesContainer(new ArrayList<>(pieces))
                );
                structureManager.setStartForStructure(structure, start, chunk);
                injectedCount++;
            } catch (Exception e) {
                LOGGER.error("Failed to inject roadside village {}", village.placementId(), e);
            }
        }

        if (injectedCount > 0) {
            LOGGER.debug("Injected {} roadside villages into chunk [{}, {}]", injectedCount, chunkPos.x(), chunkPos.z());
        }

        PendingRoadsideVillageStorage.markAsInjected(level, chunkPos);
    }
}
