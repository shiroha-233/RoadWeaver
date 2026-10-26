/* 文件职责：验证自动规划采样活动范围的并发生命周期。 */
package net.shiroha233.roadweaver.planning.pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticPlanningSamplingActivitiesTest {

    @AfterEach
    void tearDown() {
        AutomaticPlanningSamplingActivities.clearAll();
    }

    @Test
    void closingOneActivityKeepsOtherRangesVisible() {
        Object level = new Object();
        AutomaticPlanningSamplingBounds first = new AutomaticPlanningSamplingBounds(-128, -128, 128, 128);
        AutomaticPlanningSamplingBounds second = new AutomaticPlanningSamplingBounds(256, 256, 512, 512);

        AutomaticPlanningSamplingActivities.Activity firstActivity =
                AutomaticPlanningSamplingActivities.beginForKey(level, first);
        AutomaticPlanningSamplingActivities.Activity secondActivity =
                AutomaticPlanningSamplingActivities.beginForKey(level, second);

        assertEquals(List.of(first, second), AutomaticPlanningSamplingActivities.snapshotForKey(level));

        firstActivity.close();
        assertEquals(List.of(second), AutomaticPlanningSamplingActivities.snapshotForKey(level));

        secondActivity.close();
        assertTrue(AutomaticPlanningSamplingActivities.snapshotForKey(level).isEmpty());
    }

    @Test
    void staleActivityCannotRemoveRangeRegisteredAfterReset() {
        Object level = new Object();
        AutomaticPlanningSamplingActivities.Activity stale = AutomaticPlanningSamplingActivities.beginForKey(
                level,
                new AutomaticPlanningSamplingBounds(0, 0, 16, 16));
        AutomaticPlanningSamplingActivities.clearForKey(level);

        AutomaticPlanningSamplingBounds current = new AutomaticPlanningSamplingBounds(32, 32, 48, 48);
        AutomaticPlanningSamplingActivities.Activity active =
                AutomaticPlanningSamplingActivities.beginForKey(level, current);

        stale.close();
        assertEquals(List.of(current), AutomaticPlanningSamplingActivities.snapshotForKey(level));

        active.close();
    }
}
