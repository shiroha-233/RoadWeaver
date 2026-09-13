/* 文件职责：验证道路区块计划的局部索引、桥段过滤与 O(1) 密度边界。 */
package net.shiroha233.roadweaver.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadChunkPlanCompilerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void compilesLocalSliceAndConstantTimeDensityStamp() {
        RoadData road = road(false);

        RoadChunkPlan plan = RoadChunkPlanCompiler.compile(
                new ChunkPos(0, 0), List.of(road), true, 4, 7L);

        assertEquals(7L, plan.revision());
        assertEquals(1, plan.slices().size());
        assertFalse(plan.densityStamp().isEmpty());
        assertTrue(plan.densityStamp().density(8, 69, 8) > 0.0D);
        assertEquals(0.0D, plan.densityStamp().density(8, 70, 8));
        assertTrue(plan.densityStamp().density(8, 71, 8) < 0.0D);
        assertEquals(0.0D, plan.densityStamp().density(8, 74, 8));
        assertEquals(0.0D, plan.densityStamp().density(16, 69, 8));
    }

    @Test
    void excludesBridgeFromTerrainStampButKeepsFeatureSlice() {
        RoadData bridge = road(true);

        RoadChunkPlan plan = RoadChunkPlanCompiler.compile(
                new ChunkPos(0, 0), List.of(bridge), false, 4, 1L);

        assertEquals(1, plan.slices().size());
        assertTrue(plan.densityStamp().isEmpty());
        assertTrue(plan.densityStamp().isOccupied(8, 8));
    }

    private static RoadData road(boolean bridge) {
        List<RoadSegmentPlacement> segments = new ArrayList<>();
        List<Integer> targetY = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            BlockPos center = new BlockPos(x, 70, 8);
            segments.add(new RoadSegmentPlacement(center, List.of(center)));
            targetY.add(70);
        }
        List<RoadSpan> spans = bridge
                ? List.of(new RoadSpan(segments.get(0).middlePos(), segments.get(segments.size() - 1).middlePos(), SpanType.BRIDGE))
                : List.of();
        return new RoadData(3, 0, List.of(), List.of(), segments, spans, targetY, 11L, 22L);
    }
}
