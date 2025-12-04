package net.shiroha233.roadweaver.features.decoration.system;

import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.features.decoration.compat.RoadFeatureCompat;

public final class PlacementRules {
    private PlacementRules() {}

    /**
     * 放置检查：道路可覆盖任意方块。
     * 仅保留兼容层检查（如其他模组明确禁止的区域）。
     */
    public static boolean placeAllowedCheck(Block block) {
        return !RoadFeatureCompat.dontPlaceHere(block);
    }
}
