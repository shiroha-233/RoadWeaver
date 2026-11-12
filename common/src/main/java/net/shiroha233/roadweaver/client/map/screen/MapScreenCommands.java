package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.ConfigScreenFactory;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.cache.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.cache.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.query.MapSearchController;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.client.map.ui.NoteEditScreen;
import net.shiroha233.roadweaver.client.ui.SimpleTextInputScreen;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.map.search.MapSearchResult;
import net.shiroha233.roadweaver.map.search.MapStructureSource;
import net.shiroha233.roadweaver.network.platform.ClientNetBridge;

/**
 * 地图屏幕的命令层：把停靠栏、工作区与右键菜单上的用户意图翻译为具体动作。
 */
final class MapScreenCommands {

    private static final Component MENU_TELEPORT = Component.translatable("gui.roadweaver.map.menu.teleport");

    private static final Component MENU_SET_ALIAS = Component.translatable("gui.roadweaver.map.menu.set_alias");

    private static final Component MENU_EDIT_NOTE = Component.translatable("gui.roadweaver.map.menu.edit_note");

    private static final Component DIALOG_ALIAS_TITLE = Component.translatable("gui.roadweaver.map.dialog.alias_title");

    private final RoadMapScreen screen;

    MapScreenCommands(RoadMapScreen screen) {
        this.screen = screen;
    }

    void handleDockAction(MapDockAction action) {
        MapWorkspacePanel panel = screen.panel();
        switch (action) {
            case SEARCH -> {
                if (panel.isTab(MapWorkspacePanel.Tab.SEARCH)) {
                    panel.close();
                    screen.focusSearchBox(false);
                } else {
                    panel.openSearch();
                    screen.focusSearchBox(true);
                }
            }
            case FILTER -> {
                if (panel.isTab(MapWorkspacePanel.Tab.FILTER)) {
                    panel.close();
                } else {
                    panel.openFilter();
                }
                screen.focusSearchBox(false);
            }
            case REFRESH -> screen.refreshCurrentView();
            case SAMPLE -> {
                ServerLevel level = screen.singleplayerLevel();
                if (screen.sampling().isAvailable(level) && !screen.sampling().snapshot().active()) {
                    screen.startMapSampling(level);
                }
            }
            case MANUAL_CONNECT -> {
                screen.state().toggleManualMode();
                panel.close();
                screen.focusSearchBox(false);
            }
            case CONFIG -> openConfig();
            case CLOSE -> screen.onCloseScreen();
        }
    }

    void openConfig() {
        Minecraft minecraft = screen.client();
        if (minecraft == null) {
            return;
        }
        if (minecraft.player != null && minecraft.getSingleplayerServer() == null
                && !minecraft.player.hasPermissions(2)) {
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.roadweaver.map.config.denied"), true);
            return;
        }
        try {
            Screen next = ConfigScreenFactory.createConfigScreen(screen);
            if (next != null) {
                minecraft.setScreen(next);
            }
        } catch (Throwable ignored) {
        }
    }

    void handleWorkspaceHit(MapWorkspacePanel.Hit hit, double mouseX, double mouseY, int button) {
        if (button != 0 || hit == null) {
            return;
        }
        MapWorkspacePanel panel = screen.panel();
        switch (hit.kind()) {
            case CLOSE -> {
                panel.close();
                screen.focusSearchBox(false);
            }
            case SEARCH_TAB -> {
                panel.openSearch();
                screen.focusSearchBox(true);
            }
            case FILTER_TAB -> {
                panel.openFilter();
                screen.focusSearchBox(false);
            }
            case DETAILS_TAB -> {
                if (panel.selectedStructure() != null) {
                    panel.openDetails(panel.selectedStructure());
                }
                screen.focusSearchBox(false);
            }
            case SEARCH_FIELD -> screen.clickSearchBox(mouseX, mouseY, button);
            case SEARCH_RESULT -> selectSearchResult((MapSearchResult) hit.value());
            case STATUS_FILTER -> {
                screen.filterState().toggleStatus((ConnectionStatus) hit.value());
                screen.data().invalidateFiltered();
            }
            case SOURCE_FILTER -> {
                screen.filterState().toggleSource((MapStructureSource) hit.value());
                screen.data().invalidateFiltered();
            }
            case TYPE_FILTER -> {
                screen.filterState().toggleType((String) hit.value(),
                        screen.filterState().availableTypes(screen.data().snapshot()));
                screen.data().invalidateFiltered();
            }
            case RESET_FILTER -> {
                screen.filterState().reset();
                screen.data().invalidateFiltered();
            }
            case DETAIL_TELEPORT -> {
                if (hit.value() instanceof BlockPos pos) {
                    screen.onTeleportTo(pos);
                }
            }
            case DETAIL_ALIAS -> {
                if (hit.value() instanceof BlockPos pos) {
                    openAliasDialog(pos);
                }
            }
            case DETAIL_NOTE -> {
                if (hit.value() instanceof BlockPos pos) {
                    openNoteEditor(pos);
                }
            }
            case NONE -> {
            }
        }
    }

    void selectSearchResult(MapSearchResult result) {
        if (result == null) {
            return;
        }
        BlockPos pos = result.pos();
        screen.view().centerOn(pos.getX(), pos.getZ(), screen.mapWidth(), screen.mapHeight());
        screen.panel().openDetails(pos);
        screen.focusSearchBox(false);
        screen.onRequestView();
    }

    void openContextMenuFor(BlockPos target, int x, int y) {
        screen.contextMenu().clearItems();
        screen.contextMenu().addItem(MENU_TELEPORT, () -> screen.onTeleportTo(target));
        screen.contextMenu().addSeparator();
        screen.contextMenu().addItem(MENU_SET_ALIAS, () -> openAliasDialog(target));
        screen.contextMenu().addItem(MENU_EDIT_NOTE, () -> openNoteEditor(target));
        screen.contextMenu().open(x, y);
    }

    void openAliasDialog(BlockPos target) {
        Minecraft minecraft = screen.client();
        if (minecraft == null) {
            return;
        }
        String currentAlias = ClientMapNotes.getAlias(target);
        minecraft.setScreen(new SimpleTextInputScreen(
                DIALOG_ALIAS_TITLE,
                currentAlias != null ? currentAlias : "",
                alias -> ClientMapNotes.setAlias(target, alias),
                screen
        ));
    }

    void openNoteEditor(BlockPos target) {
        Minecraft minecraft = screen.client();
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new NoteEditScreen(target, screen));
    }

    void onManualConnect(BlockPos from, BlockPos to) {
        ClientNetBridge.requestManualConnect(from.getX(), from.getZ(), to.getX(), to.getZ());
    }

    void onTeleportTo(BlockPos pos) {
        ClientNetBridge.requestTeleport(pos.getX(), pos.getY(), pos.getZ());
    }

    void onCenterToPlayer() {
        Minecraft minecraft = screen.client();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        screen.view().calibrateInitialToPlayer(minecraft, screen.mapWidth(), screen.mapHeight(),
                net.shiroha233.roadweaver.client.map.MapTheme.GRID_TARGET_PX);
        screen.onRequestView();
    }

    void onCenterToSpawn() {
        Minecraft minecraft = screen.client();
        if (minecraft == null) {
            return;
        }
        BlockPos spawn = resolveSpawn(minecraft);
        if (spawn == null) {
            return;
        }
        screen.view().centerOn(spawn.getX(), spawn.getZ(), screen.mapWidth(), screen.mapHeight());
        screen.onRequestView();
    }

    private static BlockPos resolveSpawn(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                return level.getSharedSpawnPos();
            }
        }
        return minecraft.level != null ? minecraft.level.getSharedSpawnPos() : null;
    }

    boolean hasMapOpenPermission() {
        Minecraft minecraft = screen.client();
        if (minecraft == null) {
            return true;
        }
        if (!ClientMapAccessGuard.isAllowedOrUnknown()) {
            return false;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return true;
        }
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(minecraft.player.getUUID());
        return serverPlayer == null || MapAccessService.canOpenMap(serverPlayer);
    }

    void denyMapAccessAndClose() {
        ClientMapAccessGuard.notifyDenied(screen.client());
        MapSnapshotCache.clearNow();
        screen.onCloseScreen();
    }

    void notifyDenied() {
        ClientMapAccessGuard.notifyDenied(screen.client());
    }

    MapSearchController search() {
        return screen.search();
    }
}
