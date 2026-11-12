package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 网格渲染器 - 绘制地图网格和刻度
 */
public final class GridRenderer {
    private GridRenderer() {}

    public static void render(GuiGraphics g,
                              Font font,
                              int mapX, int mapY, int mapW, int mapH,
                              int innerPad,
                              double viewMinX, double viewMaxX,
                              double viewMinZ, double viewMaxZ,
                              int colorGrid,
                              int gridTargetPx,
                              int colorText) {
        int left = mapX + innerPad;
        int top = mapY + innerPad;
        int right = mapX + mapW - innerPad;
        int bottom = mapY + mapH - innerPad;

        int contentW = mapW - innerPad * 2;
        int contentH = mapH - innerPad * 2;
        double ppbX = contentW / Math.max(1.0, (viewMaxX - viewMinX));
        double ppbZ = contentH / Math.max(1.0, (viewMaxZ - viewMinZ));
        double ppb = (ppbX + ppbZ) * 0.5;
        int step = snapStep((int) Math.round(gridTargetPx / Math.max(0.001, ppb)));

        int startX = (int)Math.floor(viewMinX / step) * step;
        int endX = (int)Math.ceil(viewMaxX / step) * step;
        for (int wx = startX; wx <= endX; wx += step) {
            int sx = toScreenX(wx, mapX, mapW, innerPad, viewMinX, viewMaxX);
            if (sx >= left && sx <= right) g.fill(sx, top, sx + 1, bottom, colorGrid);
        }

        int startZ = (int)Math.floor(viewMinZ / step) * step;
        int endZ = (int)Math.ceil(viewMaxZ / step) * step;
        for (int wz = startZ; wz <= endZ; wz += step) {
            int sy = toScreenY(wz, mapY, mapH, innerPad, viewMinZ, viewMaxZ);
            if (sy >= top && sy <= bottom) g.fill(left, sy, right, sy + 1, colorGrid);
        }

        Component labelComp;
        if (step % 16 == 0) {
            int chunks = step / 16;
            labelComp = Component.translatable("gui.roadweaver.map.grid.scale.chunks_blocks", chunks, step);
        } else {
            labelComp = Component.translatable("gui.roadweaver.map.grid.scale.blocks", step);
        }

        int pad = 4;
        int tw = font.width(labelComp);
        int tx = right - tw - pad;
        int ty = bottom - font.lineHeight - pad;
        g.drawString(font, labelComp, tx, ty, colorText, false);
    }


    private static int toScreenX(int blockX, int mapX, int mapW, int innerPad, double viewMinX, double viewMaxX) {
        int contentW = mapW - innerPad * 2;
        double rangeX = Math.max(1.0, viewMaxX - viewMinX);
        double nx = (blockX - viewMinX) / rangeX;
        return mapX + innerPad + (int)Math.round(nx * contentW);
    }

    private static int toScreenY(int blockZ, int mapY, int mapH, int innerPad, double viewMinZ, double viewMaxZ) {
        int contentH = mapH - innerPad * 2;
        double rangeZ = Math.max(1.0, viewMaxZ - viewMinZ);
        double nz = (blockZ - viewMinZ) / rangeZ;
        return mapY + innerPad + (int)Math.round(nz * contentH);
    }

    private static int snapStep(int approx) {
        int[] steps = new int[] {1,2,4,8,16,32,64,128,256,512,1024,2048,4096,8192};
        if (approx <= 1) return 1;
        for (int s : steps) {
            if (approx <= s) return s;
        }
        return steps[steps.length - 1];
    }
}
