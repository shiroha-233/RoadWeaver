package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.cache.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.client.map.interaction.MapInteraction;
import net.shiroha233.roadweaver.client.map.render.MapAutomaticPlanningSamplingOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.map.render.MapLegendRenderer;
import net.shiroha233.roadweaver.client.map.render.MapOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapRenderers;
import net.shiroha233.roadweaver.client.map.render.MapSamplingNoticeOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapSamplingOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapStatusRenderer;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.client.ui.Rect;
import net.shiroha233.roadweaver.client.map.view.MapViewportController;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;
import net.shiroha233.roadweaver.planning.pipeline.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.pipeline.AutomaticPlanningSamplingBounds;
import net.shiroha233.roadweaver.planning.pipeline.TerrainSamplingSessionSnapshot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 地图屏幕的渲染流程：按网格、拓扑、采样、悬浮与停靠栏的次序绘制一帧。
 */
final class MapScreenRenderer {

    private MapScreenRenderer() {
    }

    static void render(RoadMapScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentW = screen.mapWidth();
        int contentH = screen.mapHeight();
        screen.view().lockAspect(contentW, contentH);
        int left = 0;
        int top = 0;
        int right = screen.width;
        int bottom = screen.height;
        Minecraft minecraft = screen.client();
        TerrainSamplingSessionSnapshot samplingSession = screen.syncTerrainLayer(minecraft);
        MapViewportController.requestTerrainTiles(minecraft, screen.terrainTiles(), screen.view(), contentW, contentH);
        ServerLevel samplingLevel = screen.singleplayerLevel();
        MapSamplingSnapshot mapSamplingSnapshot = screen.sampling().snapshot();
        List<AutomaticPlanningSamplingBounds> automaticPlanningSamplingBounds =
                activeAutomaticPlanningSamplingBounds(samplingLevel, screen.data().dimensionId());
        screen.onSamplingPolled(mapSamplingSnapshot);
        screen.search().tick(screen.data().dimensionId());
        MapSnapshot visibleSnapshot = screen.data().visibleSnapshot();
        MapDockRenderer.DockLayout dockLayout = MapDockRenderer.layout(screen.width, screen.height);
        screen.applyDockLayout(dockLayout);
        int workspaceBottom = screen.workspaceBottom();
        screen.panel().setMousePosition(mouseX, mouseY);
        graphics.fill(0, 0, screen.width, screen.height, MapTheme.COLOR_BACKGROUND);
        graphics.enableScissor(left, top, right, bottom);
        screen.terrainTiles().render(graphics, minecraft, screen.view(), left, top, contentW, contentH);
        MapRenderers.renderGrid(graphics, screen.font(), 0, 0, contentW, contentH, 0,
                screen.view().getMinX(), screen.view().getMaxX(),
                screen.view().getMinZ(), screen.view().getMaxZ(),
                MapTheme.COLOR_GRID, MapTheme.GRID_TARGET_PX, MapTheme.COLOR_TEXT);
        int thickness = thicknessOf(screen);
        MapRenderers.renderRoadPolylines(graphics, visibleSnapshot.roadPolylines(),
                (x1, z1, x2, z2) -> screen.view().segmentInViewWorld(x1, z1, x2, z2),
                v -> screen.view().toScreenX(v, 0, 0, contentW),
                v -> screen.view().toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness),
                MapTheme.COLOR_COMPLETED,
                left, top, right, bottom,
                Math.max(1, Math.round(16 / Math.max(0.1f, (float) screen.view().pxPerBlockX(contentW)))));
        MapRenderers.renderStructures(graphics, screen.font(),
                visibleSnapshot.structures(),
                visibleSnapshot::structureName,
                v -> screen.view().toScreenX(v, 0, 0, contentW),
                v -> screen.view().toScreenY(v, 0, 0, contentH),
                (x, z) -> screen.view().isInViewWorld(x, z),
                Math.max(MapTheme.STRUCTURE_MARKER_SIZE, pointSizeOf(screen)),
                left, top, right, bottom);
        List<StructureConnection> connForLines = new ArrayList<>(visibleSnapshot.connections());
        boolean hasDetailedRoadPolylines = !visibleSnapshot.roadPolylines().isEmpty();
        connForLines.removeIf(c -> hasDetailedRoadPolylines && c.status() == ConnectionStatus.COMPLETED);
        MapRenderers.renderConnections(graphics, connForLines,
                (x1, z1, x2, z2) -> screen.view().segmentInViewWorld(x1, z1, x2, z2),
                v -> screen.view().toScreenX(v, 0, 0, contentW),
                v -> screen.view().toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness - 1),
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING,
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                left, top, right, bottom);
        MapAutomaticPlanningSamplingOverlayRenderer.render(graphics, screen.font(), screen.view(),
                automaticPlanningSamplingBounds, contentW, contentH, left, top, right, bottom);
        MapSamplingOverlayRenderer.render(graphics, screen.font(), screen.view(),
                mapSamplingSnapshot, contentW, contentH, left, top, right, bottom);
        MapSamplingNoticeOverlayRenderer.render(graphics, screen.font(),
                screen.sampling().currentNotice(), left, top, right, bottom);
        MapOverlayRenderer.renderManualModePreview(graphics, visibleSnapshot, screen.view(), screen.inputHandler(),
                screen.state().hasSelection() ? screen.state().getSelectedA() : null,
                mouseX, mouseY, contentW, contentH,
                left, top, right, bottom,
                pointSizeOf(screen) * 2 + 4,
                thickness);
        boolean hoveringUi = screen.contextMenu().isOpen()
                || dockLayout.contains(mouseX, mouseY)
                || screen.panel().contains(mouseX, mouseY, screen.width, screen.height, workspaceBottom);
        if (!hoveringUi) {
            MapInteraction.renderHoverHighlight(graphics, visibleSnapshot, screen.view(), 0, 0, contentW, contentH,
                    0, mouseX, mouseY);
        }
        MapOverlayRenderer.renderPlayer(graphics, minecraft, screen.inputHandler(), screen.view(),
                contentW, contentH, left, top, right, bottom);
        graphics.disableScissor();
        int statusRight = screen.panel().leftEdge(screen.width, screen.height, workspaceBottom);
        if (!screen.panel().isOpen()) {
            MapLegendRenderer.render(graphics, screen.font(), screen.width, visibleSnapshot);
        }
        MapStatusRenderer.render(graphics, screen.font(), screen.width, statusRight,
                screen.data().loadSession(), samplingSession);
        if (!hoveringUi) {
            MapInteraction.renderHoverTooltip(graphics, screen.font(), visibleSnapshot, screen.view(),
                    0, 0, contentW, contentH, 0, mouseX, mouseY);
        }
        if (screen.state().isZoomDebounceReady()) {
            screen.state().clearZoomDebounce();
            screen.onRequestView();
        }
        screen.panel().render(graphics, screen.font(), screen.width, screen.height, workspaceBottom,
                screen.data().snapshot(), screen.filterState(), screen.search().results(),
                screen.search().isLoading(), screen.search().hasFailed());
        renderSearchBox(screen, graphics, mouseX, mouseY, partialTick);
        MapDockRenderer.render(graphics, screen.font(), dockLayout, mouseX, mouseY,
                activeActions(screen, mapSamplingSnapshot),
                disabledActions(screen, samplingLevel, mapSamplingSnapshot),
                mapSamplingSnapshot.active() ? mapSamplingSnapshot.percent() : -1);
        screen.contextMenu().render(graphics, screen.font(), mouseX, mouseY,
                screen.width, screen.height);
    }

    private static EnumSet<MapDockAction> activeActions(RoadMapScreen screen, MapSamplingSnapshot samplingSnapshot) {
        MapWorkspacePanel panel = screen.panel();
        EnumSet<MapDockAction> actions = EnumSet.noneOf(MapDockAction.class);
        if (panel.isTab(MapWorkspacePanel.Tab.SEARCH)) {
            actions.add(MapDockAction.SEARCH);
        }
        if (panel.isTab(MapWorkspacePanel.Tab.FILTER)) {
            actions.add(MapDockAction.FILTER);
        }
        if (screen.data().loadSession() != null) {
            actions.add(MapDockAction.REFRESH);
        }
        if (samplingSnapshot.active()) {
            actions.add(MapDockAction.SAMPLE);
        }
        if (screen.state().isManualMode()) {
            actions.add(MapDockAction.MANUAL_CONNECT);
        }
        return actions;
    }

    private static EnumSet<MapDockAction> disabledActions(RoadMapScreen screen,
                                                        ServerLevel samplingLevel,
                                                        MapSamplingSnapshot samplingSnapshot) {
        EnumSet<MapDockAction> actions = EnumSet.noneOf(MapDockAction.class);
        if (!screen.sampling().isAvailable(samplingLevel) || samplingSnapshot.active()) {
            actions.add(MapDockAction.SAMPLE);
        }
        return actions;
    }

    private static List<AutomaticPlanningSamplingBounds> activeAutomaticPlanningSamplingBounds(
            ServerLevel level, net.minecraft.resources.ResourceLocation dimensionId) {
        if (level != null) {
            return AutomaticPlanningSamplingActivities.snapshot(level);
        }
        return MapAutomaticPlanningSamplingCache.snapshot(dimensionId);
    }
    private static int thicknessOf(RoadMapScreen screen) {
        double blocksPerPixel = Math.min(screen.view().pxPerBlockX(screen.mapWidth()),
                screen.view().pxPerBlockZ(screen.mapHeight()));
        int thickness = (int) Math.round(blocksPerPixel);
        return Math.max(MapTheme.MIN_THICKNESS, Math.min(thickness, MapTheme.MAX_THICKNESS));
    }

    private static int pointSizeOf(RoadMapScreen screen) {
        return MapTheme.BASE_POINT_SIZE + thicknessOf(screen);
    }

    private static void renderSearchBox(RoadMapScreen screen, GuiGraphics graphics,
                                        int mouseX, int mouseY, float partialTick) {
        EditBox box = screen.searchBox();
        if (!screen.panel().isTab(MapWorkspacePanel.Tab.SEARCH) || box == null) {
            return;
        }
        Rect rect = screen.panel().searchField();
        if (rect == null) {
            return;
        }
        MapDockRenderer.fillRounded(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 5,
                MapTheme.PANEL_CONTROL_BG);
        box.setX(rect.x() + 6);
        box.setY(rect.y() + 1);
        box.setWidth(Math.max(20, rect.width() - 12));
        box.render(graphics, mouseX, mouseY, partialTick);
    }
}