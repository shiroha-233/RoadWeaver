package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.shiroha233.roadweaver.client.map.MapTheme;

import java.util.List;

/**
 * 主动采样操作的地图内反馈层。
 */
public final class MapSamplingNoticeOverlayRenderer {
    private static final int HORIZONTAL_PADDING = 7;
    private static final int VERTICAL_PADDING = 5;
    private static final int OUTER_MARGIN = 6;
    private static final int MAX_TEXT_WIDTH = 360;
    private static final int TOP_HUD_CLEARANCE = 112;

    private MapSamplingNoticeOverlayRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              Component message,
                              int left,
                              int top,
                              int right,
                              int bottom) {
        if (message == null || right <= left || bottom <= top) return;

        int availableWidth = Math.max(1, right - left - OUTER_MARGIN * 2);
        int maxTextWidth = Math.min(MAX_TEXT_WIDTH,
                Math.max(1, availableWidth - HORIZONTAL_PADDING * 2));
        List<FormattedCharSequence> lines = font.split(message, maxTextWidth);
        if (lines.isEmpty()) return;

        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = Math.min(availableWidth, textWidth + HORIZONTAL_PADDING * 2);
        int boxHeight = lines.size() * font.lineHeight + VERTICAL_PADDING * 2;
        int minimumY = top + TOP_HUD_CLEARANCE;
        int maximumY = Math.max(top + OUTER_MARGIN, bottom - boxHeight - OUTER_MARGIN);
        int boxX = left + (right - left - boxWidth) / 2;
        int boxY = Math.min(Math.max(top + OUTER_MARGIN, minimumY), maximumY);

        renderSurface(graphics, boxX, boxY, boxWidth, boxHeight);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font,
                    lines.get(index),
                    boxX + HORIZONTAL_PADDING,
                    boxY + VERTICAL_PADDING + index * font.lineHeight,
                    MapTheme.COLOR_MAP_SAMPLING_TEXT,
                    false);
        }
    }

    private static void renderSurface(GuiGraphics graphics, int x, int y, int width, int height) {
        MapDockRenderer.fillRounded(graphics, x + 2, y + 3, width, height, 8, MapTheme.PANEL_SHADOW);
        MapDockRenderer.fillRounded(graphics, x, y, width, height, 8, MapTheme.PANEL_BG);
        MapDockRenderer.fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 7, MapTheme.PANEL_HIGHLIGHT);
        MapDockRenderer.fillRounded(graphics, x + 2, y + 2, width - 4, height - 4, 6, MapTheme.PANEL_BG);
    }
}
