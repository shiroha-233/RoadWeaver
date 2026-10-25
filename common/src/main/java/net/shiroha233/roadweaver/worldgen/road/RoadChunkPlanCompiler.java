package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 道路计划编译器。编译阶段允许 O(road segments)，世界生成阶段只读取 stamp。
 */
public final class RoadChunkPlanCompiler {
    private static final int COLUMN_COUNT = 256;
    private static final int BANK_WIDTH = 2;
    private static final int FILL_DEPTH = 6;
    private static final float MAX_FILL = 0.5F;
    private static final float MAX_CARVE = 0.6F;

    private RoadChunkPlanCompiler() {
    }

    public static RoadChunkPlan compile(ChunkPos chunkPos,
                                        Collection<RoadData> roads,
                                        boolean includeBridgeSegments,
                                        int clearHeight,
                                        long revision) {
        if (chunkPos == null) {
            throw new IllegalArgumentException("chunkPos must not be null");
        }
        int safeClearHeight = Math.max(0, Math.min(16, clearHeight));
        List<RoadData> validRoads = validRoads(roads);
        if (validRoads.isEmpty()) {
            return RoadChunkPlan.empty(chunkPos, safeClearHeight, revision);
        }

        List<RasterSegment> rasterSegments = new ArrayList<>();
        List<RoadChunkSlice> slices = new ArrayList<>();
        boolean[] occupied = new boolean[COLUMN_COUNT];
        for (RoadData road : validRoads) {
            List<RoadSegmentPlacement> placements = road.roadSegmentList();
            if (placements == null || placements.size() < 2) continue;

            boolean[] bridgeMask = buildBridgeMask(placements, road.spans());
            List<Integer> localIndices = new ArrayList<>();
            int halfWidth = Math.max(1, (road.width() + 1) / 2);
            int chunkMinX = chunkPos.getMinBlockX();
            int chunkMinZ = chunkPos.getMinBlockZ();
            int chunkMaxX = chunkPos.getMaxBlockX();
            int chunkMaxZ = chunkPos.getMaxBlockZ();

            for (int i = 0; i < placements.size(); i++) {
                RoadSegmentPlacement placement = placements.get(i);
                if (placement == null || placement.middlePos() == null) continue;
                markOccupied(occupied, placement, chunkPos, halfWidth);
                if (intersectsChunk(placement, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
                    localIndices.add(i);
                }
            }
            if (!localIndices.isEmpty()) {
                addNeighborIndices(localIndices, placements.size());
                localIndices.sort(Comparator.naturalOrder());
                slices.add(new RoadChunkSlice(road, chunkPos, toIntArray(localIndices)));
            }

            List<Integer> targetY = road.targetY();
            for (int i = 0; i < placements.size() - 1; i++) {
                if (!isRenderable(placements.get(i)) || !isRenderable(placements.get(i + 1))) continue;
                boolean isBridge = bridgeMask != null && (bridgeMask[i] || bridgeMask[i + 1]);
                if (isBridge && !includeBridgeSegments) continue;

                BlockPos a = placements.get(i).middlePos();
                BlockPos b = placements.get(i + 1).middlePos();
                int y0 = targetAt(targetY, i, a.getY());
                int y1 = targetAt(targetY, i + 1, b.getY());
                int minX = Math.min(a.getX(), b.getX()) - halfWidth - BANK_WIDTH;
                int maxX = Math.max(a.getX(), b.getX()) + halfWidth + BANK_WIDTH;
                int minZ = Math.min(a.getZ(), b.getZ()) - halfWidth - BANK_WIDTH;
                int maxZ = Math.max(a.getZ(), b.getZ()) + halfWidth + BANK_WIDTH;
                if (maxX < chunkMinX || minX > chunkMaxX || maxZ < chunkMinZ || minZ > chunkMaxZ) {
                    continue;
                }
                rasterSegments.add(new RasterSegment(
                        a.getX(), a.getZ(), y0,
                        b.getX(), b.getZ(), y1,
                        halfWidth));
            }
        }

        if (rasterSegments.isEmpty()) {
            return RoadChunkPlan.of(chunkPos, slices,
                    RoadDensityStamp.empty(chunkPos, safeClearHeight, occupied), revision);
        }
        RoadDensityStamp stamp = compileStamp(chunkPos, rasterSegments, occupied, safeClearHeight);
        return RoadChunkPlan.of(chunkPos, slices, stamp, revision);
    }

    private static RoadDensityStamp compileStamp(ChunkPos chunkPos,
                                                  List<RasterSegment> segments,
                                                  boolean[] occupied,
                                                  int clearHeight) {
        int[] fillTargetY = new int[COLUMN_COUNT];
        int[] carveTargetY = new int[COLUMN_COUNT];
        Arrays.fill(fillTargetY, Integer.MIN_VALUE);
        Arrays.fill(carveTargetY, Integer.MIN_VALUE);
        float[] fillLateral = new float[COLUMN_COUNT];
        float[] carveLateral = new float[COLUMN_COUNT];
        float[] bestFill = new float[COLUMN_COUNT];
        float[] bestCarve = new float[COLUMN_COUNT];
        int[] fillY = new int[COLUMN_COUNT];
        int[] carveY = new int[COLUMN_COUNT];

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int index = localX | (localZ << 4);
                int x = minX + localX;
                int z = minZ + localZ;
                for (RasterSegment segment : segments) {
                    Projection projection = project(x, z, segment);
                    double target = segment.y0 + projection.t() * (segment.y1 - segment.y0);
                    int surfaceY = (int) Math.round(target);
                    double distance = Math.sqrt(projection.distanceSq());

                    double totalWidth = segment.halfWidth + BANK_WIDTH;
                    if (distance <= totalWidth) {
                        double inner = Math.max(0.0D, segment.halfWidth * 0.25D);
                        float lateral = (float) smoothFalloff(distance, inner, totalWidth);
                        lateral *= lateral;
                        if (lateral > bestFill[index]) {
                            bestFill[index] = lateral;
                            fillY[index] = surfaceY;
                        }
                    }

                    if (distance <= segment.halfWidth && 1.0F > bestCarve[index]) {
                        bestCarve[index] = 1.0F;
                        carveY[index] = surfaceY;
                    }
                }
            }
        }

        float[] fillDepth = new float[FILL_DEPTH + 1];
        for (int i = 0; i < fillDepth.length; i++) {
            double depthT = Mth.clamp((i + 0.5D) / (FILL_DEPTH + 1.0D), 0.0D, 1.0D);
            fillDepth[i] = MAX_FILL * (float) Math.pow(1.0D - depthT, 2.2D);
        }
        float[] carveDepth = new float[clearHeight + 1];
        for (int i = 0; i < carveDepth.length; i++) {
            double clearT = Mth.clamp(i / (double) Math.max(1, clearHeight), 0.0D, 1.0D);
            carveDepth[i] = -MAX_CARVE * (float) Math.pow(1.0D - clearT, 1.8D);
        }

        for (int i = 0; i < COLUMN_COUNT; i++) {
            if (bestFill[i] > 0.0F) {
                fillTargetY[i] = fillY[i];
                fillLateral[i] = bestFill[i];
            }
            if (bestCarve[i] > 0.0F) {
                carveTargetY[i] = carveY[i];
                carveLateral[i] = bestCarve[i];
            }
        }
        return new RoadDensityStamp(chunkPos, fillTargetY, carveTargetY,
                fillLateral, carveLateral, fillDepth, carveDepth, occupied, true);
    }

    private static void markOccupied(boolean[] occupied,
                                     RoadSegmentPlacement placement,
                                     ChunkPos chunkPos,
                                     int halfWidth) {
        if (placement.positions() != null && !placement.positions().isEmpty()) {
            for (BlockPos position : placement.positions()) markOccupied(occupied, position, chunkPos);
        }
        BlockPos middle = placement.middlePos();
        if (middle == null) return;
        int radius = Math.min(16, Math.max(1, halfWidth + 1));
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int localCenterX = middle.getX() - chunkMinX;
        int localCenterZ = middle.getZ() - chunkMinZ;
        if (localCenterX < -radius || localCenterX >= 16 + radius
                || localCenterZ < -radius || localCenterZ >= 16 + radius) return;
        int radiusSquared = radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radiusSquared) {
                    markOccupied(occupied, middle.getX() + dx, middle.getZ() + dz, chunkPos);
                }
            }
        }
    }

    private static void markOccupied(boolean[] occupied, BlockPos position, ChunkPos chunkPos) {
        if (position == null) return;
        markOccupied(occupied, position.getX(), position.getZ(), chunkPos);
    }

    private static void markOccupied(boolean[] occupied, int blockX, int blockZ, ChunkPos chunkPos) {
        int localX = blockX - chunkPos.getMinBlockX();
        int localZ = blockZ - chunkPos.getMinBlockZ();
        if ((localX | localZ) < 0 || localX >= 16 || localZ >= 16) return;
        occupied[localX | (localZ << 4)] = true;
    }

    private static List<RoadData> validRoads(Collection<RoadData> roads) {
        if (roads == null || roads.isEmpty()) return List.of();
        List<RoadData> valid = new ArrayList<>(roads.size());
        for (RoadData road : roads) {
            if (road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) continue;
            valid.add(road);
        }
        return List.copyOf(valid);
    }

    private static boolean intersectsChunk(RoadSegmentPlacement placement,
                                           int minX, int minZ, int maxX, int maxZ) {
        if (placement.positions() != null) {
            for (BlockPos position : placement.positions()) {
                if (position != null && position.getX() >= minX && position.getX() <= maxX
                        && position.getZ() >= minZ && position.getZ() <= maxZ) {
                    return true;
                }
            }
        }
        BlockPos middle = placement.middlePos();
        return middle != null && middle.getX() >= minX && middle.getX() <= maxX
                && middle.getZ() >= minZ && middle.getZ() <= maxZ;
    }

    private static void addNeighborIndices(List<Integer> indices, int size) {
        Set<Integer> expanded = new HashSet<>(indices);
        for (int index : indices) {
            for (int delta = -2; delta <= 2; delta++) {
                int candidate = index + delta;
                if (candidate >= 0 && candidate < size) expanded.add(candidate);
            }
        }
        indices.clear();
        indices.addAll(expanded);
    }

    private static boolean isRenderable(RoadSegmentPlacement placement) {
        return placement != null && placement.middlePos() != null
                && placement.positions() != null && !placement.positions().isEmpty();
    }

    private static int targetAt(List<Integer> targetY, int index, int fallback) {
        return targetY != null && index >= 0 && index < targetY.size() && targetY.get(index) != null
                ? targetY.get(index) : fallback;
    }

    private static boolean[] buildBridgeMask(List<RoadSegmentPlacement> segments, List<RoadSpan> spans) {
        if (spans == null || spans.isEmpty()) return null;
        Map<Long, Integer> indexByPosition = new HashMap<>(segments.size() * 2);
        for (int i = 0; i < segments.size(); i++) {
            RoadSegmentPlacement segment = segments.get(i);
            if (segment != null && segment.middlePos() != null) {
                indexByPosition.put(segment.middlePos().asLong(), i);
            }
        }
        boolean[] mask = new boolean[segments.size()];
        boolean found = false;
        for (RoadSpan span : spans) {
            if (span == null || span.type() != SpanType.BRIDGE || span.start() == null || span.end() == null) continue;
            Integer start = indexByPosition.get(span.start().asLong());
            Integer end = indexByPosition.get(span.end().asLong());
            if (start == null || end == null) continue;
            int from = Math.max(0, Math.min(start, end));
            int to = Math.min(mask.length - 1, Math.max(start, end));
            for (int i = from; i <= to; i++) mask[i] = true;
            found = true;
        }
        return found ? mask : null;
    }

    private static Projection project(int x, int z, RasterSegment segment) {
        double dx = segment.x1 - segment.x0;
        double dz = segment.z1 - segment.z0;
        double lenSq = dx * dx + dz * dz;
        double t = lenSq < 1.0E-9D
                ? 0.0D
                : Mth.clamp(((x - segment.x0) * dx + (z - segment.z0) * dz) / lenSq, 0.0D, 1.0D);
        double px = segment.x0 + t * dx;
        double pz = segment.z0 + t * dz;
        double ddx = x - px;
        double ddz = z - pz;
        return new Projection(t, ddx * ddx + ddz * ddz);
    }

    private static double smoothFalloff(double distance, double fullRadius, double zeroRadius) {
        if (distance <= fullRadius) return 1.0D;
        if (distance >= zeroRadius) return 0.0D;
        double t = (distance - fullRadius) / (zeroRadius - fullRadius);
        double x = Mth.clamp(t, 0.0D, 1.0D);
        double smooth = x * x * x * (x * (x * 6.0D - 15.0D) + 10.0D);
        return 1.0D - smooth;
    }

    private record Projection(double t, double distanceSq) {
    }

    private record RasterSegment(int x0, int z0, int y0,
                                 int x1, int z1, int y1,
                                 int halfWidth) {
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}
