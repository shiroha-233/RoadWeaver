package net.shiroha233.roadweaver.planning.path;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebugRawPathStoreTest {
    @TempDir Path temp;

    @Test
    void corridorPersistsBothOriginalAndRefinedNodesAcrossReload() throws IOException {
        PlannedPathKey key = PlannedPathKey.of(new BlockPos(0, 0, 0), new BlockPos(32, 0, 0));
        List<BlockPos> raw = List.of(new BlockPos(0, 60, 0), new BlockPos(8, 61, 0),
                new BlockPos(16, 62, 0), new BlockPos(32, 63, 0));
        List<BlockPos> accurate = List.of(new BlockPos(0, 70, 0), new BlockPos(8, 71, 0),
                new BlockPos(16, 72, 0), new BlockPos(32, 73, 0));

        DebugRawPathStore initial = new DebugRawPathStore(temp);
        initial.recordSearch(key, TerrainSamplingMode.COARSE_CORRIDOR, raw);
        assertEquals(raw, collect(initial.query(-1, -1, 33, 1, 100), true));
        initial.recordResolved(key, TerrainSamplingMode.COARSE_CORRIDOR, raw, accurate);
        // Replanning the same edge cannot erase the refined track.
        initial.recordSearch(key, TerrainSamplingMode.COARSE_CORRIDOR, raw);

        List<DebugRawPath> restored = new DebugRawPathStore(temp).query(-1, -1, 33, 1, 100);
        assertEquals(raw, collect(restored, true));
        assertEquals(accurate, collect(restored, false));
        assertTrue(restored.stream().allMatch(path -> path.mode() == TerrainSamplingMode.COARSE_CORRIDOR));
        assertTrue(new DebugRawPathStore(temp).query(1000, 1000, 1200, 1200, 100).isEmpty());
    }

    @Test
    void fullRegionOnlyExposesAccurateNodes() throws IOException {
        PlannedPathKey key = PlannedPathKey.of(new BlockPos(0, 0, 0), new BlockPos(16, 0, 0));
        List<BlockPos> nodes = List.of(new BlockPos(0, 70, 0), new BlockPos(16, 71, 0));
        DebugRawPathStore store = new DebugRawPathStore(temp);
        store.recordSearch(key, TerrainSamplingMode.FULL_REGION, nodes);
        store.recordResolved(key, TerrainSamplingMode.FULL_REGION, nodes, nodes);

        List<DebugRawPath> found = new DebugRawPathStore(temp).query(-10, -10, 20, 10, 100);
        assertEquals(nodes, collect(found, false));
        assertTrue(collect(found, true).isEmpty());
    }

    @Test
    void viewportClippingNeverConnectsSeparatedVisibleRuns() {
        List<BlockPos> path = List.of(
                new BlockPos(0, 64, 0), new BlockPos(8, 64, 0),
                new BlockPos(16, 64, 0), new BlockPos(24, 64, 0),
                new BlockPos(8, 64, 0), new BlockPos(0, 64, 0));
        assertEquals(List.of(List.of(path.get(0), path.get(1), path.get(2)),
                        List.of(path.get(3), path.get(4), path.get(5))),
                DebugRawPathStore.clipRuns(path, 0, -1, 8, 1).stream().toList());
    }

    private static List<BlockPos> collect(List<DebugRawPath> paths, boolean coarse) {
        List<BlockPos> result = new ArrayList<>();
        for (DebugRawPath path : paths) result.addAll(coarse ? path.coarse() : path.accurate());
        return result;
    }
}
