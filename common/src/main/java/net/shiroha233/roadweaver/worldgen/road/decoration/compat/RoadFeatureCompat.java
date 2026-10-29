package net.shiroha233.roadweaver.worldgen.road.decoration.compat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * 道路生成兼容层
 */
public final class RoadFeatureCompat {
    private RoadFeatureCompat() {}

    private static final Set<Block> DONT_PLACE = new HashSet<>();
    static {
        DONT_PLACE.add(Blocks.TALL_SEAGRASS);
        DONT_PLACE.add(Blocks.MANGROVE_ROOTS);
    }

    public static boolean dontPlaceHere(Block b) {
        return DONT_PLACE.contains(b);
    }
}
