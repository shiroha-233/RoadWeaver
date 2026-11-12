/* 文件职责：验证精采 OpenCL 批次按显存与单次分配上限切分。 */
package net.shiroha233.roadweaver.terrain.opencl;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCLAccurateBatchPlannerTest {
    @Test
    void capsLargeDevicesAtWatchdogSafeChunkCount() {
        OpenCLBridge.DeviceInfo device = new OpenCLBridge.DeviceInfo(
                "large", true, true, 16L << 30, 8L << 30, 256);
        assertEquals(128, OpenCLAccurateBatchPlanner.maxChunks(device, 80, 8, 1225, 315));
    }

    @Test
    void respectsLargestScratchAllocation() {
        OpenCLBridge.DeviceInfo device = new OpenCLBridge.DeviceInfo(
                "limited", true, true, 64L << 20, 1_600_000L, 256);
        assertEquals(2, OpenCLAccurateBatchPlanner.maxChunks(device, 100, 10, 1000, 300));
    }

    @Test
    void rejectsDeviceThatCannotFitOneScratchBuffer() {
        OpenCLBridge.DeviceInfo device = new OpenCLBridge.DeviceInfo(
                "too-small", true, true, 64L << 20, 799_999L, 256);
        assertEquals(0, OpenCLAccurateBatchPlanner.maxChunks(device, 100, 10, 1000, 300));
    }

    @Test
    void groupsChunksIntoBoundedSpatialTiles() {
        List<Long> chunks = new ArrayList<>();
        for (int z = -12; z <= 12; z++) {
            for (int x = -12; x <= 12; x++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }

        List<List<Long>> batches = OpenCLAccurateBatchPlanner.spatialBatches(chunks, 128);
        Set<Long> visited = new HashSet<>();
        for (List<Long> batch : batches) {
            assertTrue(batch.size() <= 128);
            int minX = batch.stream().mapToInt(ChunkPos::getX).min().orElseThrow();
            int maxX = batch.stream().mapToInt(ChunkPos::getX).max().orElseThrow();
            int minZ = batch.stream().mapToInt(ChunkPos::getZ).min().orElseThrow();
            int maxZ = batch.stream().mapToInt(ChunkPos::getZ).max().orElseThrow();
            assertTrue(maxX - minX < 11);
            assertTrue(maxZ - minZ < 11);
            visited.addAll(batch);
        }
        assertEquals(new HashSet<>(chunks), visited);
    }
}
