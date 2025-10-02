package net.countered.settlementroads.features.decoration;

import net.countered.settlementroads.features.decoration.util.WoodSelector;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

import java.util.Iterator;
import java.util.Set;

public class RoadStructures {

    public static void tryPlaceDecorations(Set<Decoration> roadDecorationPlacementPositions) {
        if (roadDecorationPlacementPositions.isEmpty()) {
            return;
        }
        Iterator<Decoration> iterator = roadDecorationPlacementPositions.iterator();
        while (iterator.hasNext()) {
            Decoration roadDecoration = iterator.next();
            if (roadDecoration != null) {
                // place lantern
                if (roadDecoration instanceof LamppostDecoration lamppostDecoration) {
                    lamppostDecoration.setWoodType(WoodSelector.forBiome(lamppostDecoration.getWorld(), lamppostDecoration.getPos()));
                    lamppostDecoration.place();
                }
                // place distance sign
                if (roadDecoration instanceof DistanceSignDecoration distanceSignDecoration) {
                    distanceSignDecoration.setWoodType(WoodSelector.forBiome(distanceSignDecoration.getWorld(), distanceSignDecoration.getPos()));
                    distanceSignDecoration.place();
                }
                // place waypoint
                if (roadDecoration instanceof FenceWaypointDecoration fenceWaypointDecoration) {
                    fenceWaypointDecoration.setWoodType(WoodSelector.forBiome(fenceWaypointDecoration.getWorld(), fenceWaypointDecoration.getPos()));
                    fenceWaypointDecoration.place();
                }
                iterator.remove();
            }
        }
    }

    public static void placeBuoy(StructureWorldAccess worldAccess, BlockPos surfacePos) {
        worldAccess.setBlockState(surfacePos.down(), Blocks.SPRUCE_PLANKS.getDefaultState(), 3);
        worldAccess.setBlockState(surfacePos, Blocks.SPRUCE_FENCE.getDefaultState(), 3);
    }
}
