package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.MapView;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingBounds;
import net.shiroha233.roadweaver.map.tile.sampling.MapSamplingSnapshot;

/**
 * 主动地图采样的视口叠加层。
 */
public final class MapSamplingOverlayRenderer {
    private static final int FRAME_THICKNESS = 2;
    private static final int LABEL_PADDING_X = 5;
    private static final int LABEL_PADDING_Y = 3;

    private MapSamplingOverlayRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              MapView view,
                              MapSamplingSnapshot snapshot,
                              int contentWidth,
                              int contentHeight,
                              int left,
                              int top,
                              int right,
                              int bottom) {
        if (snapshot == null || !snapshot.active() || snapshot.bounds() == null) return;

        MapSamplingBounds bounds = snapshot.bounds();
        renderBounds(
                graphics,
                font,
                view,
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ(),
                Component.translatable("gui.roadweaver.map.sample.progress", snapshot.percent()),
                contentWidth,
                contentHeight,
                left,
                top,
                right,
                bottom);
    }

    static void renderBounds(GuiGraphics graphics,
                             Font font,
                             MapView view,
                             int minBlockX,
                             int minBlockZ,
                             int maxBlockX,
                             int maxBlockZ,
                             Component label,
                             int contentWidth,
                             int contentHeight,
                             int left,
                             int top,
                             int right,
                             int bottom) {
        if (graphics == null || font == null || view == null || label == null) return;

        int rawX0 = view.toScreenX(minBlockX, 0, 0, contentWidth);
        int rawY0 = view.toScreenY(minBlockZ, 0, 0, contentHeight);
        int rawX1 = view.toScreenX(maxBlockX, 0, 0, contentWidth);
        int rawY1 = view.toScreenY(maxBlockZ, 0, 0, contentHeight);
        int minX = Math.min(rawX0, rawX1);
        int minY = Math.min(rawY0, rawY1);
        int maxX = Math.max(rawX0, rawX1);
        int maxY = Math.max(rawY0, rawY1);
        if (maxX < left || minX >= right || maxY < top || minY >= bottom) return;

        int x0 = clamp(minX, left, right - 1);
        int y0 = clamp(minY, top, bottom - 1);
        int x1 = clamp(maxX, left, right - 1);
        int y1 = clamp(maxY, top, bottom - 1);
        drawFrame(graphics, x0, y0, x1, y1);
        drawLabel(graphics, font, label, x0, y0, x1, y1, left, top, right, bottom);
    }

    private static void drawFrame(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        int horizontalEnd = Math.max(x0 + 1, x1 + 1);
        int verticalEnd = Math.max(y0 + 1, y1 + 1);
        graphics.fill(x0, y0, horizontalEnd, Math.min(verticalEnd, y0 + FRAME_THICKNESS),
                MapTheme.COLOR_MAP_SAMPLING_FRAME);
        graphics.fill(x0, Math.max(y0, y1 - FRAME_THICKNESS + 1), horizontalEnd, verticalEnd,
                MapTheme.COLOR_MAP_SAMPLING_FRAME);
        graphics.fill(x0, y0, Math.min(horizontalEnd, x0 + FRAME_THICKNESS), verticalEnd,
                MapTheme.COLOR_MAP_SAMPLING_FRAME);
        graphics.fill(Math.max(x0, x1 - FRAME_THICKNESS + 1), y0, horizontalEnd, verticalEnd,
                MapTheme.COLOR_MAP_SAMPLING_FRAME);
    }

    private static void drawLabel(GuiGraphics graphics,
                                  Font font,
                                  Component label,
                                  int x0,
                                  int y0,
                                  int x1,
                                  int y1,
                                  int left,
                                  int top,
                                  int right,
                                  int bottom) {
        int labelWidth = font.width(label);
        int boxWidth = labelWidth + LABEL_PADDING_X * 2;
        int boxHeight = font.lineHeight + LABEL_PADDING_Y * 2;
        int centerX = x0 + (x1 - x0) / 2;
        int centerY = y0 + (y1 - y0) / 2;
        int boxX = clamp(centerX - boxWidth / 2, left, Math.max(left, right - boxWidth));
        int boxY = clamp(centerY - boxHeight / 2, top, Math.max(top, bottom - boxHeight));
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, MapTheme.MAP_SAMPLING_LABEL_BG);
        graphics.drawString(font, label,
                boxX + LABEL_PADDING_X,
                boxY + LABEL_PADDING_Y,
                MapTheme.COLOR_MAP_SAMPLING_TEXT,
                false);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
