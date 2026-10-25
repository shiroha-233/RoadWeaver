package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.path.decoration.system.SurfacePlacementUtil;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadHeightInterpolator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路面铺设器
 */
public final class SegmentPaver {
    private SegmentPaver() {}

    public static void paveSegment(WorldGenLevel world,
            RoadSegmentPlacement seg,
            int segmentIndex,
            List<BlockPos> centers,
            int[] targetY,
            int roadType,
            List<BlockState> materials,
            List<BlockState> slabMaterials,
            RandomSource random,
            ModConfig cfg) {
        List<BlockPos> positions = seg.positions();
        if (positions.isEmpty())
            return;

        int[] heights = RoadHeightInterpolator.batchInterpolate(positions, segmentIndex, centers, targetY);
        Map<ResourceLocation, NaturalMaterials> naturalMaterials = roadType == 1 ? new HashMap<>() : Map.of();

        for (int i = 0; i < positions.size(); i++) {
            BlockPos widthBlock = positions.get(i);
            int y = heights[i];
            BlockPos pos = new BlockPos(widthBlock.getX(), y, widthBlock.getZ());

            if (StructureAvoidanceService.shouldAvoid(world, pos)) {
                continue;
            }

            List<BlockState> baseMats;
            List<BlockState> slabs;
            if (roadType == 1) {
                ResourceLocation biomeId = world.getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
                NaturalMaterials selected = naturalMaterials.computeIfAbsent(
                        biomeId, ignored -> resolveNaturalMaterials(world, pos, biomeId));
                baseMats = selected.base();
                slabs = selected.slabs();
            } else if (materials != null && !materials.isEmpty()) {
                baseMats = materials;
                slabs = slabMaterials;
            } else {
                baseMats = List.of();
                slabs = List.of();
            }

            if (!baseMats.isEmpty()) {
                SurfacePlacementUtil.placeOnSurface(world, pos, baseMats, roadType, random, cfg);
            }

            if (slabs != null && !slabs.isEmpty()) {
                if (shouldPlaceSlab(widthBlock.getX(), widthBlock.getZ(), y,
                        segmentIndex, centers, targetY)) {
                    BlockState slabState = slabs.get(random.nextInt(slabs.size()));
                    if (slabState.getBlock() instanceof SlabBlock) {
                        slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    }
                    world.setBlock(pos, slabState, 3);
                }
            }

            if (!baseMats.isEmpty()) {
                RoadSupports.support(world, pos.below(), RoadSupports.isStoneRoad(baseMats));
            }
        }
    }

    private static boolean shouldPlaceSlab(int x, int z, int currentY,
            int segmentIndex, List<BlockPos> centers, int[] targetY) {
        int yAhead = RoadHeightInterpolator.getInterpolatedYNear(x + 1, z, centers, targetY, segmentIndex, 20);
        int yBehind = RoadHeightInterpolator.getInterpolatedYNear(x - 1, z, centers, targetY, segmentIndex, 20);
        int yLeft = RoadHeightInterpolator.getInterpolatedYNear(x, z + 1, centers, targetY, segmentIndex, 20);
        int yRight = RoadHeightInterpolator.getInterpolatedYNear(x, z - 1, centers, targetY, segmentIndex, 20);

        boolean needsSlabX = (yAhead > currentY && yBehind >= currentY)
                || (yBehind > currentY && yAhead >= currentY);
        boolean needsSlabZ = (yLeft > currentY && yRight >= currentY)
                || (yRight > currentY && yLeft >= currentY);

        return needsSlabX || needsSlabZ;
    }

    private static NaturalMaterials resolveNaturalMaterials(WorldGenLevel world,
                                                             BlockPos pos,
                                                             ResourceLocation biomeId) {
        PresetService.PresetDef preset = PresetService.findNaturalPresetForBiome(biomeId);
        if (preset != null) {
            return new NaturalMaterials(
                    PresetService.toBlockStatesFromIdsAllowEmpty(preset.materials()),
                    PresetService.toBlockStatesFromIdsAllowEmpty(preset.slabMaterials()));
        }
        return new NaturalMaterials(
                net.shiroha233.roadweaver.features.path.decoration.material.BiomeRoadMaterialSelector
                        .forBiome(world, pos),
                List.of());
    }

    private record NaturalMaterials(List<BlockState> base, List<BlockState> slabs) {
    }
}
