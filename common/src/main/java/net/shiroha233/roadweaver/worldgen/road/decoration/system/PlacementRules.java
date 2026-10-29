package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.minecraft.world.level.block.Block;
import net.shiroha233.roadweaver.worldgen.road.decoration.compat.RoadFeatureCompat;

/**
 * 放置规则
 */
public final class PlacementRules {
    private PlacementRules() {}

    public static boolean placeAllowedCheck(Block block) {
        return !RoadFeatureCompat.dontPlaceHere(block);
    }
}
