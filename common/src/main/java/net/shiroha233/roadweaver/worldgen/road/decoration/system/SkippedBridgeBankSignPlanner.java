package net.shiroha233.roadweaver.worldgen.road.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.worldgen.road.decoration.base.Decoration;
import net.shiroha233.roadweaver.worldgen.road.decoration.types.SeaQuestionSignDecoration;
import net.shiroha233.roadweaver.worldgen.road.StructureAvoidanceService;

import java.util.Set;

/**
 * 跳过桥梁岸边路牌规划器
 */
public final class SkippedBridgeBankSignPlanner {
    private SkippedBridgeBankSignPlanner() {}

    private static final int SIDE_OFFSET = 2;

    public static void addIfSkippedBridgeBank(WorldGenLevel world,
                                             Set<Decoration> out,
                                             BlockPos placePos,
                                             BlockPos nextPos,
                                             BlockPos prevPos,
                                             int roadWidth,
                                             boolean[] skipSegments,
                                             int i) {
        if (skipSegments == null || i < 0 || i >= skipSegments.length) return;
        if (skipSegments[i]) return;

        boolean beforeSkip = (i + 1 < skipSegments.length) && skipSegments[i + 1];
        boolean afterSkip = (i - 1 >= 0) && skipSegments[i - 1];
        if (!beforeSkip && !afterSkip) return;

        int dx = nextPos.getX() - prevPos.getX();
        int dz = nextPos.getZ() - prevPos.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        int nx = len != 0 ? (int) Math.round(dx / len) : 0;
        int nz = len != 0 ? (int) Math.round(dz / len) : 0;
        Vec3i dir = new Vec3i(nx, 0, nz);
        Vec3i ortho = new Vec3i(-dir.getZ(), 0, dir.getX());

        int halfWidth = Math.max(1, roadWidth / 2);
        int sideOffset = Math.max(SIDE_OFFSET, halfWidth + 1);

        boolean isStart = afterSkip;
        BlockPos shifted = isStart
                ? placePos.offset(ortho.getX() * sideOffset, 0, ortho.getZ() * sideOffset)
                : placePos.offset(-ortho.getX() * sideOffset, 0, -ortho.getZ() * sideOffset);

        if (StructureAvoidanceService.shouldAvoid(world, shifted)) return;
        out.add(new SeaQuestionSignDecoration(shifted, ortho, world, isStart));
    }
}
