package net.shiroha233.roadweaver.worldgen.road.decoration.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 生物群系道路材料选择器
 */
public final class BiomeRoadMaterialSelector {
    private BiomeRoadMaterialSelector() {}

    public static List<BlockState> forBiome(WorldGenLevel world, BlockPos pos) {
        Holder<Biome> biome = world.getBiome(pos);
        Optional<ResourceKey<Biome>> keyOpt = biome.unwrapKey();
        if (keyOpt.isPresent()) {
            ResourceKey<Biome> key = keyOpt.get();
            if (key == Biomes.DESERT || key == Biomes.BEACH) {
                return list(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE);
            } else if (key == Biomes.BADLANDS || key == Biomes.ERODED_BADLANDS || key == Biomes.WOODED_BADLANDS) {
                return list(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE);
            } else if (biome.is(BiomeTags.IS_SAVANNA)) {
                return list(Blocks.DIRT_PATH, Blocks.COARSE_DIRT);
            } else if (biome.is(BiomeTags.IS_JUNGLE) || key == Biomes.BAMBOO_JUNGLE) {
                return list(Blocks.DIRT_PATH, Blocks.MOSSY_COBBLESTONE);
            } else if (biome.is(BiomeTags.IS_TAIGA)) {
                return list(Blocks.COARSE_DIRT, Blocks.GRAVEL);
            } else if (key == Biomes.SNOWY_PLAINS || key == Biomes.SNOWY_TAIGA || key == Biomes.GROVE
                    || key == Biomes.SNOWY_SLOPES || key == Biomes.JAGGED_PEAKS || key == Biomes.FROZEN_PEAKS) {
                return list(Blocks.ANDESITE, Blocks.COBBLESTONE);
            } else if (key == Biomes.SWAMP || key == Biomes.MANGROVE_SWAMP) {
                return list(Blocks.PACKED_MUD, Blocks.COARSE_DIRT);
            } else if (key == Biomes.DARK_FOREST) {
                return list(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE);
            } else if (key == Biomes.BIRCH_FOREST || key == Biomes.OLD_GROWTH_BIRCH_FOREST || key == Biomes.CHERRY_GROVE) {
                return list(Blocks.DIRT_PATH, Blocks.GRAVEL);
            } else if (key == Biomes.STONY_PEAKS || key == Biomes.WINDSWEPT_FOREST || key == Biomes.WINDSWEPT_HILLS
                    || key == Biomes.WINDSWEPT_GRAVELLY_HILLS) {
                return list(Blocks.STONE, Blocks.COBBLESTONE);
            } else if (key == Biomes.MUSHROOM_FIELDS) {
                return list(Blocks.STONE, Blocks.COBBLESTONE);
            }
        }
        return list(Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.COARSE_DIRT);
    }

    private static List<BlockState> list(net.minecraft.world.level.block.Block... blocks) {
        List<BlockState> out = new ArrayList<>();
        for (var b : blocks) out.add(b.defaultBlockState());
        return out;
    }
}
