package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.MapState;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.client.map.RevocableMapScreen;
import net.shiroha233.roadweaver.client.map.cache.MapFilterState;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.query.MapSearchController;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.map.tile.SingleplayerTerrainTileManager;
import net.shiroha233.roadweaver.client.map.ui.ContextMenu;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.client.map.view.MapInputHandler;
import net.shiroha233.roadweaver.client.map.view.MapViewportController;
import net.shiroha233.roadweaver.core.model.MapLoadPhase;
import net.shiroha233.roadweaver.core.model.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.RequestRect;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSearchService;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;
import net.shiroha233.roadweaver.planning.pipeline.TerrainSamplingSessionSnapshot;

import java.util.List;


/**
 * 道路地图界面
 */
public class RoadMapScreen extends Screen implements MapInputHandler.Callbacks, RevocableMapScreen {

    private static final int WORKSPACE_DOCK_GAP = 8;

    private final MapState state = new MapState();

    private final MapView view = new MapView();

    private final MapFilterState filterState = new MapFilterState();

    private final MapScreenData data = new MapScreenData(state, view, filterState);

    private final MapScreenSampling sampling = new MapScreenSampling();

    private final MapScreenCommands commands = new MapScreenCommands(this);

    private final ContextMenu contextMenu = new ContextMenu();

    private final MapWorkspacePanel workspacePanel = new MapWorkspacePanel();

    private final SingleplayerTerrainTileManager terrainTiles = new SingleplayerTerrainTileManager();

    private final MapSearchController search = new MapSearchController();

    private final MapInputHandler inputHandler;

    private final MapScreenInput input;

    private MapDockRenderer.DockLayout dockLayout;

    private EditBox searchBox;

    private int mapW;

    private int mapH;

    public RoadMapScreen() {
        super(Component.translatable("gui.roadweaver.map.title"));
        this.inputHandler = new MapInputHandler(state, view, this);
        this.input = new MapScreenInput(this);
    }

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
        searchBox.setValue(search.query());
        searchBox.setResponder(search::onQueryChanged);
        if (!commands.hasMapOpenPermission()) {
            commands.denyMapAccessAndClose();
            return;
        }
        MapSnapshotCache.cancelClear();
        Minecraft minecraft = client();
        data.bindFromLevel(minecraft);
        syncTerrainLayer(minecraft);
        mapW = this.width;
        mapH = this.height;
        inputHandler.updateLayout(0, 0, mapW, mapH, 0);
        data.resetView();
        if (minecraft != null && minecraft.player != null) {
            view.calibrateInitialToPlayer(minecraft, mapW, mapH, MapTheme.GRID_TARGET_PX);
        }
        onRequestView();
    }

    @Override
    public void removed() {
        super.removed();
        terrainTiles.clear();
        data.clearSession();
        data.persistStore();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapScreenRenderer.render(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (input.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (input.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (input.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (input.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (input.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onCloseScreen() {
        this.onClose();
    }

    @Override
    public void onOpenConfig() {
        commands.openConfig();
    }

    @Override
    public void onRequestView() {
        if (!commands.hasMapOpenPermission()) {
            commands.denyMapAccessAndClose();
            return;
        }
        data.requestView(client());
    }

    @Override
    public void onTeleportTo(BlockPos pos) {
        commands.onTeleportTo(pos);
    }

    @Override
    public void onManualConnect(BlockPos from, BlockPos to) {
        commands.onManualConnect(from, to);
    }

    @Override
    public void onCenterToPlayer() {
        commands.onCenterToPlayer();
    }

    @Override
    public void onCenterToSpawn() {
        commands.onCenterToSpawn();
    }

    @Override
    public void onMapAccessRevoked() {
        commands.notifyDenied();
    }

    public void acceptPatch(ResourceLocation dimensionId, MapSnapshotPatch patch) {
        data.applyPatch(dimensionId, patch);
    }

    public void acceptSearchResults(int requestSeq,
                                    ResourceLocation dimensionId,
                                    boolean success,
                                    List<MapSearchResult> serverResults) {
        if (dimensionId == null || !dimensionId.equals(data.dimensionId())) {
            return;
        }
        search.accept(requestSeq, success, serverResults);
    }

    public void acceptSnapshotPart(int requestSeq,
                                   ResourceLocation dimensionId,
                                   MapLoadPhase phase,
                                   int responseIndex,
                                   MapSnapshot snapshot) {
        if (data.acceptSnapshotPart(requestSeq, dimensionId, phase, responseIndex, snapshot)) {
            onRequestView();
        }
    }

    void refreshCurrentView() {
        data.refreshView();
        onRequestView();
    }

    void startMapSampling(ServerLevel level) {
        sampling.startAndNotify(client(), level, MapViewportController.currentRequestRect(view));
    }

    void onSamplingPolled(MapSamplingSnapshot current) {
        sampling.pollAndNotify(client(), current, () -> {
            terrainTiles.clear();
            if (current != null && current.bounds() != null) {
                data.clearStructures(new RequestRect(current.bounds().minX(), current.bounds().minZ(),
                        current.bounds().maxX(), current.bounds().maxZ()));
            }
            onRequestView();
        });
    }

    TerrainSamplingSessionSnapshot syncTerrainLayer(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        return terrainTiles.syncSamplingLayer(MapViewportController.resolveSingleplayerLevel(minecraft));
    }

    void focusSearchBox(boolean focused) {
        if (searchBox != null) {
            searchBox.setFocused(focused);
        }
    }

    void clickSearchBox(double mouseX, double mouseY, int button) {
        if (searchBox != null) {
            searchBox.mouseClicked(mouseX, mouseY, button);
        }
    }

    boolean searchBoxFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    void forwardSearchKey(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    boolean forwardSearchChar(char codePoint, int modifiers) {
        return searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers);
    }

    void applyDockLayout(MapDockRenderer.DockLayout layout) {
        this.dockLayout = layout;
    }

    MapDockRenderer.DockLayout dockLayout() {
        MapDockRenderer.DockLayout layout = MapDockRenderer.layout(width, height);
        this.dockLayout = layout;
        return layout;
    }

    int workspaceBottom() {
        MapDockRenderer.DockLayout layout = this.dockLayout != null ? this.dockLayout : dockLayout();
        return Math.max(1, layout.bounds().y() - WORKSPACE_DOCK_GAP);
    }

    int mapWidth() {
        return mapW;
    }

    int mapHeight() {
        return mapH;
    }

    Font font() {
        return this.font;
    }

    Minecraft client() {
        return this.minecraft;
    }

    MapState state() {
        return state;
    }

    MapView view() {
        return view;
    }

    MapFilterState filterState() {
        return filterState;
    }

    MapScreenData data() {
        return data;
    }

    MapScreenCommands commands() {
        return commands;
    }

    MapScreenSampling sampling() {
        return sampling;
    }

    MapSearchController search() {
        return search;
    }

    MapWorkspacePanel panel() {
        return workspacePanel;
    }

    ContextMenu contextMenu() {
        return contextMenu;
    }

    MapInputHandler inputHandler() {
        return inputHandler;
    }

    SingleplayerTerrainTileManager terrainTiles() {
        return terrainTiles;
    }

    EditBox searchBox() {
        return searchBox;
    }

    ServerLevel singleplayerLevel() {
        return MapViewportController.resolveSingleplayerLevel(client());
    }
}
