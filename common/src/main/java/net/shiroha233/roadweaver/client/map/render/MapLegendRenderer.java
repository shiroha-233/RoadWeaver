package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;

public final class MapLegendRenderer {
    private static final int SCREEN_MARGIN = MapTheme.HUD_TOP_MARGIN;
    private static final int PADDING_X = 9;
    private static final int PADDING_Y = 7;
    private static final int ROW_STEP = 13;
    private static final int ROW_GAP = 1;
    private static final int SWATCH_WIDTH = 12;
    private static final int SWATCH_HEIGHT = 4;
    private static final int SWATCH_LABEL_GAP = 7;
    private static final int LABEL_COUNT_GAP = 7;

    private MapLegendRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              int screenWidth,
                              MapSnapshot snapshot) {
        if (snapshot == null) return;

        LegendEntry[] entries = entries(snapshot);
        int width = clampWidth(width(font, entries), screenWidth);
        int height = PADDING_Y * 2 + entries.length * ROW_STEP - ROW_GAP;
        int x = Math.max(0, screenWidth - SCREEN_MARGIN - width);
        int y = SCREEN_MARGIN;
        renderSurface(graphics, x, y, width, height);

        int rowY = y + PADDING_Y;
        int swatchX = x + PADDING_X;
        int labelX = swatchX + SWATCH_WIDTH + SWATCH_LABEL_GAP;
        int right = x + width - PADDING_X;
        for (LegendEntry entry : entries) {
            String count = fitText(font, Integer.toString(entry.count()),
                    Math.max(0, right - labelX));
            int countX = right - font.width(count);
            int labelWidth = Math.max(0, countX - labelX - LABEL_COUNT_GAP);
            Component label = fitLabel(font, entry.label(), labelWidth);
            graphics.drawString(font, label, labelX, rowY,
                    MapTheme.PANEL_MUTED, false);
            if (!count.isEmpty()) {
                graphics.drawString(font, count, countX, rowY,
                        MapTheme.PANEL_TEXT, false);
            }
            renderSwatch(graphics, swatchX, rowY + 4, entry);
            rowY += ROW_STEP;
        }
    }

    private static int width(Font font, LegendEntry[] entries) {
        int textWidth = 0;
        for (LegendEntry entry : entries) {
            textWidth = Math.max(textWidth,
                    font.width(entry.label())
                            + LABEL_COUNT_GAP
                            + font.width(Integer.toString(entry.count())));
        }
        return PADDING_X * 2 + SWATCH_WIDTH + SWATCH_LABEL_GAP + textWidth;
    }

    static int clampWidth(int contentWidth, int screenWidth) {
        int availableWidth = Math.max(1, (screenWidth - SCREEN_MARGIN * 3) / 2);
        return Math.min(contentWidth, availableWidth);
    }

    private static LegendEntry[] entries(MapSnapshot snapshot) {
        return new LegendEntry[] {
                new LegendEntry(label("structures"),
                        snapshot.structuresCount(), MapTheme.COLOR_STRUCTURE, true, false),
                new LegendEntry(label("planned"),
                        snapshot.plannedCount(), MapTheme.COLOR_PLANNED, false, false),
                new LegendEntry(label("generating"),
                        snapshot.generatingCount(), MapTheme.COLOR_GENERATING, false, true),
                new LegendEntry(label("completed"),
                        snapshot.completedCount(), MapTheme.COLOR_COMPLETED, false, false),
                new LegendEntry(label("failed"),
                        snapshot.failedCount(), MapTheme.COLOR_FAILED, false, false)
        };
    }

    private static Component label(String key) {
        return Component.translatable("gui.roadweaver.map.legend." + key);
    }

    private static void renderSwatch(GuiGraphics graphics, int x, int y, LegendEntry entry) {
        if (entry.structure()) {
            graphics.fill(x, y - 1, x + SWATCH_WIDTH, y + SWATCH_HEIGHT + 1, MapTheme.PANEL_MUTED);
            graphics.fill(x + 2, y, x + SWATCH_WIDTH - 2, y + SWATCH_HEIGHT, entry.color());
            return;
        }
        if (entry.dashed()) {
            graphics.fill(x, y, x + 5, y + SWATCH_HEIGHT, entry.color());
            graphics.fill(x + 8, y, x + SWATCH_WIDTH, y + SWATCH_HEIGHT, entry.color());
            return;
        }
        graphics.fill(x, y, x + SWATCH_WIDTH, y + SWATCH_HEIGHT, entry.color());
    }

    private static Component fitLabel(Font font, Component label, int maxWidth) {
        if (font.width(label) <= maxWidth) return label;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (suffixWidth >= maxWidth) {
            return Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(0, maxWidth)));
        }
        String prefix = font.plainSubstrByWidth(label.getString(), maxWidth - suffixWidth);
        return Component.literal(prefix + suffix);
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (suffixWidth >= maxWidth) {
            return font.plainSubstrByWidth(text, maxWidth);
        }
        return font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix;
    }

    private static void renderSurface(GuiGraphics graphics, int x, int y, int width, int height) {
        MapDockRenderer.fillRounded(graphics, x + 2, y + 3, width, height, 10, MapTheme.PANEL_SHADOW);
        MapDockRenderer.fillRounded(graphics, x, y, width, height, 10, MapTheme.PANEL_BG);
        MapDockRenderer.fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 9, MapTheme.PANEL_HIGHLIGHT);
        MapDockRenderer.fillRounded(graphics, x + 2, y + 2, width - 4, height - 4, 8, MapTheme.PANEL_BG);
    }

    private record LegendEntry(Component label,
                               int count,
                               int color,
                               boolean structure,
                               boolean dashed) {}
}
