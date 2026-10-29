package net.shiroha233.roadweaver.worldgen.road.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.util.Line;

public final class BridgeSegmentPlannerNew {
    private BridgeSegmentPlannerNew() {
    }

    public static boolean processSegment(
            WorldGenLevel world,
            Line line,
            RoadSegmentPlacement seg,
            BlockPos middle,
            BlockPos prev) {
        double bridgeLength = line.getTotalLength();

        Holder<Biome> biome = world.getBiome(middle);
        var bridge = BridgeTemplateStructureRegistry.choose(world.getLevel(), (int) bridgeLength, biome);

        if (bridge == null) {
            return false;
        }

        int minX = Math.min(prev.getX(), middle.getX()) - 10;
        int maxX = Math.max(prev.getX(), middle.getX()) + 10;
        int minZ = Math.min(prev.getZ(), middle.getZ()) - 10;
        int maxZ = Math.max(prev.getZ(), middle.getZ()) + 10;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec3 testPos = new Vec3(x, middle.getY(), z);
                Line.Frame frame = line.getFrame(testPos);

                Vec3 vec0 = testPos.subtract(frame.closestPoint);
                double testX = vec0.dot(frame.tangent0);
                double testZ = vec0.dot(frame.binormal0);

                if (Math.abs(testX) > 2 || !bridge.isInVoxelGrid(0, 0, testZ)) {
                    continue;
                }

                for (int y = middle.getY() + 20; y >= middle.getY() - 60; y--) {
                    Vec3 vec = new Vec3(x, y, z).subtract(frame.closestPoint);

                    double localX = Math.min(Math.max(0, frame.globalT), 1) * bridgeLength;
                    double localY = vec.dot(frame.normal0);
                    double localZ = vec.dot(frame.binormal0);

                    if (localX >= bridge.getStartLength() && localX <= bridgeLength - bridge.getEndLength()) {
                        localX = bridge.getStartLength() + (localX % bridge.getDeckLength());
                    } else if (localX > bridgeLength - bridge.getEndLength()) {
                        localX = bridge.getTotalLength() - (bridgeLength - localX);
                    }

                    BlockState state = bridge.getBlock(localX, localY, localZ);
                    if (state != null && !state.is(Blocks.AIR)) {
                        BlockPos pos = new BlockPos(x, y, z);
                        world.setBlock(pos, state, 3);
                        BlockState updatedState = Block.updateFromNeighbourShapes(world.getBlockState(pos), world, pos);
                        world.setBlock(pos, updatedState, 3);
                    }

                    BlockPos cur = new BlockPos(x, y - 1, z);
                    if (world.getBlockState(cur).isFaceSturdy(world, cur, Direction.UP)) {
                        break;
                    }
                }
            }
        }

        return true;
    }
}
