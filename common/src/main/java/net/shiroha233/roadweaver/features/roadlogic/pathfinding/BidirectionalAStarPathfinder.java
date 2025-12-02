package net.shiroha233.roadweaver.features.roadlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.*;

/**
 * 双向 A* 寻路：从起点和终点同时扩展搜索，
 * 在中间相遇后重建完整路径，以减少节点展开数量。
 */
final class BidirectionalAStarPathfinder {
    private BidirectionalAStarPathfinder() {
    }

    private static final int BIOME_BASE_COST = 12; // 特定生物群系基础成本（河流/海洋/深海）
    private static final double HEURISTIC_EPSILON = 0.2; // 启发式 epsilon

    static List<Records.RoadSegmentPlacement> calculateLandPath(BlockPos startGround,
            BlockPos endGround,
            int width,
            ServerLevel level,
            int maxSteps,
            TerrainSamplingCache cache) {
        // 特殊情况：起终点非常接近时无需复杂寻路
        if (startGround.equals(endGround)) {
            return Collections.emptyList();
        }

        int d = RoadPathCalculator.getNeighborDistance();
        int[][] neighborOffsets = new int[][] {
                { d, 0 }, { -d, 0 }, { 0, d }, { 0, -d },
                { d, d }, { d, -d }, { -d, d }, { -d, -d }
        };

        var cfg = net.shiroha233.roadweaver.config.ConfigService.get();

        PriorityQueue<Node> openF = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        PriorityQueue<Node> openB = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> nodesF = new HashMap<>();
        Map<BlockPos, Node> nodesB = new HashMap<>();
        Set<BlockPos> closedF = new HashSet<>();
        Set<BlockPos> closedB = new HashSet<>();

        Node startNode = new Node(startGround, null, 0.0, heuristic(startGround, endGround, cfg));
        Node endNode = new Node(endGround, null, 0.0, heuristic(endGround, startGround, cfg));
        openF.add(startNode);
        nodesF.put(startGround, startNode);
        openB.add(endNode);
        nodesB.put(endGround, endNode);

        int stepsBudget = Math.max(1, maxSteps);
        ThreadPoolManager.resetThrottle(); // 重置节流计时器
        while (!openF.isEmpty() && !openB.isEmpty() && stepsBudget-- > 0) {
            ThreadPoolManager.throttle(); // 根据占空比控制CPU使用率
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            Node peekF = openF.peek();
            Node peekB = openB.peek();
            boolean expandForward;
            if (peekF == null) {
                expandForward = false;
            } else if (peekB == null) {
                expandForward = true;
            } else {
                expandForward = peekF.f <= peekB.f;
            }

            Meet meet;
            if (expandForward) {
                meet = expandOneSide(openF, nodesF, closedF, nodesB, closedB, nodesB,
                        true, startGround, endGround, level, cache, neighborOffsets, d, cfg);
            } else {
                meet = expandOneSide(openB, nodesB, closedB, nodesF, closedF, nodesF,
                        false, endGround, startGround, level, cache, neighborOffsets, d, cfg);
            }

            if (meet != null) {
                // 会合后，将前向/反向节点链表合并为一条原始路径，交给 PathPostProcessor 统一处理
                return reconstructPath(meet.forward, meet.backward, width, level, cache);
            }
        }

        return null;
    }

    private static Meet expandOneSide(PriorityQueue<Node> open,
            Map<BlockPos, Node> nodesThis,
            Set<BlockPos> closedThis,
            Map<BlockPos, Node> nodesOther,
            Set<BlockPos> closedOther,
            Map<BlockPos, Node> closedOtherNodes,
            boolean isForward,
            BlockPos from,
            BlockPos to,
            ServerLevel level,
            TerrainSamplingCache cache,
            int[][] neighborOffsets,
            int d,
            net.shiroha233.roadweaver.config.ModConfig cfg) {
        if (open.isEmpty())
            return null;
        Node current = open.poll();
        if (current == null)
            return null;

        closedThis.add(current.pos);
        // 保留已关闭节点的引用，供对方搜索检测相遇
        // nodesThis.remove(current.pos); // 不再移除，保留供相遇检测

        for (int[] off : neighborOffsets) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
            int y = RoadPathCalculator.heightSampler(cache, nxz.getX(), nxz.getZ(), level);
            BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
            if (closedThis.contains(np))
                continue;

            Holder<Biome> biome = cache.getBiome(level, np.getX(), np.getZ());
            int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                    || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? BIOME_BASE_COST : 0;
            int elevation = Math.abs(y - current.pos.getY());

            int offsetSum = Math.abs(Math.abs(off[0])) + Math.abs(off[1]);
            double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
            int stabilityCost = RoadPathCalculator.calculateTerrainStability(cache, np, y, level, d);
            int sea = level.getSeaLevel();
            boolean waterColumn = RoadPathCalculator.isColumnWater(cache, nxz.getX(), nxz.getZ(), level);
            boolean nearWater = RoadPathCalculator.isNearWaterLike(cache, nxz.getX(), nxz.getZ(), level);
            int oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, nxz.getX(), nxz.getZ(), level);
            int waterDepth = Math.max(0, sea - oceanFloor);
            int waterDepthCost = waterColumn ? waterDepth * cfg.waterDepthWeight() : 0;
            int nearWaterCost = nearWater ? cfg.nearWaterCost() : 0;

            double deviation = deviation2d(np, from, to);
            double deviationCost = deviation * cfg.deviationWeight() / Math.max(1.0, d);

            double tentativeG = current.g
                    + stepCost
                    + elevation * cfg.elevationWeight()
                    + biomeCost * cfg.biomeWeight()
                    + stabilityCost * cfg.stabilityWeight()
                    + waterDepthCost
                    + nearWaterCost
                    + deviationCost;

            Node existing = nodesThis.get(np);
            if (existing != null && tentativeG >= existing.g) {
                continue;
            }

            double h = heuristic(np, to, cfg);
            double fWeighted = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
            Node next = new Node(np, current, tentativeG, fWeighted);
            nodesThis.put(np, next);
            open.add(next);

            // 检测相遇：检查对方的 openSet 和 closedSet
            Node other = nodesOther.get(np);
            if (other == null && closedOther.contains(np)) {
                other = closedOtherNodes.get(np);
            }
            if (other != null) {
                // isForward 表示当前是否为前向搜索
                // 前向搜索时：next=前向节点，other=反向节点
                // 反向搜索时：next=反向节点，other=前向节点
                if (isForward) {
                    return new Meet(next, other);
                } else {
                    return new Meet(other, next);
                }
            }
        }

        return null;
    }

    private static List<Records.RoadSegmentPlacement> reconstructPath(Node meetForward,
            Node meetBackward,
            int width,
            ServerLevel level,
            TerrainSamplingCache cache) {
        // 1. 从前向搜索链表回溯到起点，得到起点 -> 会合点 的路径
        List<BlockPos> rawPath = new ArrayList<>();
        Node cur = meetForward;
        while (cur != null) {
            rawPath.add(cur.pos);
            cur = cur.parent;
        }
        Collections.reverse(rawPath);

        // 2. 从反向搜索的会合节点沿 parent 链回溯到终点
        // 注意：反向搜索的 parent 链方向是 会合点 → 终点，所以收集后直接添加即可
        Node backStart = (meetBackward != null && meetBackward.pos.equals(meetForward.pos)) 
                ? meetBackward.parent : meetBackward;
        cur = backStart;
        while (cur != null) {
            rawPath.add(cur.pos);
            cur = cur.parent;
        }

        // 3. 交给 PathPostProcessor 做样条平滑和宽度填充
        return PathPostProcessor.process(rawPath, width, level, cache);
    }

    private static double heuristic(BlockPos a, BlockPos b, net.shiroha233.roadweaver.config.ModConfig cfg) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        double dxzApprox = Math.abs(dx) + Math.abs(dz) - 0.6 * Math.min(Math.abs(dx), Math.abs(dz));
        return dxzApprox * cfg.heuristicWeight();
    }

    private static double deviation2d(BlockPos p, BlockPos a, BlockPos b) {
        double ax = a.getX();
        double az = a.getZ();
        double bx = b.getX();
        double bz = b.getZ();
        double px = p.getX();
        double pz = p.getZ();
        double num = Math.abs((bz - az) * px - (bx - ax) * pz + bx * az - bz * ax);
        double den = Math.hypot(bx - ax, bz - az);
        if (den <= 0.0)
            return 0.0;
        return num / den;
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }

    private static final class Meet {
        final Node forward;
        final Node backward;

        Meet(Node forward, Node backward) {
            this.forward = forward;
            this.backward = backward;
        }
    }
}
