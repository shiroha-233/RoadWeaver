package net.countered.settlementroads.features.decoration.util;


import net.countered.settlementroads.helpers.Records;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Optional;

public class WoodSelector {

    public static Records.WoodAssets forBiome(StructureWorldAccess world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        Optional<RegistryKey<Biome>> optionalBiomeRegistryKey = biome.getKey();
        RegistryKey<Biome> biomeKey;

        if (optionalBiomeRegistryKey.isPresent()) {
            biomeKey = optionalBiomeRegistryKey.get();

            if (biomeKey == BiomeKeys.BAMBOO_JUNGLE) {
                return new Records.WoodAssets(Blocks.BAMBOO_FENCE, Blocks.BAMBOO_HANGING_SIGN, Blocks.BAMBOO_PLANKS);
            }
            else if (biome.isIn(BiomeTags.IS_JUNGLE)) { // after Bamboo Jungle
                return new Records.WoodAssets(Blocks.JUNGLE_FENCE, Blocks.JUNGLE_HANGING_SIGN, Blocks.JUNGLE_PLANKS);
            }
            else if (biome.isIn(BiomeTags.IS_SAVANNA)) {
                return new Records.WoodAssets(Blocks.ACACIA_FENCE, Blocks.ACACIA_HANGING_SIGN, Blocks.ACACIA_PLANKS);
            }
            else if (biomeKey == BiomeKeys.DARK_FOREST) {
                return new Records.WoodAssets(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_HANGING_SIGN, Blocks.DARK_OAK_PLANKS);
            }
            else if (biomeKey == BiomeKeys.CHERRY_GROVE) {
                return new Records.WoodAssets(Blocks.CHERRY_FENCE, Blocks.CHERRY_HANGING_SIGN, Blocks.CHERRY_PLANKS);
            }
            else if (biomeKey == BiomeKeys.BIRCH_FOREST || biomeKey == BiomeKeys.OLD_GROWTH_BIRCH_FOREST) {
                return new Records.WoodAssets(Blocks.BIRCH_FENCE, Blocks.BIRCH_HANGING_SIGN, Blocks.BIRCH_PLANKS);
            }
            else if (biome.isIn(BiomeTags.IS_TAIGA)) {
                return new Records.WoodAssets(Blocks.SPRUCE_FENCE, Blocks.SPRUCE_HANGING_SIGN, Blocks.SPRUCE_PLANKS);
            }
            else {
                return new Records.WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN, Blocks.OAK_PLANKS);
            }
        }
        else {
            return new Records.WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN, Blocks.OAK_PLANKS);
        }
    }
}

