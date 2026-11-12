package net.shiroha233.roadweaver.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置示例预设模板定义
 */
final class SamplePresets {
    static final List<SamplePresetTemplate> SAMPLE_PRESETS;

    static {
        List<SamplePresetTemplate> list = new ArrayList<>();
        list.add(new SamplePresetTemplate(
                "stone_street",
                "Stone Street",
                PresetService.RoadType.ARTIFICIAL,
                List.of("minecraft:stone_bricks", "minecraft:polished_andesite"),
                List.of("minecraft:stone_brick_slab", "minecraft:polished_andesite_slab")));
        list.add(new SamplePresetTemplate(
                "mud_road",
                "Mud Road",
                PresetService.RoadType.ARTIFICIAL,
                List.of("minecraft:mud_bricks", "minecraft:packed_mud"),
                List.of("minecraft:mud_brick_slab")));
        list.add(new SamplePresetTemplate(
                "aged_stone",
                "Aged Stone",
                PresetService.RoadType.ARTIFICIAL,
                List.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
                List.of("minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab")));

        list.add(naturalPreset("plains", "Plains Trail",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("sunflower_plains", "Sunflower Plains Trail",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("meadow", "Meadow Trail",
                List.of("minecraft:dirt_path", "minecraft:moss_block", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("forest", "Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("flower_forest", "Flower Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:moss_block"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("birch_forest", "Birch Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:birch_slab")));
        list.add(naturalPreset("old_growth_birch_forest", "Old Growth Birch Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:birch_slab")));
        list.add(naturalPreset("dark_forest", "Dark Forest Trail",
                List.of("minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:podzol"),
                List.of("minecraft:dark_oak_slab")));
        list.add(naturalPreset("cherry_grove", "Cherry Grove Path",
                List.of("minecraft:dirt_path", "minecraft:moss_block", "minecraft:coarse_dirt"),
                List.of("minecraft:cherry_slab")));
        list.add(naturalPreset("taiga", "Taiga Trail",
                List.of("minecraft:coarse_dirt", "minecraft:podzol", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("old_growth_pine_taiga", "Old Growth Pine Trail",
                List.of("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("old_growth_spruce_taiga", "Old Growth Spruce Trail",
                List.of("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_taiga", "Snowy Taiga Trail",
                List.of("minecraft:snow_block", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_plains", "Snowy Plains Trail",
                List.of("minecraft:snow_block", "minecraft:packed_ice", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("ice_spikes", "Ice Spikes Trail",
                List.of("minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow_block"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_slopes", "Snowy Slopes Trail",
                List.of("minecraft:snow_block", "minecraft:powder_snow", "minecraft:stone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("frozen_peaks", "Frozen Peaks Trail",
                List.of("minecraft:packed_ice", "minecraft:snow_block", "minecraft:stone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("grove", "Grove Trail",
                List.of("minecraft:snow_block", "minecraft:powder_snow", "minecraft:dirt_path"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("windswept_hills", "Windswept Hills Trail",
                List.of("minecraft:stone", "minecraft:gravel", "minecraft:cobblestone"),
                List.of("minecraft:stone_slab", "minecraft:cobblestone_slab")));
        list.add(naturalPreset("windswept_gravelly_hills", "Windswept Gravelly Trail",
                List.of("minecraft:gravel", "minecraft:stone", "minecraft:cobblestone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("windswept_forest", "Windswept Forest Trail",
                List.of("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("stony_peaks", "Stony Peaks Trail",
                List.of("minecraft:stone", "minecraft:calcite", "minecraft:andesite"),
                List.of("minecraft:stone_slab", "minecraft:andesite_slab")));
        list.add(naturalPreset("jagged_peaks", "Jagged Peaks Trail",
                List.of("minecraft:stone", "minecraft:snow_block", "minecraft:packed_ice"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("desert", "Desert Trail",
                List.of("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:sand", "minecraft:gravel"),
                List.of("minecraft:sandstone_slab", "minecraft:smooth_sandstone_slab")));
        list.add(naturalPreset("badlands", "Badlands Trail",
                List.of("minecraft:red_sand", "minecraft:terracotta", "minecraft:orange_terracotta"),
                List.of("minecraft:red_sandstone_slab")));
        list.add(naturalPreset("eroded_badlands", "Eroded Badlands Trail",
                List.of("minecraft:red_sand", "minecraft:terracotta", "minecraft:red_terracotta"),
                List.of("minecraft:red_sandstone_slab")));
        list.add(naturalPreset("wooded_badlands", "Wooded Badlands Trail",
                List.of("minecraft:coarse_dirt", "minecraft:terracotta", "minecraft:red_sand"),
                List.of("minecraft:red_sandstone_slab", "minecraft:oak_slab")));
        list.add(naturalPreset("jungle", "Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:rooted_dirt", "minecraft:mud"),
                List.of("minecraft:jungle_slab")));
        list.add(naturalPreset("sparse_jungle", "Sparse Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:jungle_slab")));
        list.add(naturalPreset("bamboo_jungle", "Bamboo Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:podzol", "minecraft:rooted_dirt"),
                List.of("minecraft:bamboo_slab", "minecraft:jungle_slab")));
        list.add(naturalPreset("swamp", "Swamp Path",
                List.of("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:dirt_path"),
                List.of("minecraft:mud_brick_slab", "minecraft:oak_slab")));
        list.add(naturalPreset("mangrove_swamp", "Mangrove Swamp Path",
                List.of("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mangrove_roots"),
                List.of("minecraft:mud_brick_slab", "minecraft:mangrove_slab")));
        list.add(naturalPreset("savanna", "Savanna Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:acacia_slab")));
        list.add(naturalPreset("savanna_plateau", "Savanna Plateau Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:stone"),
                List.of("minecraft:acacia_slab")));
        list.add(naturalPreset("windswept_savanna", "Windswept Savanna Trail",
                List.of("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
                List.of("minecraft:acacia_slab", "minecraft:stone_slab")));
        list.add(naturalPreset("beach", "Beach Path",
                List.of("minecraft:sand", "minecraft:sandstone", "minecraft:gravel"),
                List.of("minecraft:sandstone_slab")));
        list.add(naturalPreset("snowy_beach", "Snowy Beach Path",
                List.of("minecraft:snow_block", "minecraft:sand", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("stony_shore", "Stony Shore Path",
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:gravel"),
                List.of("minecraft:stone_slab", "minecraft:cobblestone_slab")));
        list.add(naturalPreset("river", "River Bank Path",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:sand"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("frozen_river", "Frozen River Path",
                List.of("minecraft:packed_ice", "minecraft:gravel", "minecraft:snow_block"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("mushroom_fields", "Mushroom Fields Path",
                List.of("minecraft:mycelium", "minecraft:dirt_path", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));

        SAMPLE_PRESETS = List.copyOf(list);
    }

    private static SamplePresetTemplate naturalPreset(String biomeId, String displayName, List<String> materials,
            List<String> slabMaterials) {
        String sanitizedId = biomeId.contains(":")
                ? biomeId.replace(':', '_')
                : biomeId;
        String presetId = "natural_" + sanitizedId;
        return new SamplePresetTemplate(
                presetId,
                displayName,
                PresetService.RoadType.NATURAL,
                List.copyOf(materials),
                List.copyOf(slabMaterials));
    }

    record SamplePresetTemplate(String id, String name, PresetService.RoadType type,
            List<String> materials, List<String> slabMaterials) {
        PresetService.PresetDef toPresetDef() {
            return new PresetService.PresetDef(
                    id,
                    name,
                    type,
                    List.copyOf(materials),
                    List.copyOf(slabMaterials));
        }

        PresetService.PresetFile toPresetFile() {
            PresetService.PresetFile dto = new PresetService.PresetFile();
            dto.id = id;
            dto.name = name;
            dto.type = type.name();
            dto.materials = List.copyOf(materials);
            dto.slabMaterials = List.copyOf(slabMaterials);
            return dto;
        }
    }

    private SamplePresets() {
    }
}
