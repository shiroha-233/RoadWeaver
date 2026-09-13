package net.shiroha233.roadweaver.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 材料预设管理服务
 */
public final class PresetService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_DIR = "roadweaver";
    private static final String PRESET_DIR = "presets";

    public enum RoadType {
        ARTIFICIAL,
        NATURAL
    }

    private static final AtomicReference<Map<String, PresetDef>> PRESETS = new AtomicReference<>(new LinkedHashMap<>());

    private PresetService() {
    }

    public static synchronized void reload() {
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        Map<String, PresetDef> map = new LinkedHashMap<>();
        try {
            try {
                Files.createDirectories(presetDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to create preset directory: {}", presetDir, e);
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(presetDir, "*.json")) {
                for (Path p : ds) {
                    PresetFile dto = readPresetFile(p);
                    if (dto == null)
                        continue;
                    if (!isOverworldPreset(dto.dimensions)) {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    String id = dto.id;
                    if (id == null || id.isBlank())
                        id = stripExt(p.getFileName().toString());

                    List<String> valid = validateBlockIds(dto.materials);
                    List<String> validSlabs = validateBlockIds(dto.slabMaterials);

                    if (valid.isEmpty()) {
                        LOGGER.warn("Skip preset {} due to empty/invalid materials", p.getFileName());
                        continue;
                    }

                    String name = dto.name == null || dto.name.isBlank() ? id : dto.name;
                    RoadType type = parseRoadType(dto.type, id);
                    PresetDef def = new PresetDef(id, name, type,
                            Collections.unmodifiableList(valid), Collections.unmodifiableList(validSlabs));
                    if (map.containsKey(id)) {
                        LOGGER.warn("Duplicate preset id '{}', file {} is ignored", id, p.getFileName());
                        continue;
                    }
                    map.put(id, def);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed scanning presets: {}", presetDir, e);
        }

        if (map.isEmpty()) {
            try {
                writeSamplePresets(presetDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to write sample presets: {}", e.toString());
            }
            map = defaultPresets();
        }
        PRESETS.set(map);
        LOGGER.info("Presets loaded: {} entries", map.size());
    }

    private static List<String> validateBlockIds(List<String> ids) {
        if (ids == null) return List.of();
        List<String> valid = new ArrayList<>();
        for (String s : ids) {
            try {
                ResourceLocation rl = new ResourceLocation(s);
                Block b = BuiltInRegistries.BLOCK.get(rl);
                if (b != null && b != Blocks.AIR)
                    valid.add(s);
            } catch (Throwable ignored) {
            }
        }
        return valid;
    }

    private static RoadType parseRoadType(String typeStr, String presetId) {
        if (typeStr == null) return RoadType.ARTIFICIAL;
        try {
            return RoadType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid road type '{}' in preset {}, defaulting to ARTIFICIAL", typeStr, presetId);
            return RoadType.ARTIFICIAL;
        }
    }

    private static String stripExt(String fn) {
        int i = fn.lastIndexOf('.');
        return i > 0 ? fn.substring(0, i) : fn;
    }

    private static PresetFile readPresetFile(Path p) {
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(br, PresetFile.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to read preset file {}: {}", p.getFileName(), e.toString());
            return null;
        }
    }

    private static void writeSamplePresets(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        for (SamplePresets.SamplePresetTemplate template : SamplePresets.SAMPLE_PRESETS) {
            writePreset(dir.resolve(template.id() + ".json"), template.toPresetFile());
        }
    }

    private static void writePreset(Path file, PresetFile dto) {
        try {
            FileStorageIO.writeStringAtomic(file, GSON.toJson(dto));
        } catch (Exception ignored) {
        }
    }

    private static boolean isOverworldPreset(List<String> dimensions) {
        return dimensions == null || dimensions.isEmpty()
                || (dimensions.size() == 1 && "minecraft:overworld".equals(dimensions.get(0)));
    }

    private static Map<String, PresetDef> defaultPresets() {
        Map<String, PresetDef> m = new LinkedHashMap<>();
        for (SamplePresets.SamplePresetTemplate template : SamplePresets.SAMPLE_PRESETS) {
            PresetDef def = template.toPresetDef();
            if (m.containsKey(def.id()))
                continue;
            m.put(def.id(), def);
        }
        return m;
    }

    public static synchronized List<PresetDef> getAllPresets() {
        if (PRESETS.get().isEmpty())
            reload();
        return List.copyOf(PRESETS.get().values());
    }

    public static synchronized PresetDef choosePreset(RandomSource rnd, RoadType type) {
        if (PRESETS.get().isEmpty())
            reload();
        Map<String, PresetDef> all = PRESETS.get();

        List<PresetDef> candidates = new ArrayList<>();
        for (PresetDef def : all.values()) {
            if (def.type == type) {
                candidates.add(def);
            }
        }

        if (candidates.isEmpty()) {
            if (type == RoadType.NATURAL) {
                return new PresetDef("fallback_natural", "Natural Fallback", RoadType.NATURAL,
                        List.of("minecraft:dirt_path", "minecraft:gravel"), List.of());
            }
            return new PresetDef("fallback_artificial", "Artificial Fallback", RoadType.ARTIFICIAL,
                    List.of("minecraft:stone_bricks"), List.of("minecraft:stone_brick_slab"));
        }

        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private static List<BlockState> toBlockStates(List<String> ids) {
        List<BlockState> out = new ArrayList<>();
        if (ids == null)
            return out;
        for (String s : ids) {
            try {
                ResourceLocation rl = new ResourceLocation(s);
                Block b = BuiltInRegistries.BLOCK.get(rl);
                if (b != null && b != Blocks.AIR)
                    out.add(b.defaultBlockState());
            } catch (Throwable ignored) {
            }
        }
        if (out.isEmpty())
            out.add(Blocks.STONE_BRICKS.defaultBlockState());
        return out;
    }

    private static List<BlockState> toBlockStatesAllowEmpty(List<String> ids) {
        List<BlockState> out = new ArrayList<>();
        if (ids == null)
            return out;
        for (String s : ids) {
            try {
                ResourceLocation rl = new ResourceLocation(s);
                Block b = BuiltInRegistries.BLOCK.get(rl);
                if (b != null && b != Blocks.AIR)
                    out.add(b.defaultBlockState());
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static List<BlockState> toBlockStatesFromIds(List<String> ids) {
        return toBlockStates(ids);
    }

    public static List<BlockState> toBlockStatesFromIdsAllowEmpty(List<String> ids) {
        return toBlockStatesAllowEmpty(ids);
    }

    public static synchronized PresetDef findNaturalPresetForBiome(net.minecraft.resources.ResourceLocation biomeId) {
        if (biomeId == null)
            return null;
        if (PRESETS.get().isEmpty())
            reload();
        Map<String, PresetDef> all = PRESETS.get();

        String idPathOnly = "natural_" + biomeId.getPath();
        String idNsPath = "natural_" + biomeId.getNamespace() + "_" + biomeId.getPath();

        PresetDef def = all.get(idPathOnly);
        if (def == null)
            def = all.get(idNsPath);
        if (def == null)
            return null;
        if (def.type != RoadType.NATURAL)
            return null;
        return def;
    }

    public static synchronized List<List<String>> getMaterialCombos() {
        if (PRESETS.get().isEmpty())
            reload();
        List<List<String>> combos = new ArrayList<>();
        for (PresetDef d : PRESETS.get().values())
            combos.add(d.materials());
        return combos;
    }

    public static synchronized void saveOrUpdatePresetFile(String id, String name, RoadType type,
            List<String> materials, List<String> slabMaterials) {
        if (id == null || id.isBlank()) {
            return;
        }
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        try {
            Files.createDirectories(presetDir);
        } catch (Exception e) {
            LOGGER.warn("Failed to create preset directory: {}", presetDir, e);
        }
        PresetFile dto = new PresetFile();
        dto.id = id;
        dto.name = name;
        dto.type = type.name();
        dto.materials = materials == null ? List.of() : new ArrayList<>(materials);
        dto.slabMaterials = slabMaterials == null ? List.of() : new ArrayList<>(slabMaterials);
        writePreset(presetDir.resolve(id + ".json"), dto);
    }

    public static synchronized void saveOrUpdatePresetFile(String id, String name, List<String> materials,
            List<String> slabMaterials) {
        saveOrUpdatePresetFile(id, name, RoadType.ARTIFICIAL, materials, slabMaterials);
    }

    public static synchronized void deletePresetFile(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        try {
            Files.deleteIfExists(presetDir.resolve(id + ".json"));
        } catch (Exception e) {
            LOGGER.warn("Failed to delete preset file for id {}: {}", id, e.toString());
        }
    }

    static class PresetFile {
        String id;
        String name;
        String type;
        List<String> dimensions;
        List<String> materials;
        List<String> slabMaterials;
    }

    public record PresetDef(String id, String name, RoadType type, List<String> materials, List<String> slabMaterials) {
    }
}
