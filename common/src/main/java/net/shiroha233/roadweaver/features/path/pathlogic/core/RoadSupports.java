package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.shiroha233.roadweaver.core.model.WoodAssets;
import net.shiroha233.roadweaver.features.path.decoration.material.WoodSelector;

import java.util.List;

/**
 * The terrain is raised under a road only a few blocks deep, so where a road crosses a deeper dip or runs past a cliff
 * edge its surface ends up on a thin sheet of dirt over open air. Gaps that do not open to the side (a cave under the
 * road) are left alone. Gaps of up to {@link #MAX_EMBANKMENT_GAP} blocks are
 * filled with dirt like a low embankment. Over a deeper gap the road becomes a deck: wooden planks, or stone bricks on
 * a stone road, with the hanging dirt under it removed and posts down to the ground every few blocks, fences where
 * the deck is low and stone brick pillars where it is high.
 */
public final class RoadSupports {
    private static final int SHEET_SEARCH = 6;
    private static final int MAX_EMBANKMENT_GAP = 2;
    private static final int MAX_DROP = 32;
    private static final int MAX_FENCE_POST = 4;
    private static final int POST_SPACING = 3;
    /** How far beside the road the side of a platform is looked for. */
    private static final int SIDE_REACH = 3;

    private RoadSupports() {}

    /**
     * @param surface   the road surface block (the one walked on)
     * @param stoneRoad whether the road is paved in stone, which gets a stone brick deck instead of planks
     */
    public static void support(WorldGenLevel world, BlockPos surface, boolean stoneRoad) {
        BlockState top = world.getBlockState(surface);
        if (top.isAir() || !top.getFluidState().isEmpty()) return;

        // First open block under the surface, within the thin sheet the terrain fill leaves.
        int sheet = 0;
        BlockPos gapTop = null;
        for (int k = 1; k <= SHEET_SEARCH; k++) {
            BlockPos p = surface.below(k);
            if (isOpen(world, p)) {
                gapTop = p;
                break;
            }
            sheet = k;
        }
        if (gapTop == null) return;

        int gap = 0;
        BlockPos p = gapTop;
        while (gap < MAX_DROP && isOpen(world, p)) {
            gap++;
            p = p.below();
        }
        if (gap >= MAX_DROP) return;

        // A road over a cave keeps its ground: only a gap that opens to the side, where the sheet hangs over a dip or
        // a slope, is a platform anyone can see.
        if (!openToTheSide(world, surface, gapTop)) return;

        if (gap <= MAX_EMBANKMENT_GAP) {
            for (int k = 0; k < gap; k++) world.setBlock(gapTop.below(k), Blocks.DIRT.defaultBlockState(), 3);
            return;
        }

        boolean stone = stoneRoad;
        WoodAssets wood = stone ? null : WoodSelector.forBiome(world, surface);
        BlockState deck = stone ? Blocks.STONE_BRICKS.defaultBlockState() : wood.planks().defaultBlockState();
        world.setBlock(surface, deck, 3);
        for (int k = 1; k <= sheet; k++) world.setBlock(surface.below(k), Blocks.AIR.defaultBlockState(), 3);

        BlockPos above = surface.above();
        BlockState slab = world.getBlockState(above);
        if (slab.getBlock() instanceof SlabBlock) {
            Block deckSlab = stone ? Blocks.STONE_BRICK_SLAB : slabOf(wood.planks());
            if (deckSlab != null) world.setBlock(above, deckSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        }

        if (Math.floorMod(surface.getX(), POST_SPACING) != 0 || Math.floorMod(surface.getZ(), POST_SPACING) != 0) return;
        int height = sheet + gap;
        BlockState post = height <= MAX_FENCE_POST && wood != null
                ? wood.fence().defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
        for (int k = 1; k <= height; k++) {
            BlockPos at = surface.below(k);
            if (!isOpen(world, at)) break;
            world.setBlock(at, post, 3);
        }
    }

    /** Whether most of a road's surface blocks are stone or cobblestone (gravel does not count). */
    public static boolean isStoneRoad(List<BlockState> surface) {
        if (surface == null || surface.isEmpty()) return false;
        int stone = 0;
        for (BlockState state : surface) {
            if (state.is(BlockTags.STONE_BRICKS) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                    || state.is(Blocks.STONE) || state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.ANDESITE)
                    || state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.TUFF)) {
                stone++;
            }
        }
        return stone * 2 > surface.size();
    }

    /** True when, within a few blocks beside the road, a column is open from the road surface down to the gap. */
    private static boolean openToTheSide(WorldGenLevel world, BlockPos surface, BlockPos gapTop) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int d = 1; d <= SIDE_REACH; d++) {
                BlockPos column = surface.relative(dir, d);
                boolean open = true;
                for (int y = surface.getY(); y >= gapTop.getY() && open; y--) {
                    open = isOpen(world, column.atY(y));
                }
                if (open) return true;
            }
        }
        return false;
    }

    private static boolean isOpen(WorldGenLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || state.canBeReplaced())
                && !state.isFaceSturdy(world, pos, Direction.UP);
    }

    private static Block slabOf(Block planks) {
        String path = BuiltInRegistries.BLOCK.getKey(planks).getPath();
        if (!path.endsWith("_planks")) return null;
        ResourceLocation id = ResourceLocation.withDefaultNamespace(path.substring(0, path.length() - "_planks".length()) + "_slab");
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }
}
