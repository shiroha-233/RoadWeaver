/* 文件职责：对已持久化道路执行端点吸附与几何后处理。 */
package net.shiroha233.roadweaver.postprocess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.persistence.RoadReplacement;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 道路吸附后处理服务：检测近距离平行路段，将副路吸附到主路上，
 * 重叠段复用主路 segment，过渡段合并双路 positions 形成三叉路口
 */
public final class RoadSnapService {
    private RoadSnapService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int SNAP_THRESHOLD = RoadConstants.ROAD_SNAP_THRESHOLD;
    private static final int SPLIT_THRESHOLD = RoadConstants.ROAD_SNAP_SPLIT_THRESHOLD;
    private static final int MIN_RUN = RoadConstants.ROAD_SNAP_MIN_RUN_LENGTH;
    private static final int GRID_SHIFT = RoadConstants.ROAD_SNAP_GRID_SHIFT;
    private static final int TRANSITION = RoadConstants.ROAD_SNAP_TRANSITION_SEGMENTS;
    private static final long SNAP_DIST2 = (long) SNAP_THRESHOLD * SNAP_THRESHOLD;
    private static final long SPLIT_DIST2 = (long) SPLIT_THRESHOLD * SPLIT_THRESHOLD;

    public static void snapAllRoads(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        if (level == null) return;
        int margin = SPLIT_THRESHOLD;
        List<RoadData> roads = RoadShardStorage.queryRect(level,
                minX - margin, minZ - margin, maxX + margin, maxZ + margin);
        if (roads == null || roads.size() < 2) return;
        snapRoadList(level, roads);
    }

    public static void snapAroundConnection(ServerLevel level, BlockPos from, BlockPos to) {
        if (level == null || from == null || to == null) return;
        int minX = Math.min(from.getX(), to.getX());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int margin = SPLIT_THRESHOLD * 2;
        List<RoadData> roads = RoadShardStorage.queryRect(level,
                minX - margin, minZ - margin, maxX + margin, maxZ + margin);
        if (roads == null || roads.size() < 2) return;
        snapRoadList(level, roads);
    }

    public static CompletableFuture<Void> snapAroundConnectionAsync(ServerLevel level, BlockPos from, BlockPos to) {
        if (level == null || from == null || to == null) {
            return CompletableFuture.completedFuture(null);
        }
        int minX = Math.min(from.getX(), to.getX());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxZ = Math.max(from.getZ(), to.getZ());
        int margin = SPLIT_THRESHOLD * 2;

        // 吸附计算与道路替换均为内存/持久层操作（mutationLock 保护），调度至后台池执行，
        // 不再占用主线程；结果广播由 MapPatchService 自行回到主线程发包
        return RoadShardStorage.queryRectAsync(level,
                minX - margin, minZ - margin, maxX + margin, maxZ + margin)
                .thenAcceptAsync(roads -> {
                    if (roads == null || roads.size() < 2) return;
                    snapRoadList(level, roads);
                }, ThreadPoolManager.roleExecutor(ThreadPoolManager.TaskRole.POSTPROCESS));
    }

    private static void snapRoadList(ServerLevel level, List<RoadData> roads) {
        long[] fingerprints = new long[roads.size()];
        for (int i = 0; i < roads.size(); i++) {
            fingerprints[i] = RoadShardStorage.computeFingerprint(roads.get(i));
        }

        boolean[] modified = new boolean[roads.size()];
        RoadData[] current = roads.toArray(new RoadData[0]);

        Integer[] indices = new Integer[current.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> {
            int sa = current[a].roadSegmentList() != null ? current[a].roadSegmentList().size() : 0;
            int sb = current[b].roadSegmentList() != null ? current[b].roadSegmentList().size() : 0;
            return Integer.compare(sb, sa);
        });

        for (int pi = 0; pi < indices.length; pi++) {
            int primaryIdx = indices[pi];
            RoadData primary = current[primaryIdx];
            if (primary.roadSegmentList() == null || primary.roadSegmentList().size() < MIN_RUN) continue;

            SegmentGrid primaryGrid = buildGrid(primary);

            for (int si = pi + 1; si < indices.length; si++) {
                int secondaryIdx = indices[si];
                RoadData secondary = current[secondaryIdx];
                if (secondary.roadSegmentList() == null || secondary.roadSegmentList().size() < MIN_RUN) continue;

                RoadData snapped = trySnap(primary, primaryGrid, secondary);
                if (snapped != null) {
                    current[secondaryIdx] = snapped;
                    modified[secondaryIdx] = true;
                }
            }
        }

        List<RoadReplacement> replacements = new ArrayList<>();
        for (int i = 0; i < current.length; i++) {
            if (modified[i]) {
                replacements.add(new RoadReplacement(fingerprints[i], current[i]));
            }
        }
        if (!replacements.isEmpty()) {
            try {
                RoadShardStorage.replaceRoads(level, replacements);
            } catch (Exception e) {
                LOGGER.error("吸附后批量更新道路失败", e);
            }
        }

    }

    private static RoadData trySnap(RoadData primary, SegmentGrid primaryGrid, RoadData secondary) {
        List<RoadSegmentPlacement> secSegs = secondary.roadSegmentList();
        List<RoadSegmentPlacement> priSegs = primary.roadSegmentList();
        int n = secSegs.size();

        int[] snapTarget = markSnapTargets(secSegs, priSegs, primaryGrid);
        int snappedCount = 0;
        for (int t : snapTarget) if (t >= 0) snappedCount++;
        if (snappedCount < MIN_RUN) return null;

        List<int[]> runs = extractRuns(snapTarget, n);
        if (runs.isEmpty()) return null;

        List<RoadSegmentPlacement> newSegs = new ArrayList<>(secSegs);
        List<Integer> origTargetY = secondary.targetY();
        List<Integer> priTargetY = primary.targetY();
        int[] newTargetY = new int[n];
        for (int i = 0; i < n; i++) {
            newTargetY[i] = (origTargetY != null && i < origTargetY.size())
                    ? origTargetY.get(i) : secSegs.get(i).middlePos().getY();
        }

        boolean anySnapped = false;

        for (int[] run : runs) {
            int start = run[0], end = run[1];
            int runLen = end - start + 1;
            int trans = Math.min(TRANSITION, (runLen + 1) / 2);

            for (int i = start; i <= end; i++) {
                int targetIdx = snapTarget[i];
                if (targetIdx < 0 || targetIdx >= priSegs.size()) continue;

                RoadSegmentPlacement origSeg = secSegs.get(i);
                RoadSegmentPlacement priSeg = priSegs.get(targetIdx);
                int fromStart = i - start;
                int fromEnd = end - i;

                double ratio = computeRatio(fromStart, fromEnd, trans);

                if (ratio >= 1.0) {
                    newSegs.set(i, new RoadSegmentPlacement(priSeg.middlePos(), priSeg.positions()));
                } else {
                    newSegs.set(i, buildJunctionSegment(origSeg, priSeg, ratio));
                }

                int priY = (priTargetY != null && targetIdx < priTargetY.size())
                        ? priTargetY.get(targetIdx) : priSeg.middlePos().getY();
                newTargetY[i] = (int) Math.round(newTargetY[i] + (priY - newTargetY[i]) * ratio);
                anySnapped = true;
            }
        }

        if (!anySnapped) return null;

        List<Integer> targetYList = new ArrayList<>(n);
        for (int v : newTargetY) targetYList.add(v);

        List<RoadSegmentPlacement> finalSegs = newSegs;
        List<Integer> finalTargetY = targetYList;
        List<RoadSpan> finalSpans = secondary.spans();
        TruncateResult truncated = tryTruncateSharedEndpoint(primary, secondary, runs, priSegs, priTargetY, snapTarget, newSegs, targetYList);
        if (truncated != null) {
            finalSegs = truncated.segments();
            finalTargetY = truncated.targetY();
            finalSpans = filterSpansByRetainedSegments(secondary.spans(), finalSegs);
        }

        return new RoadData(
                secondary.width(), secondary.roadType(),
                secondary.materials(), secondary.slabMaterials(),
                finalSegs, finalSpans, finalTargetY,
                secondary.ownerA2dKey(), secondary.ownerB2dKey()
        );
    }

    private static int[] markSnapTargets(List<RoadSegmentPlacement> secSegs,
                                         List<RoadSegmentPlacement> priSegs,
                                         SegmentGrid primaryGrid) {
        int n = secSegs.size();
        int[] snapTarget = new int[n];
        Arrays.fill(snapTarget, -1);
        boolean snapping = false;

        for (int i = 0; i < n; i++) {
            BlockPos secPos = secSegs.get(i).middlePos();
            NearestResult nearest = primaryGrid.findNearest(secPos);

            if (nearest == null) {
                snapping = false;
                continue;
            }

            if (!snapping) {
                if (nearest.dist2 <= SNAP_DIST2 && directionCompatible(secSegs, i, priSegs, nearest.index)) {
                    snapping = true;
                    snapTarget[i] = nearest.index;
                }
            } else {
                if (nearest.dist2 > SPLIT_DIST2) {
                    snapping = false;
                } else {
                    snapTarget[i] = nearest.index;
                }
            }
        }
        return snapTarget;
    }

    private static double computeRatio(int fromStart, int fromEnd, int trans) {
        if (fromStart < trans && fromEnd < trans) {
            return Math.min((double) (fromStart + 1) / (trans + 1),
                            (double) (fromEnd + 1) / (trans + 1));
        } else if (fromStart < trans) {
            return (double) (fromStart + 1) / (trans + 1);
        } else if (fromEnd < trans) {
            return (double) (fromEnd + 1) / (trans + 1);
        }
        return 1.0;
    }

    private static RoadSegmentPlacement buildJunctionSegment(RoadSegmentPlacement origSeg,
                                                             RoadSegmentPlacement priSeg,
                                                             double ratio) {
        BlockPos origMiddle = origSeg.middlePos();
        BlockPos priMiddle = priSeg.middlePos();
        int newX = origMiddle.getX() + (int) Math.round((priMiddle.getX() - origMiddle.getX()) * ratio);
        int newZ = origMiddle.getZ() + (int) Math.round((priMiddle.getZ() - origMiddle.getZ()) * ratio);
        BlockPos newMiddle = new BlockPos(newX, origMiddle.getY(), newZ);

        int dx = newX - origMiddle.getX();
        int dz = newZ - origMiddle.getZ();

        Set<Long> seen = new HashSet<>();
        List<BlockPos> merged = new ArrayList<>();

        if (priSeg.positions() != null) {
            for (BlockPos p : priSeg.positions()) {
                long key = posKey(p.getX(), p.getZ());
                if (seen.add(key)) merged.add(p);
            }
        }

        if (origSeg.positions() != null) {
            for (BlockPos p : origSeg.positions()) {
                BlockPos shifted = new BlockPos(p.getX() + dx, p.getY(), p.getZ() + dz);
                long key = posKey(shifted.getX(), shifted.getZ());
                if (seen.add(key)) merged.add(shifted);
            }
        }

        return new RoadSegmentPlacement(newMiddle, merged);
    }

    private static long posKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static TruncateResult tryTruncateSharedEndpoint(RoadData primary,
                                                            RoadData secondary,
                                                            List<int[]> runs,
                                                            List<RoadSegmentPlacement> priSegs,
                                                            List<Integer> priTargetY,
                                                            int[] snapTarget,
                                                            List<RoadSegmentPlacement> snappedSegs,
                                                            List<Integer> snappedTargetY) {
        if (primary == null || secondary == null || runs == null || runs.isEmpty()) return null;
        if (!isRegularRoadType(primary.roadType()) || !isRegularRoadType(secondary.roadType())) return null;
        if (snappedSegs == null || snappedSegs.size() < MIN_RUN) return null;
        if (priSegs == null || priSegs.isEmpty() || snapTarget == null || snapTarget.length != snappedSegs.size()) return null;

        long sharedOwner = secondary.sharedOwnerWith(primary);
        if (sharedOwner == RoadData.NO_OWNER_2D) return null;

        boolean sharedAtStart = secondary.ownerA2dKey() == sharedOwner && secondary.ownerB2dKey() != sharedOwner;
        boolean sharedAtEnd = secondary.ownerB2dKey() == sharedOwner && secondary.ownerA2dKey() != sharedOwner;
        if (!sharedAtStart && !sharedAtEnd) return null;

        int[] run = sharedAtStart ? runs.get(0) : runs.get(runs.size() - 1);
        int start = run[0];
        int end = run[1];
        int runLen = end - start + 1;
        int trans = Math.min(TRANSITION, (runLen + 1) / 2);
        int coreStart = start + trans;
        int coreEnd = end - trans;
        int anchorIdx = coreStart <= coreEnd ? (sharedAtStart ? coreStart : coreEnd) : ((start + end) >>> 1);
        int anchorTarget = (anchorIdx >= 0 && anchorIdx < snapTarget.length) ? snapTarget[anchorIdx] : -1;
        if (anchorTarget < 0 || anchorTarget >= priSegs.size()) return null;

        RoadSegmentPlacement anchorSeg = priSegs.get(anchorTarget);
        RoadSegmentPlacement virtualAnchorSeg = new RoadSegmentPlacement(anchorSeg.middlePos(), List.of());
        int anchorY = (priTargetY != null && anchorTarget < priTargetY.size())
                ? priTargetY.get(anchorTarget)
                : anchorSeg.middlePos().getY();

        if (sharedAtStart) {
            int tailStart = end + 1;
            List<RoadSegmentPlacement> keptSegs = new ArrayList<>();
            keptSegs.add(virtualAnchorSeg);
            if (tailStart < snappedSegs.size()) {
                keptSegs.addAll(snappedSegs.subList(tailStart, snappedSegs.size()));
            }
            if (keptSegs.size() < MIN_RUN) return null;
            List<Integer> keptY = new ArrayList<>();
            keptY.add(anchorY);
            if (tailStart < snappedSegs.size()) {
                keptY.addAll(sliceTargetY(snappedTargetY, tailStart, snappedSegs.size()));
            }
            return new TruncateResult(keptSegs, keptY);
        }

        int headEnd = start - 1;
        List<RoadSegmentPlacement> keptSegs = new ArrayList<>();
        if (headEnd >= 0) {
            keptSegs.addAll(snappedSegs.subList(0, headEnd + 1));
        }
        keptSegs.add(virtualAnchorSeg);
        if (keptSegs.size() < MIN_RUN) return null;
        List<Integer> keptY = new ArrayList<>();
        if (headEnd >= 0) {
            keptY.addAll(sliceTargetY(snappedTargetY, 0, headEnd + 1));
        }
        keptY.add(anchorY);
        return new TruncateResult(keptSegs, keptY);
    }

    private static List<Integer> sliceTargetY(List<Integer> targetY, int fromInclusive, int toExclusive) {
        if (targetY == null || targetY.isEmpty()) return List.of();
        int lo = Math.max(0, fromInclusive);
        int hi = Math.min(targetY.size(), toExclusive);
        if (lo >= hi) return List.of();
        return new ArrayList<>(targetY.subList(lo, hi));
    }

    private static List<RoadSpan> filterSpansByRetainedSegments(List<RoadSpan> spans,
                                                                 List<RoadSegmentPlacement> retainedSegs) {
        if (spans == null || spans.isEmpty()) return spans;
        if (retainedSegs == null || retainedSegs.isEmpty()) return List.of();

        Set<Long> keep = new HashSet<>();
        for (RoadSegmentPlacement seg : retainedSegs) {
            if (seg == null || seg.middlePos() == null) continue;
            keep.add(seg.middlePos().asLong());
        }

        List<RoadSpan> out = new ArrayList<>();
        for (RoadSpan span : spans) {
            if (span == null || span.start() == null || span.end() == null) continue;
            if (keep.contains(span.start().asLong()) && keep.contains(span.end().asLong())) {
                out.add(span);
            }
        }
        return out;
    }

    private static boolean isRegularRoadType(int roadType) {
        return roadType == 0 || roadType == 1;
    }

    private static boolean directionCompatible(List<RoadSegmentPlacement> segsA, int idxA,
                                               List<RoadSegmentPlacement> segsB, int idxB) {
        double[] dirA = segmentDirection(segsA, idxA);
        double[] dirB = segmentDirection(segsB, idxB);
        if (dirA == null || dirB == null) return true;
        double dot = Math.abs(dirA[0] * dirB[0] + dirA[1] * dirB[1]);
        return dot > 0.3;
    }

    private static double[] segmentDirection(List<RoadSegmentPlacement> segs, int idx) {
        int prev = Math.max(0, idx - 2);
        int next = Math.min(segs.size() - 1, idx + 2);
        if (prev == next) return null;
        BlockPos a = segs.get(prev).middlePos();
        BlockPos b = segs.get(next).middlePos();
        double ddx = b.getX() - a.getX();
        double ddz = b.getZ() - a.getZ();
        double len = Math.hypot(ddx, ddz);
        if (len < 1.0) return null;
        return new double[]{ddx / len, ddz / len};
    }

    private static List<int[]> extractRuns(int[] snapTarget, int n) {
        List<int[]> runs = new ArrayList<>();
        int runStart = -1;
        for (int i = 0; i < n; i++) {
            if (snapTarget[i] >= 0) {
                if (runStart < 0) runStart = i;
            } else {
                if (runStart >= 0 && (i - runStart) >= MIN_RUN) {
                    runs.add(new int[]{runStart, i - 1});
                }
                runStart = -1;
            }
        }
        if (runStart >= 0 && (n - runStart) >= MIN_RUN) {
            runs.add(new int[]{runStart, n - 1});
        }
        return runs;
    }

    private static SegmentGrid buildGrid(RoadData road) {
        SegmentGrid grid = new SegmentGrid();
        List<RoadSegmentPlacement> segs = road.roadSegmentList();
        if (segs == null) return grid;
        for (int i = 0; i < segs.size(); i++) {
            BlockPos p = segs.get(i).middlePos();
            grid.add(gridKey(p.getX() >> GRID_SHIFT, p.getZ() >> GRID_SHIFT), i, p);
        }
        return grid;
    }

    private static long gridKey(int gx, int gz) {
        return (((long) gx) << 32) | (gz & 0xFFFFFFFFL);
    }

    private record TruncateResult(List<RoadSegmentPlacement> segments, List<Integer> targetY) {}

    private record NearestResult(int index, long dist2) {}

    private static final class SegmentGrid {
        private final Map<Long, List<IndexedPos>> cells = new HashMap<>();

        void add(long key, int segIndex, BlockPos pos) {
            cells.computeIfAbsent(key, k -> new ArrayList<>()).add(new IndexedPos(segIndex, pos));
        }

        NearestResult findNearest(BlockPos target) {
            int gx = target.getX() >> GRID_SHIFT;
            int gz = target.getZ() >> GRID_SHIFT;
            int bestIdx = -1;
            long bestDist2 = Long.MAX_VALUE;
            for (int ddx = -1; ddx <= 1; ddx++) {
                for (int ddz = -1; ddz <= 1; ddz++) {
                    List<IndexedPos> cell = cells.get(gridKey(gx + ddx, gz + ddz));
                    if (cell == null) continue;
                    for (IndexedPos ip : cell) {
                        long d2 = dist2XZ(target, ip.pos);
                        if (d2 < bestDist2) { bestDist2 = d2; bestIdx = ip.index; }
                    }
                }
            }
            return bestIdx >= 0 ? new NearestResult(bestIdx, bestDist2) : null;
        }

        private record IndexedPos(int index, BlockPos pos) {}
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
