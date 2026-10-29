package net.shiroha233.roadweaver.worldgen.road.decoration.material;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.shiroha233.roadweaver.core.model.WoodAssets;

/**
 * 木材活板门映射器
 */
public final class WoodTrapdoorMapper {
    private WoodTrapdoorMapper() {}

    public static Block trapdoorForWood(WoodAssets assets) {
        if (assets == null || assets.planks() == null) return Blocks.SPRUCE_TRAPDOOR;
        Block p = assets.planks();
        if (p == Blocks.SPRUCE_PLANKS) return Blocks.SPRUCE_TRAPDOOR;
        if (p == Blocks.OAK_PLANKS) return Blocks.OAK_TRAPDOOR;
        if (p == Blocks.BIRCH_PLANKS) return Blocks.BIRCH_TRAPDOOR;
        if (p == Blocks.JUNGLE_PLANKS) return Blocks.JUNGLE_TRAPDOOR;
        if (p == Blocks.ACACIA_PLANKS) return Blocks.ACACIA_TRAPDOOR;
        if (p == Blocks.DARK_OAK_PLANKS) return Blocks.DARK_OAK_TRAPDOOR;
        if (p == Blocks.MANGROVE_PLANKS) return Blocks.MANGROVE_TRAPDOOR;
        if (p == Blocks.BAMBOO_PLANKS) return Blocks.BAMBOO_TRAPDOOR;
        if (p == Blocks.CHERRY_PLANKS) return Blocks.CHERRY_TRAPDOOR;
        if (p == Blocks.CRIMSON_PLANKS) return Blocks.CRIMSON_TRAPDOOR;
        if (p == Blocks.WARPED_PLANKS) return Blocks.WARPED_TRAPDOOR;
        return Blocks.SPRUCE_TRAPDOOR;
    }
}
