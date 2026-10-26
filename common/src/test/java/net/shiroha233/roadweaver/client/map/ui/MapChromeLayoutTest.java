/* 文件职责：验证地图 Dock 与工作区在常见逻辑分辨率下保持紧凑、在屏内且互不遮挡。 */
package net.shiroha233.roadweaver.client.map.ui;

import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.ui.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapChromeLayoutTest {
    private static final int WORKSPACE_DOCK_GAP = 8;

    @Test
    void layoutsStayInsideScreenAndDoNotOverlap() {
        assertLayout(320, 240, 24, 244, 174);
        assertLayout(480, 270, 28, 244, 200);
        assertLayout(854, 480, 30, 244, 403);
        assertLayout(1920, 1080, 30, 320, 907);
    }

    private static void assertLayout(int screenWidth,
                                     int screenHeight,
                                     int expectedCellSize,
                                     int expectedPanelWidth,
                                     int expectedPanelHeight) {
        MapDockRenderer.DockLayout dock = MapDockRenderer.layout(screenWidth, screenHeight);
        Rect dockBounds = dock.bounds();
        int panelBottom = dockBounds.y() - WORKSPACE_DOCK_GAP;
        Rect panelBounds = MapWorkspacePanel.panelBounds(screenWidth, screenHeight, panelBottom);

        assertEquals(expectedCellSize, dock.cellSize());
        assertEquals(expectedPanelWidth, panelBounds.width());
        assertEquals(expectedPanelHeight, panelBounds.height());
        assertInsideScreen(dockBounds, screenWidth, screenHeight);
        assertInsideScreen(panelBounds, screenWidth, screenHeight);
        assertTrue(panelBounds.bottom() <= panelBottom);

        for (MapDockAction action : MapDockAction.values()) {
            Rect button = dock.button(action);
            assertNotNull(button);
            assertTrue(button.x() >= dockBounds.x());
            assertTrue(button.y() >= dockBounds.y());
            assertTrue(button.right() <= dockBounds.right());
            assertTrue(button.bottom() <= dockBounds.bottom());
        }
    }

    private static void assertInsideScreen(Rect bounds, int screenWidth, int screenHeight) {
        assertTrue(bounds.x() >= 0);
        assertTrue(bounds.y() >= 0);
        assertTrue(bounds.right() <= screenWidth);
        assertTrue(bounds.bottom() <= screenHeight);
    }
}
