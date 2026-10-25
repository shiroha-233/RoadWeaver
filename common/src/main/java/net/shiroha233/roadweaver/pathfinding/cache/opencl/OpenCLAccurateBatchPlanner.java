package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.pathfinding.cache.opencl.bridge.OpenCLBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 精采样 GPU 批次内存规划器。
 */
public final class OpenCLAccurateBatchPlanner {
    private static final long MIN_DEVICE_BUDGET = 16L * 1024L * 1024L;
    private static final int WATCHDOG_SAFE_MAX_CHUNKS = 128;

    private OpenCLAccurateBatchPlanner() {}

    public static int maxChunks(OpenCLBridge.DeviceInfo device,
                                int nodeCount,
                                int interpolatorCount,
                                int latticePointsPerChunk,
                                int aquiferPointsPerChunk) {
        long scratchPerChunk = Math.multiplyExact(
                (long) Math.max(latticePointsPerChunk, Math.max(aquiferPointsPerChunk, 256)),
                Math.multiplyExact((long) Math.max(1, nodeCount), Double.BYTES));
        long latticePerChunk = Math.multiplyExact(
                Math.multiplyExact((long) Math.max(1, interpolatorCount), (long) latticePointsPerChunk),
                Double.BYTES);
        long aquiferPerChunk = Math.multiplyExact((long) Math.max(1, aquiferPointsPerChunk), 5L * Integer.BYTES);
        long outputPerChunk = 3L * 256L * Integer.BYTES;
        long totalPerChunk = Math.addExact(Math.addExact(scratchPerChunk, latticePerChunk),
                Math.addExact(aquiferPerChunk, outputPerChunk));

        long maxAllocation = positiveOr(device.maxAllocationBytes(), 128L * 1024L * 1024L);
        long globalBudget = Math.max(MIN_DEVICE_BUDGET,
                positiveOr(device.globalMemoryBytes(), 512L * 1024L * 1024L) / 4L);
        long byScratch = maxAllocation / Math.max(1L, scratchPerChunk);
        long byLattice = maxAllocation / Math.max(1L, latticePerChunk);
        long byTotal = globalBudget / Math.max(1L, totalPerChunk);
        long result = Math.min(byScratch, Math.min(byLattice, byTotal));
        if (result <= 0L) {
            return 0;
        }
        // 保持在桌面 GPU 看门狗和大分配拐点内；整个区域仍由一次高优先级设备任务连续提交。
        return (int) Math.min(WATCHDOG_SAFE_MAX_CHUNKS, result);
    }

    static List<List<Long>> spatialBatches(List<Long> chunkKeys, int maxChunks) {
        if (maxChunks <= 0) {
            throw new IllegalArgumentException("maxChunks must be positive");
        }
        if (chunkKeys.isEmpty()) {
            return List.of();
        }

        int tileSize = Math.max(1, (int) Math.floor(Math.sqrt(maxChunks)));
        Map<Tile, List<Long>> chunksByTile = new LinkedHashMap<>();
        for (long chunkKey : chunkKeys) {
            int tileX = Math.floorDiv(ChunkPos.getX(chunkKey), tileSize);
            int tileZ = Math.floorDiv(ChunkPos.getZ(chunkKey), tileSize);
            chunksByTile.computeIfAbsent(new Tile(tileX, tileZ), ignored -> new ArrayList<>()).add(chunkKey);
        }

        List<List<Long>> batches = new ArrayList<>(chunksByTile.size());
        for (List<Long> tileChunks : chunksByTile.values()) {
            for (int offset = 0; offset < tileChunks.size(); offset += maxChunks) {
                batches.add(List.copyOf(tileChunks.subList(offset, Math.min(tileChunks.size(), offset + maxChunks))));
            }
        }
        return List.copyOf(batches);
    }

    private static long positiveOr(long value, long fallback) {
        return value > 0L ? value : fallback;
    }

    private record Tile(int x, int z) {}
}
