package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class AccurateCorridorTerrainTest {
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
