package net.shiroha233.roadweaver.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.model.StructureInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

/**
 * 结构位置预测服务
 */
public final class StructurePredictor {
    private StructurePredictor() {
    }

    public static List<StructureInfo> predictStructuresInRect(ServerLevel level,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        if (!isOverworld(level)) return List.of();
        return predictOverworldStructuresInRect(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ, biomePrefilter,
                whitelist, blacklist);
    }

    public static List<StructureInfo> predictOverworldVillagesAroundSpawn(ServerLevel level, int radiusChunks,
            boolean biomePrefilter) {
        if (!isOverworld(level)) return List.of();
        RegistryAccess registryAccess = level.registryAccess();
        Registry<StructureSet> setRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
        Optional<Holder.Reference<StructureSet>> optVillages = setRegistry.getHolder(BuiltinStructureSets.VILLAGES);
        if (optVillages.isEmpty())
            return List.of();
        StructureSet set = optVillages.get().value();
        StructurePlacement placement = set.placement();
        if (!(placement instanceof RandomSpreadStructurePlacement rssp))
            return List.of();

        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = cx - radiusChunks;
        int maxX = cx + radiusChunks;
        int minZ = cz - radiusChunks;
        int maxZ = cz + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Set<Holder<Biome>> allowedBiomes = null;
        if (biomePrefilter) {
            allowedBiomes = new HashSet<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Structure structure = entry.structure().value();
                for (Holder<Biome> b : structure.biomes()) {
                    allowedBiomes.add(b);
                }
            }
        }

        int spacing = rssp.spacing();
        int startI = Math.floorDiv(minX, spacing);
        int endI = Math.floorDiv(maxX, spacing);
        int startJ = Math.floorDiv(minZ, spacing);
        int endJ = Math.floorDiv(maxZ, spacing);

        long seed = level.getSeed();
        List<StructureInfo> result = new ArrayList<>();

        for (int i = startI; i <= endI; i++) {
            for (int j = startJ; j <= endJ; j++) {
                int baseX = i * spacing;
                int baseZ = j * spacing;
                ChunkPos candidate = rssp.getPotentialStructureChunk(seed, baseX, baseZ);
                int x = candidate.x;
                int z = candidate.z;
                if (x < minX || x > maxX || z < minZ || z > maxZ)
                    continue;
                if (!placement.isStructureChunk(state, x, z))
                    continue;

                BlockPos locatePos = placement.getLocatePos(candidate);
                if (biomePrefilter && allowedBiomes != null) {
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (!allowedBiomes.contains(sample))
                        continue;
                }

                result.add(new StructureInfo(locatePos, "village"));
            }
        }

        return result;
    }

    public static List<StructureInfo> predictOverworldStructuresInRect(ServerLevel level,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        if (!isOverworld(level)) return List.of();

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Filters filters = Filters.of(whitelist, blacklist);

        List<StructureInfo> result = new ArrayList<>();

        List<Holder<StructureSet>> possibleSets = state.possibleStructureSets();
        for (Holder<StructureSet> holder : possibleSets) {
            StructureSet set = holder.value();
            StructurePlacement placement = set.placement();

            List<Holder<Structure>> matchedStructures = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> structureHolder = entry.structure();
                Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                if (key.isEmpty())
                    continue;
                ResourceLocation id = key.get().location();
                if (filters.matches(structureHolder, id)) {
                    matchedStructures.add(structureHolder);
                }
            }

            if (matchedStructures.isEmpty()) {
                if (filters.hasWhitelist())
                    continue;
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    Holder<Structure> structureHolder = entry.structure();
                    Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                    if (key.isEmpty())
                        continue;
                    ResourceLocation id = key.get().location();
                    if (!filters.isBlacklisted(structureHolder, id)) {
                        matchedStructures.add(structureHolder);
                    }
                }
                if (matchedStructures.isEmpty())
                    continue;
            }

            String labelId = matchedStructures.stream()
                    .map(h -> h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString)
                            .orElse("structure"))
                    .findFirst().orElse("structure");

            if (placement instanceof ConcentricRingsStructurePlacement crsp) {
                List<ChunkPos> ring = state.getRingPositionsFor(crsp);
                if (ring == null || ring.isEmpty()) {
                    continue;
                }
                for (ChunkPos cp : ring) {
                    int x = cp.x;
                    int z = cp.z;
                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ)
                        continue;
                    if (!placement.isStructureChunk(state, x, z))
                        continue;
                    BlockPos locatePos = placement.getLocatePos(cp);
                    result.add(new StructureInfo(locatePos, labelId));
                }
                continue;
            }

            if (!(placement instanceof RandomSpreadStructurePlacement rssp)) {
                continue;
            }

            Set<Holder<Biome>> allowedBiomes = null;
            if (biomePrefilter) {
                allowedBiomes = new HashSet<>();
                for (Holder<Structure> h : matchedStructures) {
                    for (Holder<Biome> b : h.value().biomes()) {
                        allowedBiomes.add(b);
                    }
                }
            }

            int spacing = rssp.spacing();
            int startI = Math.floorDiv(minChunkX, spacing);
            int endI = Math.floorDiv(maxChunkX, spacing);
            int startJ = Math.floorDiv(minChunkZ, spacing);
            int endJ = Math.floorDiv(maxChunkZ, spacing);

            for (int i = startI; i <= endI; i++) {
                for (int j = startJ; j <= endJ; j++) {
                    int baseX = i * spacing;
                    int baseZ = j * spacing;
                    ChunkPos candidate = rssp.getPotentialStructureChunk(level.getSeed(), baseX, baseZ);
                    int x = candidate.x;
                    int z = candidate.z;
                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ)
                        continue;
                    if (!placement.isStructureChunk(state, x, z))
                        continue;

                    BlockPos locatePos = placement.getLocatePos(candidate);
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (biomePrefilter && allowedBiomes != null) {
                        if (!allowedBiomes.contains(sample))
                            continue;
                    }

                    String chosenId = labelId;
                    for (Holder<Structure> h : matchedStructures) {
                        if (h.value().biomes().contains(sample)) {
                            chosenId = h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString)
                                    .orElse(labelId);
                            break;
                        }
                    }
                    result.add(new StructureInfo(locatePos, chosenId));
                }
            }
        }

        return result;
    }

    public static List<StructureInfo> predictOverworldStructuresAroundSpawn(ServerLevel level,
            int radiusChunks,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        if (!isOverworld(level)) return List.of();
        RegistryAccess access = level.registryAccess();
        Registry<StructureSet> setRegistry = access.registryOrThrow(Registries.STRUCTURE_SET);

        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = cx - radiusChunks;
        int maxX = cx + radiusChunks;
        int minZ = cz - radiusChunks;
        int maxZ = cz + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Filters filters = Filters.of(whitelist, blacklist);

        List<StructureInfo> result = new ArrayList<>();

        for (Holder.Reference<StructureSet> holder : setRegistry.holders().toList()) {
            StructureSet set = holder.value();
            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement rssp))
                continue;

            List<Holder<Structure>> matchedStructures = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> structureHolder = entry.structure();
                Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                if (key.isEmpty())
                    continue;
                ResourceLocation id = key.get().location();
                if (filters.matches(structureHolder, id)) {
                    matchedStructures.add(structureHolder);
                }
            }

            if (matchedStructures.isEmpty()) {
                if (filters.hasWhitelist())
                    continue;
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    Holder<Structure> structureHolder = entry.structure();
                    Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                    if (key.isEmpty())
                        continue;
                    ResourceLocation id = key.get().location();
                    if (!filters.isBlacklisted(structureHolder, id)) {
                        matchedStructures.add(structureHolder);
                    }
                }
                if (matchedStructures.isEmpty())
                    continue;
            }

            Set<Holder<Biome>> allowedBiomes = null;
            if (biomePrefilter) {
                allowedBiomes = new HashSet<>();
                for (Holder<Structure> h : matchedStructures) {
                    for (Holder<Biome> b : h.value().biomes()) {
                        allowedBiomes.add(b);
                    }
                }
            }

            int spacing = rssp.spacing();
            int startI = Math.floorDiv(minX, spacing);
            int endI = Math.floorDiv(maxX, spacing);
            int startJ = Math.floorDiv(minZ, spacing);
            int endJ = Math.floorDiv(maxZ, spacing);

            String labelId = matchedStructures.stream()
                    .map(h -> h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString)
                            .orElse("structure"))
                    .findFirst().orElse("structure");

            for (int i = startI; i <= endI; i++) {
                for (int j = startJ; j <= endJ; j++) {
                    int baseX = i * spacing;
                    int baseZ = j * spacing;
                    ChunkPos candidate = rssp.getPotentialStructureChunk(level.getSeed(), baseX, baseZ);
                    int x = candidate.x;
                    int z = candidate.z;
                    if (x < minX || x > maxX || z < minZ || z > maxZ)
                        continue;
                    if (!placement.isStructureChunk(state, x, z))
                        continue;
                    BlockPos locatePos = placement.getLocatePos(candidate);
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (biomePrefilter && allowedBiomes != null) {
                        if (!allowedBiomes.contains(sample))
                            continue;
                    }
                    String chosenId = labelId;
                    for (Holder<Structure> h : matchedStructures) {
                        if (h.value().biomes().contains(sample)) {
                            chosenId = h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString)
                                    .orElse(labelId);
                            break;
                        }
                    }
                    result.add(new StructureInfo(locatePos, chosenId));
                }
            }
        }

        return result;
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null
                && Level.OVERWORLD.equals(level.dimension())
                && ConfigService.get().structurePredictionEnabled();
    }

    private static final class Filters {
        private final List<String> whitelist;
        private final List<String> blacklist;

        private Filters(List<String> whitelist, List<String> blacklist) {
            this.whitelist = normalize(whitelist);
            this.blacklist = normalize(blacklist);
        }

        static Filters of(List<String> whitelist, List<String> blacklist) {
            return new Filters(whitelist, blacklist);
        }

        boolean hasWhitelist() {
            return !whitelist.isEmpty();
        }

        boolean matches(Holder<Structure> holder, ResourceLocation id) {
            boolean whiteOk = whitelist.isEmpty() || whitelist.stream().anyMatch(p -> matchesPattern(holder, id, p));
            boolean blackHit = blacklist.stream().anyMatch(p -> matchesPattern(holder, id, p));
            return whiteOk && !blackHit;
        }

        boolean isBlacklisted(Holder<Structure> holder, ResourceLocation id) {
            return blacklist.stream().anyMatch(p -> matchesPattern(holder, id, p));
        }

        private boolean matchesPattern(Holder<Structure> holder, ResourceLocation id, String pattern) {
            if (pattern == null || pattern.isEmpty())
                return false;
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            String idStr = id.toString().toLowerCase(Locale.ROOT);
            if (p.startsWith("#")) {
                String raw = p.substring(1);
                ResourceLocation tagId = ResourceLocation.tryParse(raw);
                if (tagId == null)
                    return false;
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
                return holder.is(tag);
            }
            if (p.endsWith("/*")) {
                String base = p.substring(0, p.length() - 2);
                if (idStr.startsWith(base + "/"))
                    return true;
                if (idStr.startsWith(base + "_"))
                    return true;
                if (idStr.startsWith(base + "-"))
                    return true;
                if (idStr.startsWith(base + "."))
                    return true;
                return false;
            }
            if (p.endsWith(":*")) {
                String ns = p.substring(0, p.length() - 2);
                int idx = ns.indexOf(':');
                if (idx > 0)
                    ns = ns.substring(0, idx);
                return id.getNamespace().equalsIgnoreCase(ns);
            }
            return idStr.equals(p);
        }

        private static List<String> normalize(List<String> src) {
            List<String> out = new ArrayList<>();
            if (src == null)
                return out;
            for (String s : src) {
                if (s == null)
                    continue;
                String v = s.trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty())
                    out.add(v);
            }
            return out;
        }
    }
}
