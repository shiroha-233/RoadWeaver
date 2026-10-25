package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.ui.Rect;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class MapDockRenderer {
    private static final MapDockAction[] ACTIONS = MapDockAction.values();
    private static final int SCREEN_EDGE_MARGIN = 6;
    private static final int BOTTOM_MARGIN = 12;

    private MapDockRenderer() {}

    public record DockLayout(Rect bounds, Map<MapDockAction, Rect> buttons, int cellSize) {
        public DockLayout {
            buttons = Map.copyOf(buttons);
        }

        public Rect button(MapDockAction action) {
            return buttons.get(action);
        }

        public MapDockAction hit(double x, double y) {
            if (!bounds.contains(x, y)) return null;
            for (MapDockAction action : ACTIONS) {
                Rect rect = buttons.get(action);
                if (rect != null && rect.contains(x, y)) return action;
            }
            return null;
        }

        public boolean contains(double x, double y) {
            return bounds.contains(x, y);
        }
    }

    public static DockLayout layout(int screenWidth, int screenHeight) {
        int cell = screenWidth >= 720 ? 30 : screenWidth >= 480 ? 28 : 24;
        int gap = Math.max(2, cell / 10);
        int horizontalPadding = Math.max(5, cell / 5);
        int outerHeight = cell + horizontalPadding * 2;
        int width = horizontalPadding * 2 + ACTIONS.length * cell + (ACTIONS.length - 1) * gap;
        int availableWidth = Math.max(ACTIONS.length, screenWidth - SCREEN_EDGE_MARGIN * 2);
        if (width > availableWidth) {
            gap = 2;
            horizontalPadding = 4;
            cell = Math.max(8,
                    (availableWidth - horizontalPadding * 2 - (ACTIONS.length - 1) * gap) / ACTIONS.length);
            outerHeight = cell + horizontalPadding * 2;
            width = horizontalPadding * 2 + ACTIONS.length * cell + (ACTIONS.length - 1) * gap;
        }
        int x = Math.max(SCREEN_EDGE_MARGIN, (screenWidth - width) / 2);
        int y = Math.max(SCREEN_EDGE_MARGIN, screenHeight - outerHeight - BOTTOM_MARGIN);
        Rect bounds = new Rect(x, y, width, outerHeight);
        EnumMap<MapDockAction, Rect> buttons = new EnumMap<>(MapDockAction.class);
        int buttonX = x + horizontalPadding;
        int buttonY = y + horizontalPadding;
        for (MapDockAction action : ACTIONS) {
            buttons.put(action, new Rect(buttonX, buttonY, cell, cell));
            buttonX += cell + gap;
        }
        return new DockLayout(bounds, buttons, cell);
    }

    public static void render(GuiGraphics graphics,
                              Font font,
                              DockLayout layout,
                              int mouseX,
                              int mouseY,
                              Set<MapDockAction> active,
                              Set<MapDockAction> disabled,
                              int samplePercent) {
        int x = layout.bounds().x();
        int y = layout.bounds().y();
        int w = layout.bounds().width();
        int h = layout.bounds().height();
        fillRounded(graphics, x + 2, y + 3, w, h, 10, MapTheme.DOCK_SHADOW);
        fillRounded(graphics, x, y, w, h, 10, MapTheme.DOCK_BG);
        fillRounded(graphics, x + 1, y + 1, w - 2, h - 2, 9, MapTheme.DOCK_HIGHLIGHT);
        fillRounded(graphics, x + 2, y + 2, w - 4, h - 4, 8, MapTheme.DOCK_BG);

        Set<MapDockAction> activeActions = active == null ? EnumSet.noneOf(MapDockAction.class) : active;
        Set<MapDockAction> disabledActions = disabled == null ? EnumSet.noneOf(MapDockAction.class) : disabled;
        for (MapDockAction action : ACTIONS) {
            Rect button = layout.button(action);
            boolean hovered = button != null && button.contains(mouseX, mouseY);
            boolean isDisabled = disabledActions.contains(action);
            boolean isActive = activeActions.contains(action);
            if (isActive || hovered) {
                int bg = isDisabled ? MapTheme.DOCK_DISABLED_ACTIVE : isActive ? MapTheme.DOCK_ACTIVE : MapTheme.DOCK_HOVER;
                fillRounded(graphics, button.x(), button.y(), button.width(), button.height(), 7, bg);
            }
            int color = isDisabled ? MapTheme.DOCK_ICON_DISABLED
                    : isActive ? MapTheme.DOCK_ICON_ACTIVE
                    : hovered ? MapTheme.DOCK_ICON_HOVER
                    : MapTheme.DOCK_ICON;
            int iconSize = Math.max(14, Math.round(layout.cellSize() * 0.52f));
            MapIconRenderer.render(graphics, action,
                    button.x() + button.width() / 2,
                    button.y() + button.height() / 2,
                    color,
                    iconSize);
            if (action == MapDockAction.SAMPLE && samplePercent >= 0 && samplePercent < 100) {
                renderProgress(graphics, button, samplePercent);
            }
            if (isActive) {
                graphics.fill(button.x() + button.width() / 2 - 3,
                        button.bottom() - 3,
                        button.x() + button.width() / 2 + 3,
                        button.bottom() - 2,
                        MapTheme.DOCK_INDICATOR);
            }
        }

        MapDockAction hovered = layout.hit(mouseX, mouseY);
        if (hovered != null) {
            Component tooltip = Component.translatable(hovered.tooltipKey());
            graphics.renderTooltip(font, tooltip, mouseX, mouseY - 4);
        }
    }

    private static void renderProgress(GuiGraphics graphics, Rect button, int percent) {
        int width = Math.max(4, button.width() - 10);
        int filled = Math.max(1, Math.round(width * Math.max(0, Math.min(100, percent)) / 100.0f));
        int y = button.bottom() - 4;
        graphics.fill(button.x() + 5, y, button.x() + 5 + width, y + 2, MapTheme.DOCK_PROGRESS_TRACK);
        graphics.fill(button.x() + 5, y, button.x() + 5 + filled, y + 2, MapTheme.DOCK_PROGRESS);
    }

    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - 1 - i;
            graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }
}
