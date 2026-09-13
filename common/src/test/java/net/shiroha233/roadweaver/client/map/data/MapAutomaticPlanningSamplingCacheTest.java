/* 文件职责：验证客户端自动规划采样范围缓存的替换与清理行为。 */
package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapAutomaticPlanningSamplingCacheTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @AfterEach
    void tearDown() {
        MapAutomaticPlanningSamplingCache.clear();
    }

    @Test
    void replacesAndRemovesDimensionRanges() {
        AutomaticPlanningSamplingBounds bounds = new AutomaticPlanningSamplingBounds(-64, -64, 64, 64);

        MapAutomaticPlanningSamplingCache.replace(OVERWORLD, List.of(bounds));
        assertEquals(List.of(bounds), MapAutomaticPlanningSamplingCache.snapshot(OVERWORLD));

        MapAutomaticPlanningSamplingCache.replace(OVERWORLD, List.of());
        assertTrue(MapAutomaticPlanningSamplingCache.snapshot(OVERWORLD).isEmpty());
    }
}
