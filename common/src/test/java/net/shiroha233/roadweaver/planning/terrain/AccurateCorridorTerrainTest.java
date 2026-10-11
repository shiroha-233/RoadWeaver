package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class AccurateCorridorTerrainTest {
    @Test
    void sampledCorridorSearchReadsOnlyPreparedArrays() {
        Map<Long, AccurateHeightSample> samples = new HashMap<>();
        for (int x = -800; x <= 800; x += 8) {
            samples.put(AccurateHeightSample.key(x, -8), new AccurateHeightSample(72, 60, 73));
        }
        samples.put(AccurateHeightSample.key(-8, 0), new AccurateHeightSample(70, 55, 70));
        samples.put(AccurateHeightSample.key(0, 0), new AccurateHeightSample(76, 76, 78));
        AtomicInteger biomeLookups = new AtomicInteger();
        AccurateCorridorTerrain terrain = new AccurateCorridorTerrain(63, 8, samples, (x, z) -> {
            biomeLookups.incrementAndGet();
            return null;
        });
        assertEquals(samples.size(), biomeLookups.get());
        samples.clear();

        assertEquals(78, terrain.height(0, 0));
        assertEquals(76, terrain.oceanFloor(0, 0));
        assertFalse(terrain.isColumnWater(0, 0));
        assertTrue(terrain.isColumnWater(-8, 0));
        assertTrue(terrain.isNearWater(0, 0, 8));
        assertEquals(70, terrain.sampleBundle(-8, 0).height());
        assertEquals(8, terrain.sampleBundle(-8, 0).waterDepth());
        assertTrue(terrain.hasAccurateSample(-800, -8));
        assertTrue(terrain.hasAccurateSample(800, -8));
        assertFalse(terrain.contains(8, 0));
        assertEquals(63, terrain.height(8, 0));
        assertEquals(null, terrain.biome(0, 0));
        assertEquals(201 + 2, biomeLookups.get());
    }

    @Test
    void corridorSamplesSegmentsNotWholeBoundingRectangle() {
        List<BlockPos> path = List.of(new BlockPos(-128, 70, -128), new BlockPos(0, 70, 0), new BlockPos(128, 70, 128));
        var points = new HashSet<>(AccurateCorridorTerrain.positions(path, 8, 16));
        assertTrue(points.contains(new BlockPos(-64, 0, -64)));
        assertTrue(points.contains(new BlockPos(0, 0, 16)));
        assertFalse(points.contains(new BlockPos(-128, 0, 128)));
        assertTrue(points.stream().allMatch(p -> p.getX() % 8 == 0 && p.getZ() % 8 == 0));
    }
}
