package net.shiroha233.roadweaver.search;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.core.Registry;

import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.config.ConfigService;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构验证服务
 */
public final class StructureVerificationService {
    private StructureVerificationService() {}

    /**
     * 对一批预测结构点进行验证，返回通过验证的子集
     */
    public static List<StructureInfo> verifyPredictedStructures(ServerLevel level,
                                                                        List<StructureInfo> predicted) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())
                || !ConfigService.get().structurePredictionEnabled() || predicted == null || predicted.isEmpty()) {
            return List.of();
        }

        var source = level.getChunkSource();
        if (!(source instanceof ServerChunkCache)) {
            return new ArrayList<>(predicted);
        }
        ServerChunkCache chunkCache = (ServerChunkCache) source;
        var server = level.getServer();
        if (server == null) {
            return new ArrayList<>(predicted);
        }

        ChunkScanAccess scanAccess;
        try {
            scanAccess = chunkCache.chunkScanner();
        } catch (Throwable t) {
            return new ArrayList<>(predicted);
        }

        var registryAccess = level.registryAccess();
        ChunkGenerator generator = chunkCache.getGenerator();
        ChunkGeneratorStructureState generatorState = chunkCache.getGeneratorState();
        RandomState randomState = chunkCache.randomState();
        BiomeSource biomeSource = generator.getBiomeSource();
        long seed = level.getSeed();

        StructureCheck checker = new StructureCheck(
                scanAccess,
                registryAccess,
                server.getStructureManager(),
                level.dimension(),
                generator,
                randomState,
                level,
                biomeSource,
                seed,
                server.getFixerUpper()
        );

        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);

        ArrayList<StructureInfo> result = new ArrayList<>();

        for (StructureInfo info : predicted) {
            String idStr = info.structureId();
            if (idStr == null || idStr.isEmpty()) {
                result.add(info);
                continue;
            }

            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            if (rl == null) {
                result.add(info);
                continue;
            }

            Structure structure = structureRegistry.get(rl);
            if (structure == null) {
                result.add(info);
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(info.pos().getX() >> 4, info.pos().getZ() >> 4);
            StructureCheckResult checkResult;
            try {
                checkResult = checker.checkStart(chunkPos, structure, false);
            } catch (Throwable t) {
                result.add(info);
                continue;
            }
            if (checkResult == StructureCheckResult.START_PRESENT || checkResult == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                result.add(info);
            }
        }

        return result;
    }
}
