package net.shiroha233.roadweaver.worldgen.road.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.worldgen.road.decoration.system.AboveColumnClearer;

/**
 * 浮标建造器
 */
public final class BuoyBuilder {
    private BuoyBuilder() {}

    private static final BlockState BASE = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState POST = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.TORCH.defaultBlockState();

    public static void placeBuoy(WorldGenLevel world,
                                BlockPos center,
                                int seaLevel,
                                RandomSource random,
                                ModConfig cfg) {
        if (world == null || center == null) return;
        BlockPos basePos = new BlockPos(center.getX(), seaLevel, center.getZ());
        BlockPos postPos = basePos.above();
        BlockPos lightPos = basePos.above(2);

        world.setBlock(basePos, BASE, 3);
        world.setBlock(postPos, POST, 3);
        world.setBlock(lightPos, LIGHT, 3);

        AboveColumnClearer.clearAboveColumn(world, lightPos.above(), cfg);
    }
}
