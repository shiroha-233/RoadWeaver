package net.countered.settlementroads.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 渲染工具类 - 提供基础绘制方法
 */
public class RenderUtils {
    
    /**
     * 绘制实线
     */
    public static void drawLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
        ctx.pose().pushPose();
        if (Math.abs(x1 - x0) > Math.abs(y1 - y0)) {
            int left = Math.min(x0, x1);
            int right = Math.max(x0, x1);
            ctx.fill(left, y0, right + 1, y0 + 1, color);
        } else {
            int top = Math.min(y0, y1);
            int bottom = Math.max(y0, y1);
            ctx.fill(x0, top, x0 + 1, bottom + 1, color);
        }
        ctx.pose().popPose();
    }
    
    /**
     * 绘制虚线 - 用于连接线
     */
    public static void drawDashedLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        
        int dashLength = 5;
        int gapLength = 3;
        int counter = 0;
        boolean drawing = true;
        
        int x = x0;
        int y = y0;
        
        while (true) {
            if (drawing) {
                ctx.fill(x, y, x + 1, y + 1, color);
            }
            
            counter++;
            if (drawing && counter >= dashLength) {
                drawing = false;
                counter = 0;
            } else if (!drawing && counter >= gapLength) {
                drawing = true;
                counter = 0;
            }
            
            if (x == x1 && y == y1) break;
            
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    /**
     * 填充圆形
     */
    public static void fillCircle(GuiGraphics ctx, int cx, int cy, int r, int color) {
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                if (x * x + y * y <= r * r) {
                    ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
                }
            }
        }
    }
    
    /**
     * 绘制圆形轮廓
     */
    public static void drawCircleOutline(GuiGraphics ctx, int cx, int cy, int r, int color) {
        // 使用中点圆算法绘制圆圈轮廓
        int x = 0, y = r;
        int d = 3 - 2 * r;

        while (x <= y) {
            ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
            ctx.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
            ctx.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
            ctx.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
            ctx.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
            ctx.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
            ctx.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
            ctx.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);

            if (d < 0) {
                d = d + 4 * x + 6;
            } else {
                d = d + 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }
    
    /**
     * 绘制面板
     */
    public static void drawPanel(GuiGraphics ctx, int x1, int y1, int x2, int y2, int bg, int border) {
        ctx.fill(x1, y1, x2, y2, bg);
        ctx.fill(x1, y1, x2, y1 + 1, border);
        ctx.fill(x1, y2 - 1, x2, y2, border);
        ctx.fill(x1, y1, x1 + 1, y2, border);
        ctx.fill(x2 - 1, y1, x2, y2, border);
    }
    
    /**
     * 填充垂直线
     */
    public static void fillV(GuiGraphics ctx, int x, int y1, int y2, int color) {
        ctx.fill(x, y1, x + 1, y2, color);
    }
    
    /**
     * 填充水平线
     */
    public static void fillH(GuiGraphics ctx, int x1, int x2, int y, int color) {
        ctx.fill(x1, y, x2, y + 1, color);
    }
    
    /**
     * 绘制小标签
     */
    public static void drawSmallLabel(GuiGraphics ctx, String text, int x, int y) {
        Font font = Minecraft.getInstance().font;
        ctx.drawString(font, text, x, y, 0x80FFFFFF, false);
    }
}
