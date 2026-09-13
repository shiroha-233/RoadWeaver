/* 文件职责：呈现道路地图并同步单机世界的有效地形图层。 */
package net.shiroha233.roadweaver.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotStore;
import net.shiroha233.roadweaver.client.map.data.MapAutomaticPlanningSamplingCache;
import net.shiroha233.roadweaver.client.map.interaction.MapInteraction;
import net.shiroha233.roadweaver.client.map.data.MapFilterState;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.map.render.MapAutomaticPlanningSamplingOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapLegendRenderer;
import net.shiroha233.roadweaver.client.map.render.MapOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapRenderers;
import net.shiroha233.roadweaver.client.map.render.MapSamplingNoticeOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapSamplingOverlayRenderer;
import net.shiroha233.roadweaver.client.map.render.MapStatusRenderer;
import net.shiroha233.roadweaver.client.map.tile.SingleplayerTerrainTileManager;
import net.shiroha233.roadweaver.client.map.ui.ContextMenu;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.client.map.ui.NoteEditScreen;
import net.shiroha233.roadweaver.client.map.ui.SimpleTextInputScreen;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingBounds;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;
import net.shiroha233.roadweaver.map.tile.sampling.MapTerrainSamplingService;
import net.shiroha233.roadweaver.network.ClientNetBridge;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessions;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingActivities;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.lwjgl.glfw.GLFW;

/**
 * 道路地图界面
 */
public class RoadMapScreen extends Screen implements MapInputHandler.Callbacks {
    private static final long MAP_SAMPLING_NOTICE_DURATION_MS = 4_000L;
    private static final int WORKSPACE_DOCK_GAP = 8;
    
    // 翻译键
    private static final Component MENU_TELEPORT = Component.translatable("gui.roadweaver.map.menu.teleport");
    private static final Component MENU_SET_ALIAS = Component.translatable("gui.roadweaver.map.menu.set_alias");
    private static final Component MENU_EDIT_NOTE = Component.translatable("gui.roadweaver.map.menu.edit_note");
    private static final Component DIALOG_ALIAS_TITLE = Component.translatable("gui.roadweaver.map.dialog.alias_title");

    // 数据
    private MapSnapshot snapshot = MapSnapshot.empty();
    private ResourceLocation currentDimensionId;
    private MapSnapshotStore snapshotStore = new MapSnapshotStore();
    private MapLoadSession loadSession;
    
    // 组件
    private final MapState state = new MapState();
    private final MapView view = new MapView();
    private final MapInputHandler inputHandler;
    private final ContextMenu contextMenu = new ContextMenu();
    private final MapWorkspacePanel workspacePanel = new MapWorkspacePanel();
    private final MapFilterState filterState = new MapFilterState();
    private final SingleplayerTerrainTileManager terrainTiles = new SingleplayerTerrainTileManager();
    private final MapTerrainSamplingService mapSampling = new MapTerrainSamplingService();
    private MapSamplingSnapshot.Stage observedSamplingStage = MapSamplingSnapshot.Stage.IDLE;
    private Component mapSamplingNotice;
    private long mapSamplingNoticeExpiresAtMs;

    private MapDockRenderer.DockLayout dockLayout;
    private EditBox searchBox;
    private String searchQuery = "";
    private List<MapSearchResult> searchResults = List.of();
    private boolean searchLoading;
    private boolean searchFailed;
    private int searchRequestSeq;
    private long searchDeadlineMs;
    private long searchStartedAtMs;
    private String pendingSearchQuery = "";
    private String submittedSearchQuery = "";
    private MapSnapshot filteredSnapshot;
    private int filteredSnapshotRevision = -1;
    private MapSnapshot filteredSnapshotSource;
    
    // 布局
    private int mapX, mapY, mapW, mapH;

    public RoadMapScreen() {
        super(Component.translatable("gui.roadweaver.map.title"));
        this.inputHandler = new MapInputHandler(state, view, this);
    }

    // ========== 生命周期 ==========
    
    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, 0, 0, 1, 18,
                Component.translatable("gui.roadweaver.map.panel.search.input"));
        searchBox.setMaxLength(MapStructureSearchService.MAX_QUERY_LENGTH);
        searchBox.setBordered(false);
        searchBox.setTextColor(MapTheme.PANEL_TEXT);
        searchBox.setTextColorUneditable(MapTheme.PANEL_MUTED);
        searchBox.setHint(Component.translatable("gui.roadweaver.map.panel.search.hint"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(this::onSearchQueryChanged);
        if (!hasMapOpenPermission()) {
            denyMapAccessAndClose();
            return;
        }
        MapSnapshotCache.cancelClear();
        Minecraft mc = this.minecraft;
        if (mc != null && mc.level != null) {
            currentDimensionId = mc.level.dimension().location();
            snapshotStore = MapSnapshotCache.store(currentDimensionId);
            this.snapshot = snapshotStore.snapshot();
        }
        syncTerrainLayer(mc);
        computeMapRect();
        inputHandler.updateLayout(mapX, mapY, mapW, mapH, 0);
        
        int contentW = mapW;
        int contentH = mapH;
        view.resetFromSnapshot(snapshot);
        
        if (mc != null && mc.player != null) {
            view.calibrateInitialToPlayer(mc, contentW, contentH, MapTheme.GRID_TARGET_PX);
        }
        onRequestView();
    }

    @Override
    public void removed() {
        super.removed();
        terrainTiles.clear();
        loadSession = null;
        MapSnapshotCache.putStoreSnapshot(currentDimensionId, snapshotStore);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 渲染 ==========
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int contentW = mapW;
        int contentH = mapH;
        view.lockAspect(contentW, contentH);
        int left = 0;
        int top = 0;
        int right = this.width;
        int bottom = this.height;
        Minecraft mc = this.minecraft;
        TerrainSamplingSessionSnapshot samplingSession = syncTerrainLayer(mc);
        MapViewportController.requestTerrainTiles(mc, terrainTiles, view, contentW, contentH);
        ServerLevel samplingLevel = MapViewportController.resolveSingleplayerLevel(mc);
        MapSamplingSnapshot mapSamplingSnapshot = mapSampling.snapshot();
        List<AutomaticPlanningSamplingBounds> automaticPlanningSamplingBounds =
                activeAutomaticPlanningSamplingBounds(samplingLevel);
        handleSamplingTransition(mapSamplingSnapshot);
        processSearchDebounce();
        MapSnapshot visibleSnapshot = visibleSnapshot();
        dockLayout = MapDockRenderer.layout(this.width, this.height);
        int workspaceBottom = workspaceBottomLimit(dockLayout);
        workspacePanel.setMousePosition(mouseX, mouseY);
        g.fill(0, 0, this.width, this.height, MapTheme.COLOR_BACKGROUND);
        g.enableScissor(left, top, right, bottom);
        terrainTiles.render(g, this.minecraft, view, left, top, contentW, contentH);
        MapRenderers.renderGrid(g, this.font, 0, 0, mapW, mapH, 0,
                view.getMinX(), view.getMaxX(), view.getMinZ(), view.getMaxZ(),
                MapTheme.COLOR_GRID, MapTheme.GRID_TARGET_PX, MapTheme.COLOR_TEXT);
        int thickness = computeThickness();
        MapRenderers.renderRoadPolylines(g, visibleSnapshot.roadPolylines(),
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness),
                MapTheme.COLOR_COMPLETED,
                left, top, right, bottom,
                Math.max(1, Math.round(16 / Math.max(0.1f, (float) view.pxPerBlockX(contentW)))));
        MapRenderers.renderStructures(g, this.font,
                visibleSnapshot.structures(),
                visibleSnapshot::structureName,
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                (x, z) -> view.isInViewWorld(x, z),
                Math.max(MapTheme.STRUCTURE_MARKER_SIZE, computePointSize()),
                left, top, right, bottom);
        List<StructureConnection> connForLines = new ArrayList<>(visibleSnapshot.connections());
        boolean hasDetailedRoadPolylines = !visibleSnapshot.roadPolylines().isEmpty();
        connForLines.removeIf(c -> hasDetailedRoadPolylines && c.status() == ConnectionStatus.COMPLETED);
        MapRenderers.renderConnections(g, connForLines,
                (x1, z1, x2, z2) -> view.segmentInViewWorld(x1, z1, x2, z2),
                v -> view.toScreenX(v, 0, 0, contentW),
                v -> view.toScreenY(v, 0, 0, contentH),
                Math.max(1, thickness - 1),
                MapTheme.COLOR_PLANNED, MapTheme.COLOR_GENERATING,
                MapTheme.COLOR_COMPLETED, MapTheme.COLOR_FAILED,
                left, top, right, bottom);
        MapAutomaticPlanningSamplingOverlayRenderer.render(
                g,
                this.font,
                view,
                automaticPlanningSamplingBounds,
                contentW,
                contentH,
                left,
                top,
                right,
                bottom);
        MapSamplingOverlayRenderer.render(
                g,
                this.font,
                view,
                mapSamplingSnapshot,
                contentW,
                contentH,
                left,
                top,
                right,
                bottom);
        MapSamplingNoticeOverlayRenderer.render(
                g,
                this.font,
                currentMapSamplingNotice(),
                left,
                top,
                right,
                bottom);
        MapOverlayRenderer.renderManualModePreview(g, visibleSnapshot, view, inputHandler,
                state.hasSelection() ? state.getSelectedA() : null,
                mouseX, mouseY, contentW, contentH,
                left, top, right, bottom,
                computePointSize() * 2 + 4,
                computeThickness());
        if (!contextMenu.isOpen()
                && !dockLayout.contains(mouseX, mouseY)
                && !workspacePanel.contains(mouseX, mouseY, this.width, this.height, workspaceBottom)) {
            MapInteraction.renderHoverHighlight(g, visibleSnapshot, view, 0, 0, mapW, mapH,
                    0, mouseX, mouseY);
        }
        MapOverlayRenderer.renderPlayer(g, this.minecraft, inputHandler, view,
                contentW, contentH, left, top, right, bottom);
        g.disableScissor();
        int statusRight = workspacePanel.leftEdge(this.width, this.height, workspaceBottom);
        if (!workspacePanel.isOpen()) {
            MapLegendRenderer.render(g, this.font, this.width, visibleSnapshot);
        }
        MapStatusRenderer.render(g, this.font, this.width, statusRight, loadSession, samplingSession);
        if (!contextMenu.isOpen()
                && !dockLayout.contains(mouseX, mouseY)
                && !workspacePanel.contains(mouseX, mouseY, this.width, this.height, workspaceBottom)) {
            MapInteraction.renderHoverTooltip(g, this.font, visibleSnapshot, view, 0, 0, mapW, mapH,
                    0, mouseX, mouseY);
        }
        if (state.isZoomDebounceReady()) {
            state.clearZoomDebounce();
            onRequestView();
        }

        workspacePanel.render(g, this.font, this.width, this.height, workspaceBottom,
                snapshot, filterState, searchResults, searchLoading, searchFailed);
        renderSearchBox(g, mouseX, mouseY, partialTick);

        EnumSet<MapDockAction> activeActions = EnumSet.noneOf(MapDockAction.class);
        if (workspacePanel.isTab(MapWorkspacePanel.Tab.SEARCH)) activeActions.add(MapDockAction.SEARCH);
        if (workspacePanel.isTab(MapWorkspacePanel.Tab.FILTER)) activeActions.add(MapDockAction.FILTER);
        if (loadSession != null) activeActions.add(MapDockAction.REFRESH);
        if (mapSamplingSnapshot.active()) activeActions.add(MapDockAction.SAMPLE);
        if (state.isManualMode()) activeActions.add(MapDockAction.MANUAL_CONNECT);
        EnumSet<MapDockAction> disabledActions = EnumSet.noneOf(MapDockAction.class);
        if (!mapSampling.isAvailable(samplingLevel) || mapSamplingSnapshot.active()) {
            disabledActions.add(MapDockAction.SAMPLE);
        }
        int samplePercent = mapSamplingSnapshot.active() ? mapSamplingSnapshot.percent() : -1;
        MapDockRenderer.render(g, this.font, dockLayout, mouseX, mouseY,
                activeActions, disabledActions, samplePercent);
        contextMenu.render(g, this.font, mouseX, mouseY, this.width, this.height);
    }

    // ========== 输入处理 ==========
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        dockLayout = MapDockRenderer.layout(this.width, this.height);
        int workspaceBottom = workspaceBottomLimit(dockLayout);
        if (dockLayout.contains(mouseX, mouseY)) return true;
        if (workspacePanel.contains(mouseX, mouseY, this.width, this.height, workspaceBottom)) {
            workspacePanel.scroll(scrollY);
            return true;
        }
        return inputHandler.mouseScrolled(mouseX, mouseY, scrollY)
               || super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.isOpen()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        dockLayout = MapDockRenderer.layout(this.width, this.height);
        int workspaceBottom = workspaceBottomLimit(dockLayout);
        MapDockAction dockAction = dockLayout.hit(mouseX, mouseY);
        if (button == 0 && dockAction != null) {
            handleDockAction(dockAction);
            return true;
        }

        MapSnapshot visibleSnapshot = visibleSnapshot();
        if (workspacePanel.contains(mouseX, mouseY, this.width, this.height, workspaceBottom)) {
            MapWorkspacePanel.Hit hit = workspacePanel.hit(
                    mouseX, mouseY, this.width, this.height, workspaceBottom,
                    snapshot, filterState, searchResults);
            handleWorkspaceHit(hit, mouseX, mouseY, button);
            return true;
        }

        if (workspacePanel.isOpen()) {
            workspacePanel.close();
            if (searchBox != null) searchBox.setFocused(false);
        }

        if (button == 1 && inputHandler.insideMap(mouseX, mouseY)) {
            BlockPos target = inputHandler.findNearestStructure(visibleSnapshot, mouseX, mouseY);
            if (target != null) {
                openContextMenuFor(target, (int) mouseX, (int) mouseY);
                return true;
            }
        }

        if (button == 0 && !state.isManualMode() && inputHandler.insideMap(mouseX, mouseY)) {
            BlockPos target = inputHandler.findNearestStructure(visibleSnapshot, mouseX, mouseY);
            if (target != null) {
                workspacePanel.openDetails(target);
                return true;
            }
        }

        return inputHandler.mouseClicked(mouseX, mouseY, button, visibleSnapshot)
               || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY)
               || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.mouseReleased(mouseX, mouseY, button)
               || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (workspacePanel.isTab(MapWorkspacePanel.Tab.SEARCH) && searchBox != null && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitSearchNow();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                workspacePanel.close();
                return true;
            }
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && workspacePanel.isOpen()) {
            workspacePanel.close();
            return true;
        }
        return inputHandler.keyPressed(keyCode, scanCode, modifiers, this.minecraft)
               || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (workspacePanel.isTab(MapWorkspacePanel.Tab.SEARCH)
                && searchBox != null
                && searchBox.isFocused()
                && searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void handleDockAction(MapDockAction action) {
        switch (action) {
            case SEARCH -> {
                if (workspacePanel.isTab(MapWorkspacePanel.Tab.SEARCH)) {
                    workspacePanel.close();
                    if (searchBox != null) searchBox.setFocused(false);
                } else {
                    workspacePanel.openSearch();
                    if (searchBox != null) searchBox.setFocused(true);
                }
            }
            case FILTER -> {
                if (workspacePanel.isTab(MapWorkspacePanel.Tab.FILTER)) {
                    workspacePanel.close();
                } else {
                    workspacePanel.openFilter();
                }
                if (searchBox != null) searchBox.setFocused(false);
            }
            case REFRESH -> refreshCurrentView();
            case SAMPLE -> {
                ServerLevel level = MapViewportController.resolveSingleplayerLevel(this.minecraft);
                if (mapSampling.isAvailable(level) && !mapSampling.snapshot().active()) {
                    startMapSampling(level);
                }
            }
            case MANUAL_CONNECT -> {
                state.toggleManualMode();
                workspacePanel.close();
                if (searchBox != null) searchBox.setFocused(false);
            }
            case CONFIG -> onOpenConfig();
            case CLOSE -> onCloseScreen();
        }
    }

    private void handleWorkspaceHit(MapWorkspacePanel.Hit hit,
                                    double mouseX,
                                    double mouseY,
                                    int button) {
        if (button != 0 || hit == null) return;
        switch (hit.kind()) {
            case CLOSE -> {
                workspacePanel.close();
                if (searchBox != null) searchBox.setFocused(false);
            }
            case SEARCH_TAB -> {
                workspacePanel.openSearch();
                if (searchBox != null) searchBox.setFocused(true);
            }
            case FILTER_TAB -> {
                workspacePanel.openFilter();
                if (searchBox != null) searchBox.setFocused(false);
            }
            case DETAILS_TAB -> {
                if (workspacePanel.selectedStructure() != null) {
                    workspacePanel.openDetails(workspacePanel.selectedStructure());
                }
                if (searchBox != null) searchBox.setFocused(false);
            }
            case SEARCH_FIELD -> {
                if (searchBox != null) searchBox.mouseClicked(mouseX, mouseY, button);
            }
            case SEARCH_RESULT -> selectSearchResult((MapSearchResult) hit.value());
            case STATUS_FILTER -> {
                filterState.toggleStatus((ConnectionStatus) hit.value());
                invalidateFilteredSnapshot();
            }
            case SOURCE_FILTER -> {
                filterState.toggleSource((net.shiroha233.roadweaver.map.search.MapStructureSource) hit.value());
                invalidateFilteredSnapshot();
            }
            case TYPE_FILTER -> {
                filterState.toggleType((String) hit.value(), filterState.availableTypes(snapshot));
                invalidateFilteredSnapshot();
            }
            case RESET_FILTER -> {
                filterState.reset();
                invalidateFilteredSnapshot();
            }
            case DETAIL_TELEPORT -> {
                if (hit.value() instanceof BlockPos pos) onTeleportTo(pos);
            }
            case DETAIL_ALIAS -> {
                if (hit.value() instanceof BlockPos pos) openAliasDialog(pos);
            }
            case DETAIL_NOTE -> {
                if (hit.value() instanceof BlockPos pos) openNoteEditor(pos);
            }
            case NONE -> {
            }
        }
    }

    private void renderSearchBox(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!workspacePanel.isTab(MapWorkspacePanel.Tab.SEARCH) || searchBox == null) return;
        var rect = workspacePanel.searchField();
        if (rect == null) return;
        MapDockRenderer.fillRounded(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 5, MapTheme.PANEL_CONTROL_BG);
        searchBox.setX(rect.x() + 6);
        searchBox.setY(rect.y() + 1);
        searchBox.setWidth(Math.max(20, rect.width() - 12));
        searchBox.render(graphics, mouseX, mouseY, partialTick);
    }

    private void onSearchQueryChanged(String value) {
        searchQuery = value == null ? "" : value;
        pendingSearchQuery = searchQuery.trim();
        searchFailed = false;
        searchLoading = false;
        searchStartedAtMs = 0L;
        searchResults = ClientMapNotes.searchAliases(pendingSearchQuery, MapStructureSearchService.MAX_RESULTS);
        if (pendingSearchQuery.isEmpty()) {
            searchLoading = false;
            searchDeadlineMs = 0L;
            return;
        }
        searchDeadlineMs = System.currentTimeMillis() + 250L;
    }

    private void processSearchDebounce() {
        long now = System.currentTimeMillis();
        if (searchLoading && searchStartedAtMs > 0L && now - searchStartedAtMs > 8_000L) {
            searchLoading = false;
            searchFailed = true;
            searchStartedAtMs = 0L;
            searchRequestSeq++;
        }
        if (searchDeadlineMs <= 0L || now < searchDeadlineMs) return;
        submitSearchNow();
    }

    private void submitSearchNow() {
        String query = searchQuery.trim();
        searchDeadlineMs = 0L;
        if (query.isEmpty() || currentDimensionId == null) {
            searchLoading = false;
            searchResults = List.of();
            return;
        }
        submittedSearchQuery = query;
        searchLoading = true;
        searchFailed = false;
        searchStartedAtMs = System.currentTimeMillis();
        int requestSeq = ++searchRequestSeq;
        ClientNetBridge.requestSearch(requestSeq, currentDimensionId, query);
    }

    private void selectSearchResult(MapSearchResult result) {
        if (result == null) return;
        BlockPos pos = result.pos();
        view.centerOn(pos.getX(), pos.getZ(), mapW, mapH);
        workspacePanel.openDetails(pos);
        if (searchBox != null) searchBox.setFocused(false);
        onRequestView();
    }

    private void refreshCurrentView() {
        MapViewportController.RequestRect rect = MapViewportController.currentRequestRect(view);
        for (MapLoadPhase phase : MapLoadPhase.values()) {
            snapshotStore.clearRect(phase, rect);
        }
        snapshot = snapshotStore.snapshot();
        invalidateFilteredSnapshot();
        MapSnapshotCache.put(currentDimensionId, snapshot);
        loadSession = null;
        onRequestView();
    }

    private MapSnapshot visibleSnapshot() {
        if (filteredSnapshot == null
                || filteredSnapshotSource != snapshot
                || filteredSnapshotRevision != filterState.revision()) {
            filteredSnapshot = filterState.apply(snapshot);
            filteredSnapshotSource = snapshot;
            filteredSnapshotRevision = filterState.revision();
        }
        return filteredSnapshot;
    }

    private void invalidateFilteredSnapshot() {
        filteredSnapshot = null;
        filteredSnapshotSource = null;
        filteredSnapshotRevision = -1;
    }

    // ========== 回调实现 ==========
    
    @Override
    public void onCloseScreen() {
        this.onClose();
    }

    @Override
    public void onOpenConfig() {
        if (this.minecraft == null) return;
        var p = this.minecraft.player;
        if (p != null && this.minecraft.getSingleplayerServer() == null) {
            // 多人游戏：配置修改应限制为 OP（服务端权限等级 2+）
            if (!p.hasPermissions(2)) {
                p.displayClientMessage(Component.translatable("gui.roadweaver.map.config.denied"), true);
                return;
            }
        }
        try {
            Screen next = net.shiroha233.roadweaver.client.ConfigScreenFactory.createConfigScreen(this);
            if (next != null) {
                this.minecraft.setScreen(next);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRequestView() {
        if (!hasMapOpenPermission()) {
            denyMapAccessAndClose();
            return;
        }

        Minecraft mc = this.minecraft;
        if (mc == null) return;

        MapViewportController.RequestRect requestRect = MapViewportController.currentRequestRect(view);
        currentDimensionId = MapViewportController.syncDimensionAndRestoreCache(mc, currentDimensionId, cached -> {
            snapshotStore = MapSnapshotCache.store(mc.level.dimension().location());
            this.snapshot = snapshotStore.snapshot();
        });

        ResourceLocation did = (mc.level != null) ? mc.level.dimension().location() : currentDimensionId;
        if (did == null) {
            did = new ResourceLocation("minecraft", "overworld");
        }
        if (currentDimensionId == null || !did.equals(currentDimensionId)) {
            snapshotStore = MapSnapshotCache.store(did);
            snapshot = snapshotStore.snapshot();
        }
        currentDimensionId = did;

        List<MapViewportController.RequestRect> structureRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.STRUCTURES));
        List<MapViewportController.RequestRect> roadRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.ROADS));
        List<MapViewportController.RequestRect> connectionRects = MapViewportController.prioritizeMissingRects(
                requestRect, snapshotStore.loadedRects(MapLoadPhase.CONNECTIONS));
        List<MapLoadSession.ResponseRequest> requests;
        if (!structureRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(structureRects, List.of(), List.of());
        } else if (!roadRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), roadRects, List.of());
        } else if (!connectionRects.isEmpty()) {
            requests = MapLoadSession.ResponseRequest.fromRects(List.of(), List.of(), connectionRects);
        } else {
            loadSession = null;
            snapshot = snapshotStore.snapshot();
            MapSnapshotCache.put(did, snapshot);
            return;
        }

        int requestSeq = state.incrementAndGetRequestSeq();
        loadSession = new MapLoadSession(requestSeq, did, requests);
        dispatchRemoteSnapshotLoad(requestSeq, did, requests);
    }

    @Override
    public void onTeleportTo(BlockPos pos) {
        ClientNetBridge.requestTeleport(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void onManualConnect(BlockPos a, BlockPos b) {
        ClientNetBridge.requestManualConnect(a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    @Override
    public void onCenterToPlayer() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        int contentW = mapW;
        int contentH = mapH;
        view.calibrateInitialToPlayer(this.minecraft, contentW, contentH, MapTheme.GRID_TARGET_PX);
        onRequestView();
    }

    @Override
    public void onCenterToSpawn() {
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        
        BlockPos spawn = null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                spawn = level.getSharedSpawnPos();
            }
        }
        if (spawn == null && mc.level != null) {
            spawn = mc.level.getSharedSpawnPos();
        }
        if (spawn == null) return;
        
        int contentW = mapW;
        int contentH = mapH;
        view.centerOn(spawn.getX(), spawn.getZ(), contentW, contentH);
        onRequestView();
    }

    private void startMapSampling(ServerLevel level) {
        MapViewportController.RequestRect rect = MapViewportController.currentRequestRect(view);
        MapTerrainSamplingService.StartResult result = mapSampling.start(
                level,
                new MapSamplingBounds(rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ()));
        Component message = switch (result) {
            case STARTED -> Component.translatable("gui.roadweaver.map.sample.started");
            case ALREADY_RUNNING -> Component.translatable("gui.roadweaver.map.sample.running");
            case WORLD_UNAVAILABLE -> Component.translatable("gui.roadweaver.map.sample.singleplayer_only");
            case MODE_UNAVAILABLE -> Component.translatable("gui.roadweaver.map.sample.mode_unavailable");
            case RANGE_TOO_LARGE -> Component.translatable("gui.roadweaver.map.sample.range_too_large");
            case SUBMISSION_FAILED -> Component.translatable("gui.roadweaver.map.sample.failed");
        };
        notifyMapSampling(message);
    }

    private void handleSamplingTransition(MapSamplingSnapshot current) {
        if (current == null || current.stage() == observedSamplingStage) return;
        observedSamplingStage = current.stage();
        if (current.stage() == MapSamplingSnapshot.Stage.COMPLETED) {
            terrainTiles.clear();
            refreshStructuresAfterSampling(current.bounds());
            notifyMapSampling(Component.translatable("gui.roadweaver.map.sample.completed"));
        } else if (current.stage() == MapSamplingSnapshot.Stage.FAILED) {
            notifyMapSampling(Component.translatable("gui.roadweaver.map.sample.failed"));
        }
    }

    private void refreshStructuresAfterSampling(MapSamplingBounds bounds) {
        if (bounds == null || currentDimensionId == null) return;
        MapViewportController.RequestRect sampledRect = new MapViewportController.RequestRect(
                bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        snapshotStore.clearRect(MapLoadPhase.STRUCTURES, sampledRect);
        snapshot = snapshotStore.snapshot();
        invalidateFilteredSnapshot();
        MapSnapshotCache.put(currentDimensionId, snapshot);
        onRequestView();
    }

    private void notifyMapSampling(Component message) {
        if (message == null) return;
        mapSamplingNotice = message;
        mapSamplingNoticeExpiresAtMs = System.currentTimeMillis() + MAP_SAMPLING_NOTICE_DURATION_MS;
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(message, true);
        }
    }

    private Component currentMapSamplingNotice() {
        if (mapSamplingNotice == null) return null;
        if (System.currentTimeMillis() < mapSamplingNoticeExpiresAtMs) {
            return mapSamplingNotice;
        }
        mapSamplingNotice = null;
        return null;
    }

    public void acceptSnapshot(int requestSeq, ResourceLocation dimensionId, MapSnapshot snapshot) {
        acceptSnapshotPart(requestSeq, dimensionId, MapLoadPhase.CONNECTIONS, 0, snapshot);
    }

    public void acceptPatch(ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (dimensionId == null || patch == null) return;
        if (currentDimensionId != null && !dimensionId.equals(currentDimensionId)) return;
        snapshotStore.apply(patch);
        this.snapshot = snapshotStore.snapshot();
        MapSnapshotCache.put(dimensionId, this.snapshot);
    }

    public void acceptSearchResults(int requestSeq,
                                    ResourceLocation dimensionId,
                                    boolean success,
                                    List<MapSearchResult> serverResults) {
        if (requestSeq != searchRequestSeq || dimensionId == null || !dimensionId.equals(currentDimensionId)) return;
        if (!submittedSearchQuery.equals(searchQuery.trim())) return;
        searchLoading = false;
        searchStartedAtMs = 0L;
        searchFailed = !success;
        if (!success) return;

        java.util.LinkedHashMap<Long, MapSearchResult> merged = new java.util.LinkedHashMap<>();
        for (MapSearchResult local : ClientMapNotes.searchAliases(searchQuery, MapStructureSearchService.MAX_RESULTS)) {
            merged.put(searchResultKey(local.pos()), local);
        }
        if (serverResults != null) {
            for (MapSearchResult result : serverResults) {
                if (result == null || result.pos() == null) continue;
                if (!merged.containsKey(searchResultKey(result.pos()))
                        && merged.size() >= MapStructureSearchService.MAX_RESULTS) break;
                merged.put(searchResultKey(result.pos()), result);
            }
        }
        searchResults = List.copyOf(merged.values());
    }

    public void acceptSnapshotPart(int requestSeq,
                                   ResourceLocation dimensionId,
                                   MapLoadPhase phase,
                                   int responseIndex,
                                   MapSnapshot snapshot) {
        if (dimensionId == null || snapshot == null) return;
        if (requestSeq != state.getCurrentRequestSeq()) return;
        if (currentDimensionId != null && !dimensionId.equals(currentDimensionId)) return;
        if (loadSession == null || !loadSession.accepts(requestSeq, dimensionId)) return;

        MapLoadSession.ResponseRequest request = loadSession.requestAt(responseIndex);
        MapViewportController.RequestRect loadedRect = request != null ? request.rect() : null;
        MapLoadPhase loadedPhase = request != null ? request.phase() : phase;

        snapshotStore.merge(loadedPhase, loadedRect, snapshot);
        loadSession.markReceived(loadedPhase, responseIndex, snapshot);
        this.snapshot = snapshotStore.snapshot();
        MapSnapshotCache.put(dimensionId, this.snapshot);
        if (loadSession.isComplete()) {
            loadSession = null;
            onRequestView();
        }
    }

    private void scheduleSingleplayerSnapshotLoad(Minecraft mc,
                                                  ServerLevel level,
                                                  int requestSeq,
                                                  List<MapLoadSession.ResponseRequest> requests) {
        ResourceLocation dimensionId = level.dimension().location();
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            MapViewportController.RequestRect rect = request.rect();
            scheduleSingleplayerPhase(mc, requestSeq, dimensionId, request.phase(), responseIndex,
                    () -> buildSnapshotForPhase(level, request.phase(), rect));
        }
    }

    private MapSnapshot buildSnapshotForPhase(ServerLevel level,
                                             MapLoadPhase phase,
                                             MapViewportController.RequestRect rect) {
        return switch (phase) {
            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        };
    }

    private int scheduleSingleplayerPhase(Minecraft mc,
                                          int requestSeq,
                                          ResourceLocation dimensionId,
                                          MapLoadPhase phase,
                                          int responseIndex,
                                          java.util.function.Supplier<MapSnapshot> supplier) {
        final int currentIndex = responseIndex;
        CompletableFuture
                .supplyAsync(supplier, ComputeService.mapExecutor())
                .thenAccept(snap -> mc.execute(() -> acceptSnapshotPart(requestSeq, dimensionId, phase, currentIndex, snap)));
        return responseIndex + 1;
    }

    private void dispatchRemoteSnapshotLoad(int requestSeq,
                                            ResourceLocation dimensionId,
                                            List<MapLoadSession.ResponseRequest> requests) {
        for (int responseIndex = 0; responseIndex < requests.size(); responseIndex++) {
            MapLoadSession.ResponseRequest request = requests.get(responseIndex);
            MapViewportController.RequestRect rect = request.rect();
            ClientNetBridge.requestSnapshot(requestSeq, dimensionId, request.phase(), responseIndex,
                    rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
        }
    }

    // ========== 辅助方法 ==========
    
    private void computeMapRect() {
        mapX = 0;
        mapY = 0;
        mapW = this.width;
        mapH = this.height;
    }

    private static int workspaceBottomLimit(MapDockRenderer.DockLayout layout) {
        return Math.max(1, layout.bounds().y() - WORKSPACE_DOCK_GAP);
    }

    private int computeThickness() {
        int contentW = mapW;
        int contentH = mapH;
        double ppb = Math.min(view.pxPerBlockX(contentW), view.pxPerBlockZ(contentH));
        int t = (int) Math.round(ppb);
        return Math.max(MapTheme.MIN_THICKNESS, Math.min(t, MapTheme.MAX_THICKNESS));
    }

    private int computePointSize() {
        return MapTheme.BASE_POINT_SIZE + computeThickness();
    }

    private void openContextMenuFor(BlockPos target, int x, int y) {
        contextMenu.clearItems();
        contextMenu.addItem(MENU_TELEPORT, () -> onTeleportTo(target));
        contextMenu.addSeparator();
        contextMenu.addItem(MENU_SET_ALIAS, () -> openAliasDialog(target));
        contextMenu.addItem(MENU_EDIT_NOTE, () -> openNoteEditor(target));
        contextMenu.open(x, y);
    }

    /** 打开别名设置对话框 */
    private void openAliasDialog(BlockPos target) {
        if (this.minecraft == null) return;
        String currentAlias = ClientMapNotes.getAlias(target);
        this.minecraft.setScreen(new SimpleTextInputScreen(
                DIALOG_ALIAS_TITLE,
                currentAlias != null ? currentAlias : "",
                alias -> ClientMapNotes.setAlias(target, alias),
                this  // 返回地图界面
        ));
    }

    /** 打开笔记编辑器（书与笔风格） */
    private void openNoteEditor(BlockPos target) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new NoteEditScreen(target, this));
    }

    private boolean hasMapOpenPermission() {
        Minecraft mc = this.minecraft;
        if (mc == null) {
            return true;
        }
        if (!ClientMapAccessGuard.isAllowedOrUnknown()) {
            return false;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return true;
        }
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        return serverPlayer == null || MapAccessService.canOpenMap(serverPlayer);
    }

    private TerrainSamplingSessionSnapshot syncTerrainLayer(Minecraft minecraft) {
        if (minecraft == null) return null;
        ServerLevel level = MapViewportController.resolveSingleplayerLevel(minecraft);
        if (level == null) return null;
        TerrainSamplingSessionSnapshot session = TerrainSamplingSessions.forLevel(level).snapshot();
        terrainTiles.selectTerrainLayer(session.effectiveMode() == TerrainSamplingMode.FULL_REGION
                ? MapTileLayer.TERRAIN_ACCURATE
                : MapTileLayer.TERRAIN_COARSE);
        return session;
    }

    private List<AutomaticPlanningSamplingBounds> activeAutomaticPlanningSamplingBounds(ServerLevel level) {
        if (level != null) {
            return AutomaticPlanningSamplingActivities.snapshot(level);
        }
        return MapAutomaticPlanningSamplingCache.snapshot(currentDimensionId);
    }

    private static long searchResultKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private void denyMapAccessAndClose() {
        ClientMapAccessGuard.notifyDenied(this.minecraft);
        MapSnapshotCache.clearNow();
        this.onClose();
    }
}
