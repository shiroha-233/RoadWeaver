package net.countered.settlementroads.features.decoration.util;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.biome.Biome;

public class WoodSelector {

    public static Records.WoodAssets forBiome(WorldGenLevel world, BlockPos pos) {
        Biome biome = world.getBiome(pos).value();
        ResourceKey<Biome> biomeKey = world.getBiome(pos).unwrapKey().orElse(null);

        if (biomeKey != null) {
            if (biomeKey == Biomes.BAMBOO_JUNGLE) {
                return new Records.WoodAssets(Blocks.BAMBOO_FENCE, Blocks.BAMBOO_HANGING_SIGN, Blocks.BAMBOO_PLANKS);
            }
            else if (isBiomeTagged(biome, "is_jungle")) { // after Bamboo Jungle
                return new Records.WoodAssets(Blocks.JUNGLE_FENCE, Blocks.JUNGLE_HANGING_SIGN, Blocks.JUNGLE_PLANKS);
            }
            else if (isBiomeTagged(biome, "is_savanna")) {
                return new Records.WoodAssets(Blocks.ACACIA_FENCE, Blocks.ACACIA_HANGING_SIGN, Blocks.ACACIA_PLANKS);
            }
            else if (biomeKey == Biomes.DARK_FOREST) {
                return new Records.WoodAssets(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_HANGING_SIGN, Blocks.DARK_OAK_PLANKS);
            }
            else if (biomeKey == Biomes.CHERRY_GROVE) {
                return new Records.WoodAssets(Blocks.CHERRY_FENCE, Blocks.CHERRY_HANGING_SIGN, Blocks.CHERRY_PLANKS);
            }
            else if (biomeKey == Biomes.BIRCH_FOREST || biomeKey == Biomes.OLD_GROWTH_BIRCH_FOREST) {
                return new Records.WoodAssets(Blocks.BIRCH_FENCE, Blocks.BIRCH_HANGING_SIGN, Blocks.BIRCH_PLANKS);
            }
            else if (isBiomeTagged(biome, "is_taiga")) {
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

    private static boolean isBiomeTagged(Biome biome, String tag) {
        // 使用Forge注册表来检查生物群系标签
        ResourceLocation biomeResLoc = ForgeRegistries.BIOMES.getKey(biome);
        if (biomeResLoc == null) return false;

        // 简化处理：返回一些常见生态类型判断
        String biomePath = biomeResLoc.getPath();
        switch (tag) {
            case "is_jungle":
                return biomePath.contains("jungle");
            case "is_savanna":
                return biomePath.contains("savanna") || biomePath.contains("plains");
            case "is_taiga":
                return biomePath.contains("taiga") || biomePath.contains("grove");
            default:
                return false;
        }
    }
}
