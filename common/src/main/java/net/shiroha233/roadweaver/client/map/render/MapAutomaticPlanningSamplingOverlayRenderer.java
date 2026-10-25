package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.planning.terrain.AutomaticPlanningSamplingBounds;

import java.util.List;

/**
 * 自动规划采样范围的地图叠加层。
 */
public final class MapAutomaticPlanningSamplingOverlayRenderer {
    private static final Component LABEL = Component.translatable("gui.roadweaver.map.auto_planning.sampling");

    private MapAutomaticPlanningSamplingOverlayRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              MapView view,
                              List<AutomaticPlanningSamplingBounds> bounds,
                              int contentWidth,
                              int contentHeight,
                              int left,
                              int top,
                              int right,
                              int bottom) {
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        for (AutomaticPlanningSamplingBounds bound : bounds) {
            if (bound == null) {
                continue;
            }
            MapSamplingOverlayRenderer.renderBounds(
                    graphics,
                    font,
                    view,
                    bound.minX(),
                    bound.minZ(),
                    bound.maxX(),
                    bound.maxZ(),
                    LABEL,
                    contentWidth,
                    contentHeight,
                    left,
                    top,
                    right,
                    bottom);
        }
    }
}
