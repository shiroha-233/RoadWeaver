package net.shiroha233.roadweaver.client.map.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.render.MapDockAction;
import net.shiroha233.roadweaver.client.map.render.MapDockRenderer;
import net.shiroha233.roadweaver.client.map.ui.MapWorkspacePanel;
import net.shiroha233.roadweaver.map.data.MapSnapshot;


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

    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (screen.contextMenu().isOpen()) {
            if (screen.contextMenu().contains(mouseX, mouseY)
                    && screen.contextMenu().mouseClicked(event, doubleClick)) {
                return true;
            }
            // 点击菜单外部时只关闭菜单，并继续分发本次点击（例如停靠栏的关闭按钮）。
            screen.contextMenu().close();
        }
        MapDockAction dockAction = screen.dockLayout().hit(mouseX, mouseY);
        if (button == InputConstants.MOUSE_BUTTON_LEFT && dockAction != null) {
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
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            screen.commands().openContextMenuFor(target, (int) mouseX, (int) mouseY);
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && !screen.state().isManualMode()) {
            panel.openDetails(target);
            return true;
        }
        return screen.inputHandler().mouseClicked(mouseX, mouseY, button, visibleSnapshot);
    }

    boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return screen.inputHandler().mouseDragged(event, dragX, dragY);
    }

    boolean mouseReleased(MouseButtonEvent event) {
        return screen.inputHandler().mouseReleased(event);
    }

    boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        MapWorkspacePanel panel = screen.panel();
        if (panel.isTab(MapWorkspacePanel.Tab.SEARCH) && screen.searchBoxFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                screen.search().submitNow(screen.data().dimensionId());
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                screen.focusSearchBox(false);
                panel.close();
                return true;
            }
            screen.forwardSearchKey(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE && panel.isOpen()) {
            panel.close();
            return true;
        }
        return screen.inputHandler().keyPressed(keyCode, scanCode, modifiers, screen.client());
    }

    boolean charTyped(CharacterEvent event) {
        return screen.forwardSearchChar((char) event.codepoint(), 0);
    }
}
