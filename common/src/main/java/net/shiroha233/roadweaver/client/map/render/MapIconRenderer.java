package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.GuiGraphics;

public final class MapIconRenderer {
    private MapIconRenderer() {}

    public static void render(GuiGraphics graphics, MapDockAction action, int centerX, int centerY, int color, int size) {
        int s = Math.max(12, size);
        int half = s / 2;
        int left = centerX - half;
        int top = centerY - half;
        int right = centerX + half;
        int bottom = centerY + half;
        int clip = s + 4;
        switch (action) {
            case SEARCH -> search(graphics, centerX, centerY, color, s, clip);
            case FILTER -> filter(graphics, left, top, right, bottom, color);
            case REFRESH -> refresh(graphics, centerX, centerY, color, s, clip);
            case SAMPLE -> sample(graphics, centerX, centerY, color, s, clip);
            case MANUAL_CONNECT -> connect(graphics, centerX, centerY, color, s, clip);
            case CONFIG -> config(graphics, centerX, centerY, color, s, clip);
            case CLOSE -> close(graphics, centerX, centerY, color, s, clip);
        }
    }

    private static void search(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int radius = Math.max(4, size / 3);
        line(g, cx - radius, cy - radius, cx + radius, cy + radius, color, 2, clip);
        line(g, cx + radius - 1, cy + radius - 1, cx + size / 2, cy + size / 2, color, 2, clip);
        eraseCenter(g, cx + 1, cy + 1, radius - 2);
    }

    private static void filter(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        int cx = (left + right) / 2;
        int y = top + 3;
        line(g, left + 2, y, right - 2, y, color, 2, 20);
        line(g, left + 5, y + 5, right - 5, y + 5, color, 2, 20);
        line(g, left + 8, y + 10, right - 8, y + 10, color, 2, 20);
        line(g, cx, y + 10, cx, bottom - 1, color, 2, 20);
    }

    private static void refresh(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int radius = Math.max(5, size / 3);
        line(g, cx - radius, cy, cx - radius / 2, cy - radius, color, 2, clip);
        line(g, cx - radius / 2, cy - radius, cx + radius, cy - radius / 2, color, 2, clip);
        line(g, cx + radius, cy - radius / 2, cx + radius, cy + radius / 2, color, 2, clip);
        line(g, cx + radius, cy + radius / 2, cx + radius / 2, cy + radius, color, 2, clip);
        line(g, cx + radius / 2, cy + radius, cx - radius, cy + radius / 2, color, 2, clip);
        line(g, cx - radius, cy + radius / 2, cx - radius, cy, color, 2, clip);
        g.fill(cx - radius - 1, cy - 1, cx - radius + 3, cy + 3, color);
    }

    private static void sample(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int radius = Math.max(5, size / 3);
        line(g, cx - radius, cy, cx + radius, cy, color, 1, clip);
        line(g, cx, cy - radius, cx, cy + radius, color, 1, clip);
        line(g, cx - radius, cy - radius, cx - radius + 3, cy - radius, color, 2, clip);
        line(g, cx - radius, cy - radius, cx - radius, cy - radius + 3, color, 2, clip);
        line(g, cx + radius, cy + radius, cx + radius - 3, cy + radius, color, 2, clip);
        line(g, cx + radius, cy + radius, cx + radius, cy + radius - 3, color, 2, clip);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
    }

    private static void connect(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int offset = Math.max(4, size / 3);
        line(g, cx - offset + 1, cy - offset + 1, cx + offset - 1, cy + offset - 1, color, 2, clip);
        g.fill(cx - offset - 2, cy - offset - 2, cx - offset + 3, cy - offset + 3, color);
        g.fill(cx + offset - 2, cy + offset - 2, cx + offset + 3, cy + offset + 3, color);
        g.fill(cx - offset, cy - offset, cx - offset + 1, cy - offset + 1, 0xFF1A1A1A);
        g.fill(cx + offset, cy + offset, cx + offset + 1, cy + offset + 1, 0xFF1A1A1A);
    }

    private static void config(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int radius = Math.max(4, size / 3);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int x = cx + (int) Math.round(Math.cos(angle) * (radius + 2));
            int y = cy + (int) Math.round(Math.sin(angle) * (radius + 2));
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
        g.fill(cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, color);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF1A1A1A);
    }

    private static void close(GuiGraphics g, int cx, int cy, int color, int size, int clip) {
        int radius = Math.max(5, size / 3);
        line(g, cx - radius, cy - radius, cx + radius, cy + radius, color, 2, clip);
        line(g, cx + radius, cy - radius, cx - radius, cy + radius, color, 2, clip);
    }

    private static void eraseCenter(GuiGraphics g, int cx, int cy, int radius) {
        if (radius > 0) {
            g.fill(cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, 0xFF1A1A1A);
        }
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness, int clip) {
        RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness,
                x1 - clip, y1 - clip, x1 + clip, y1 + clip);
    }
}
