package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 渲染工具类 - 提供基础图形绘制方法
 */
public final class RenderUtils {
    private RenderUtils() {}

    public static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int left, int top, int right, int bottom) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            if (x1 >= left && x1 <= right && y1 >= top && y1 <= bottom) g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        float fx = x1;
        float fy = y1;
        float sx = (x2 - x1) / (float) steps;
        float sy = (y2 - y1) / (float) steps;
        for (int i = 0; i <= steps; i++) {
            int px = Math.round(fx);
            int py = Math.round(fy);
            if (px >= left && px <= right && py >= top && py <= bottom) {
                g.fill(px, py, px + 1, py + 1, color);
            }
            fx += sx;
            fy += sy;
        }
    }

    public static void drawThickLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness, int left, int top, int right, int bottom) {
        thickness = Math.max(1, thickness);
        if (thickness == 1) { drawLine(g, x1, y1, x2, y2, color, left, top, right, bottom); return; }
        int half = thickness / 2;
        for (int ox = -half; ox <= half; ox++) {
            for (int oy = -half; oy <= half; oy++) {
                drawLine(g, x1 + ox, y1 + oy, x2 + ox, y2 + oy, color, left, top, right, bottom);
            }
        }
    }

    public static void drawDashedLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int dash, int gap, int left, int top, int right, int bottom) {
        dash = Math.max(1, dash);
        gap = Math.max(1, gap);
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            if (x1 >= left && x1 <= right && y1 >= top && y1 <= bottom) g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        float fx = x1;
        float fy = y1;
        float sx = (x2 - x1) / (float) steps;
        float sy = (y2 - y1) / (float) steps;
        int pattern = dash + gap;
        for (int i = 0; i <= steps; i++) {
            int idx = i % pattern;
            if (idx < dash) {
                int px = Math.round(fx);
                int py = Math.round(fy);
                if (px >= left && px <= right && py >= top && py <= bottom) {
                    g.fill(px, py, px + 1, py + 1, color);
                }
            }
            fx += sx;
            fy += sy;
        }
    }

    public static void drawThickDashedLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness, int dash, int gap, int left, int top, int right, int bottom) {
        thickness = Math.max(1, thickness);
        if (thickness == 1) { drawDashedLine(g, x1, y1, x2, y2, color, dash, gap, left, top, right, bottom); return; }
        int half = thickness / 2;
        for (int ox = -half; ox <= half; ox++) {
            for (int oy = -half; oy <= half; oy++) {
                drawDashedLine(g, x1 + ox, y1 + oy, x2 + ox, y2 + oy, color, dash, gap, left, top, right, bottom);
            }
        }
    }

    public static void fillTriangle(GuiGraphics g, int x1, int y1, int x2, int y2, int x3, int y3, int color, int left, int top, int right, int bottom) {
        if (y2 < y1) { int tx = x1, ty = y1; x1 = x2; y1 = y2; x2 = tx; y2 = ty; }
        if (y3 < y1) { int tx = x1, ty = y1; x1 = x3; y1 = y3; x3 = tx; y3 = ty; }
        if (y3 < y2) { int tx = x2, ty = y2; x2 = x3; y2 = y3; x3 = tx; y3 = ty; }
        if (y1 == y3) return;
        float inv12 = y2 != y1 ? (x2 - x1) / (float)(y2 - y1) : 0f;
        float inv13 = (x3 - x1) / (float)(y3 - y1);
        float inv23 = y3 != y2 ? (x3 - x2) / (float)(y3 - y2) : 0f;
        float sx12 = x1, sx13 = x1;
        for (int y = y1; y < y2; y++) {
            if (y < top || y > bottom) { sx12 += inv12; sx13 += inv13; continue; }
            int xa = Math.round(Math.min(sx12, sx13));
            int xb = Math.round(Math.max(sx12, sx13));
            xa = Math.max(xa, left); xb = Math.min(xb, right);
            if (xa <= xb) g.fill(xa, y, xb + 1, y + 1, color);
            sx12 += inv12; sx13 += inv13;
        }
        float sx23 = x2, sx13b = x1 + inv13 * (y2 - y1);
        for (int y = y2; y <= y3; y++) {
            if (y < top || y > bottom) { sx23 += inv23; sx13b += inv13; continue; }
            int xa = Math.round(Math.min(sx23, sx13b));
            int xb = Math.round(Math.max(sx23, sx13b));
            xa = Math.max(xa, left); xb = Math.min(xb, right);
            if (xa <= xb) g.fill(xa, y, xb + 1, y + 1, color);
            sx23 += inv23; sx13b += inv13;
        }
    }
}
