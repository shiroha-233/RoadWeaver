package net.shiroha233.roadweaver.persistence.chunk;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.List;

public final class RoadFingerprint {
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private RoadFingerprint() {}

    public static long compute(RoadData road) {
        if (road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) return 0L;
        if (road.hasOwnerPair()) {
            long firstOwner = Math.min(road.ownerA2dKey(), road.ownerB2dKey());
            long secondOwner = Math.max(road.ownerA2dKey(), road.ownerB2dKey());
            return nonZero(mix(mix(mix(FNV_OFFSET, 0x524F41444F574E52L), firstOwner), secondOwner));
        }

        BlockPos first = firstPosition(road.roadSegmentList());
        BlockPos last = lastPosition(road.roadSegmentList());
        if (first == null || last == null) return 0L;
        boolean forward = compare(first, last) <= 0;
        long hash = mix(mix(FNV_OFFSET, road.width()), road.roadType());
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        for (int offset = 0; offset < segments.size(); offset++) {
            int index = forward ? offset : segments.size() - 1 - offset;
            RoadSegmentPlacement segment = segments.get(index);
            if (segment != null && segment.middlePos() != null) {
                hash = mix(hash, segment.middlePos().asLong());
            }
        }
        return nonZero(hash);
    }

    private static long mix(long hash, long value) {
        long mixed = hash;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xFFL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private static long nonZero(long hash) {
        return hash == 0L ? FNV_OFFSET : hash;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int z = Integer.compare(first.getZ(), second.getZ());
        return z != 0 ? z : Integer.compare(first.getY(), second.getY());
    }

    private static BlockPos firstPosition(List<RoadSegmentPlacement> segments) {
        for (RoadSegmentPlacement segment : segments) {
            if (segment != null && segment.middlePos() != null) return segment.middlePos();
        }
        return null;
    }

    private static BlockPos lastPosition(List<RoadSegmentPlacement> segments) {
        for (int index = segments.size() - 1; index >= 0; index--) {
            RoadSegmentPlacement segment = segments.get(index);
            if (segment != null && segment.middlePos() != null) return segment.middlePos();
        }
        return null;
    }
}
