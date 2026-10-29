package net.shiroha233.roadweaver.worldgen.road.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.worldgen.road.decoration.system.AboveColumnClearer;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

/**
 * 桥梁建造器
 */
public final class BridgeBuilder {
    private BridgeBuilder() {}

    private static final BlockState DECK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState PIER = Blocks.STONE_BRICKS.defaultBlockState();

    public static void placeSegment(WorldGenLevel world,
                                    RoadSegmentPlacement seg,
                                    BlockPos middle,
                                    BlockPos prev,
                                    BlockPos next,
                                    int roadWidth,
                                    int deckY,
                                    int segmentIndex,
                                    RandomSource random,
                                    ModConfig cfg,
                                    boolean placePier,
                                    boolean placeRail) {
        
        // 1) 直接使用段落自身的positions来放置桥面，确保与普通道路宽度完全一致
        //    同时对桥面上方进行清障处理，防止冰刺/地形挡住桥面
        for (BlockPos widthPos : seg.positions()) {
            BlockPos deckPos = new BlockPos(widthPos.getX(), deckY, widthPos.getZ());
            world.setBlock(deckPos, DECK, 3);
            // 注意：AboveColumnClearer 约定传入的是"路面上方一格"，否则会把路面本身清掉
            AboveColumnClearer.clearAboveColumn(world, deckPos.above(), cfg);
        }

        // 2) 桥墩（按段间隔）
        if (placePier) {
            int interval = Math.max(3, cfg.bridgePierInterval());
            if (segmentIndex % interval == 0) {
                placePierUnder(world, middle.getX(), middle.getZ(), deckY - 1, cfg.bridgePierMaxHeight(), cfg.bridgePierWidth());
            }
        }
    }

    private static void placePierUnder(WorldGenLevel world, int x, int z, int fromY, int maxHeight, int pierWidth) {
        int minY = world.getMinBuildHeight();
        int half = Math.max(0, pierWidth - 1);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int y = fromY;
                int h = 0;
                while (y >= minY && h < maxHeight) {
                    BlockPos cur = new BlockPos(x + dx, y, z + dz);
                    // 若当前方块可承重，则停止在其上方，不再继续向下
                    if (world.getBlockState(cur).isFaceSturdy(world, cur, Direction.UP)) {
                        break;
                    }
                    world.setBlock(cur, PIER, 3);
                    y--;
                    h++;
                }
            }
        }
    }
}
