package net.shiroha233.roadweaver.client.map.screen;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.map.data.MapSnapshot;

import org.lwjgl.glfw.GLFW;

/**
 * 地图屏幕的输入分发：先判定停靠栏与工作区命中，再交给地图交互处理。
 */
final class MapScreenInput {

    private final RoadMapScreen screen;

    MapScreenInput(RoadMapScreen screen) {
        this.screen = screen;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        MapDockRenderer.DockLayout layout = screen.dockLayout();
        if (layout.contains(mouseX, mouseY)) {
            return true;
        }
        if (screen.panel().contains(mouseX, mouseY, screen.width, screen.height,
                screen.workspaceBottom())) {
            screen.panel().scroll(scrollY);
            return true;
        }
        return screen.inputHandler().mouseScrolled(mouseX, mouseY, scrollY);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (screen.contextMenu().isOpen() && screen.contextMenu().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        MapDockAction dockAction = screen.dockLayout().hit(mouseX, mouseY);
        if (button == 0 && dockAction != null) {
            screen.commands().handleDockAction(dockAction);
            return true;
        }
        MapSnapshot visibleSnapshot = screen.data().visibleSnapshot();
        int workspaceBottom = screen.workspaceBottom();
        MapWorkspacePanel panel = screen.panel();
        if (panel.contains(mouseX, mouseY, screen.width, screen.height, workspaceBottom)) {
            MapWorkspacePanel.Hit hit = panel.hit(mouseX, mouseY, screen.width, screen.height,
                    workspaceBottom, screen.data().snapshot(), screen.filterState(), screen.search().results());
            screen.commands().handleWorkspaceHit(hit, mouseX, mouseY, button);
            return true;
        }
        if (panel.isOpen()) {
            panel.close();
            screen.focusSearchBox(false);
        }
        BlockPos target = screen.inputHandler().insideMap(mouseX, mouseY)
                ? screen.inputHandler().findNearestStructure(visibleSnapshot, mouseX, mouseY)
                : null;
        if (target == null) {
            return screen.inputHandler().mouseClicked(mouseX, mouseY, button, visibleSnapshot);
        }
        if (button == 1) {
            screen.commands().openContextMenuFor(target, (int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && !screen.state().isManualMode()) {
            panel.openDetails(target);
            return true;
        }
        return screen.inputHandler().mouseClicked(mouseX, mouseY, button, visibleSnapshot);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return screen.inputHandler().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return screen.inputHandler().mouseReleased(mouseX, mouseY, button);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MapWorkspacePanel panel = screen.panel();
        if (panel.isTab(MapWorkspacePanel.Tab.SEARCH) && screen.searchBoxFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                screen.search().submitNow(screen.data().dimensionId());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.focusSearchBox(false);
                panel.close();
                return true;
            }
            screen.forwardSearchKey(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && panel.isOpen()) {
            panel.close();
            return true;
        }
        return screen.inputHandler().keyPressed(keyCode, scanCode, modifiers, screen.client());
    }

    boolean charTyped(char codePoint, int modifiers) {
        return screen.forwardSearchChar(codePoint, modifiers);
    }
}
