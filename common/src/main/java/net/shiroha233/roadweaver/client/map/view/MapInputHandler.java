package net.shiroha233.roadweaver.client.map.view;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.MapState;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.map.data.MapSnapshot;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 地图输入处理器 - 处理所有鼠标和键盘输入
 */
public final class MapInputHandler {
    
    public interface Callbacks {
        void onCloseScreen();
        void onOpenConfig();
        void onRequestView();
        void onTeleportTo(BlockPos pos);
        void onManualConnect(BlockPos a, BlockPos b);
        void onCenterToPlayer();
        void onCenterToSpawn();
    }

    private final MapState state;
    private final MapView view;
    private final Callbacks callbacks;

    private int mapX, mapY, mapW, mapH;
    private int innerPad;

    public MapInputHandler(MapState state, MapView view, Callbacks callbacks) {
        this.state = state;
        this.view = view;
        this.callbacks = callbacks;
    }

    public void updateLayout(int mapX, int mapY, int mapW, int mapH, int innerPad) {
        this.mapX = mapX;
        this.mapY = mapY;
        this.mapW = mapW;
        this.mapH = mapH;
        this.innerPad = innerPad;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!insideMap(mouseX, mouseY)) return false;

        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        double factor = delta > 0 ? 0.9 : 1.1;
        double cx = view.screenToWorldX(mouseX, mapX, innerPad, contentW);
        double cz = view.screenToWorldZ(mouseY, mapY, innerPad, contentH);
        view.applyZoomAround(cx, cz, factor, contentW, contentH, MapTheme.GRID_TARGET_PX);
        
        state.scheduleZoomDebounce(MapTheme.ZOOM_DEBOUNCE_MS);
        state.closeContextMenu();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, MapSnapshot snapshot) {
        if (state.isContextMenuOpen()) {
            state.closeContextMenu();
        }

        if (state.isManualMode() && insideMap(mouseX, mouseY) && button == 0) {
            BlockPos best = findNearestStructure(snapshot, mouseX, mouseY);
            if (best != null) {
                if (!state.hasSelection()) {
                    state.selectFirstPoint(best);
                } else if (state.getSelectedA().equals(best)) {
                    state.clearSelection();
                } else {
                    callbacks.onManualConnect(state.getSelectedA(), best);
                    state.clearSelection();
                    callbacks.onRequestView();
                }
                state.closeContextMenu();
                return true;
            }
        }

        if (insideMap(mouseX, mouseY) && button == 0) {
            state.startDrag(button, mouseX, mouseY);
            return true;
        }

        if (insideMap(mouseX, mouseY) && button == 1) {
            BlockPos best = findNearestStructure(snapshot, mouseX, mouseY);
            if (best != null) {
                state.openContextMenu((int) mouseX, (int) mouseY, best);
                return true;
            }
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!state.isDragging() || button != state.getDragButton()) {
            return false;
        }

        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        double dx = mouseX - state.getLastMouseX();
        double dy = mouseY - state.getLastMouseY();
        view.panByScreenDelta(dx, dy, contentW, contentH);
        state.updateDrag(mouseX, mouseY);
        view.lockAspect(contentW, contentH);
        state.closeContextMenu();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!state.isDragging() || button != state.getDragButton()) {
            return false;
        }

        state.endDrag();
        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        view.clampZoom(contentW, contentH, MapTheme.GRID_TARGET_PX);
        state.cancelZoomDebounce();
        callbacks.onRequestView();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers, Minecraft mc) {
        if (mc != null) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if ("key.roadweaver.open_map".equals(mapping.getName()) && mapping.matches(keyCode, scanCode)) {
                    while (mapping.consumeClick()) {}
                    callbacks.onCloseScreen();
                    return true;
                }
            }
        }

        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        double panStep = 50;

        switch (keyCode) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> {
                view.panByScreenDelta(0, panStep, contentW, contentH);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> {
                view.panByScreenDelta(0, -panStep, contentW, contentH);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                view.panByScreenDelta(panStep, 0, contentW, contentH);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                view.panByScreenDelta(-panStep, 0, contentW, contentH);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                double cx = (view.getMinX() + view.getMaxX()) / 2;
                double cz = (view.getMinZ() + view.getMaxZ()) / 2;
                view.applyZoomAround(cx, cz, 0.8, contentW, contentH, MapTheme.GRID_TARGET_PX);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                double cx = (view.getMinX() + view.getMaxX()) / 2;
                double cz = (view.getMinZ() + view.getMaxZ()) / 2;
                view.applyZoomAround(cx, cz, 1.25, contentW, contentH, MapTheme.GRID_TARGET_PX);
                callbacks.onRequestView();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                callbacks.onCenterToPlayer();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                callbacks.onCenterToSpawn();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (state.isContextMenuOpen()) {
                    state.closeContextMenu();
                    return true;
                }
                if (state.isManualMode()) {
                    state.setManualMode(false);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean insideMap(double x, double y) {
        return x >= mapX + innerPad && x <= mapX + mapW - innerPad 
            && y >= mapY + innerPad && y <= mapY + mapH - innerPad;
    }

    public BlockPos findNearestStructure(MapSnapshot snapshot, double mouseX, double mouseY) {
        if (snapshot == null) return null;
        
        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        int bestDist = Integer.MAX_VALUE;
        BlockPos best = null;
        
        List<BlockPos> structures = snapshot.structures();
        for (BlockPos p : structures) {
            if (!view.isInViewWorld(p.getX(), p.getZ())) continue;
            int x = view.toScreenX(p.getX(), mapX, innerPad, contentW);
            int y = view.toScreenY(p.getZ(), mapY, innerPad, contentH);
            int dx = (int) Math.abs(x - mouseX);
            int dy = (int) Math.abs(y - mouseY);
            int d2 = dx * dx + dy * dy;
            if (d2 < bestDist) {
                bestDist = d2;
                best = p;
            }
        }
        
        if (best != null && bestDist <= MapTheme.STRUCTURE_CLICK_RADIUS_SQ) {
            return best;
        }
        return null;
    }
}
