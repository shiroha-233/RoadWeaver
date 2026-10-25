package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import net.shiroha233.roadweaver.core.model.StructureConnection;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * 地图渲染器集合
 */
public final class MapRenderers {
    private MapRenderers() {}

    public interface SegmentInView { boolean test(int x1, int z1, int x2, int z2); }

    public static void renderGrid(GuiGraphics g,
                                  Font font,
                                  int mapX, int mapY, int mapW, int mapH,
                                  int innerPad,
                                  double viewMinX, double viewMaxX, double viewMinZ, double viewMaxZ,
                                  int colorGrid, int gridTargetPx, int colorText) {
        GridRenderer.render(g, font, mapX, mapY, mapW, mapH, innerPad,
                viewMinX, viewMaxX, viewMinZ, viewMaxZ, colorGrid, gridTargetPx, colorText);
    }

    public static void renderStructures(GuiGraphics g,
                                        Font font,
                                        List<BlockPos> points,
                                        Function<BlockPos, String> structureIdResolver,
                                        IntUnaryOperator toScreenX,
                                        IntUnaryOperator toScreenY,
                                        java.util.function.BiPredicate<Integer, Integer> isInViewWorld,
                                        int size,
                                        int left, int top, int right, int bottom) {
        for (BlockPos p : points) {
            int bx = p.getX();
            int bz = p.getZ();
            if (!isInViewWorld.test(bx, bz)) continue;
            int x = toScreenX.applyAsInt(bx);
            int y = toScreenY.applyAsInt(bz);
            String structureId = structureIdResolver != null ? structureIdResolver.apply(p) : null;
            StructureIconRenderer.render(g, font, structureId, x, y, Math.max(10, size + 2), left, top, right, bottom);
        }
    }

    public static void renderConnections(GuiGraphics g,
                                         List<StructureConnection> connections,
                                         SegmentInView segmentInView,
                                         IntUnaryOperator toScreenX,
                                         IntUnaryOperator toScreenY,
                                         int thickness,
                                         int colorPlanned, int colorGenerating, int colorCompleted, int colorFailed,
                                         int left, int top, int right, int bottom) {
        for (StructureConnection c : connections) {
            int fx = c.from().getX();
            int fz = c.from().getZ();
            int tx = c.to().getX();
            int tz = c.to().getZ();
            if (!segmentInView.test(fx, fz, tx, tz)) continue;
            int x1 = toScreenX.applyAsInt(fx);
            int y1 = toScreenY.applyAsInt(fz);
            int x2 = toScreenX.applyAsInt(tx);
            int y2 = toScreenY.applyAsInt(tz);
            switch (c.status()) {
                case PLANNED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorPlanned, thickness, left, top, right, bottom);
                }
                case GENERATING -> {
                    RenderUtils.drawThickDashedLine(g, x1, y1, x2, y2, colorGenerating, thickness, 8, 6, left, top, right, bottom);
                }
                case COMPLETED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorCompleted, thickness, left, top, right, bottom);
                }
                case FAILED -> {
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, colorFailed, thickness, left, top, right, bottom);
                }
            }
        }
    }

    public static void renderRoadPolylines(GuiGraphics g,
                                           List<List<BlockPos>> polylines,
                                           SegmentInView segmentInView,
                                           IntUnaryOperator toScreenX,
                                           IntUnaryOperator toScreenY,
                                           int thickness,
                                           int color,
                                           int left, int top, int right, int bottom,
                                           int lodStepBlocks) {
        for (List<BlockPos> pl : polylines) {
            if (pl == null || pl.size() < 2) continue;
            BlockPos keep = pl.get(0);
            for (int i = 1; i < pl.size(); i++) {
                BlockPos curr = pl.get(i);
                int dx = curr.getX() - keep.getX();
                int dz = curr.getZ() - keep.getZ();
                int adx = Math.abs(dx);
                int adz = Math.abs(dz);
                int manhattan = adx + adz;
                if (manhattan < lodStepBlocks) continue;
                int x1w = keep.getX();
                int z1w = keep.getZ();
                int x2w = curr.getX();
                int z2w = curr.getZ();
                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    int x1 = toScreenX.applyAsInt(x1w);
                    int y1 = toScreenY.applyAsInt(z1w);
                    int x2 = toScreenX.applyAsInt(x2w);
                    int y2 = toScreenY.applyAsInt(z2w);
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom);
                }
                keep = curr;
            }
            BlockPos tail = pl.get(pl.size() - 1);
            if (tail != keep) {
                int x1w = keep.getX();
                int z1w = keep.getZ();
                int x2w = tail.getX();
                int z2w = tail.getZ();
                if (segmentInView.test(x1w, z1w, x2w, z2w)) {
                    int x1 = toScreenX.applyAsInt(x1w);
                    int y1 = toScreenY.applyAsInt(z1w);
                    int x2 = toScreenX.applyAsInt(x2w);
                    int y2 = toScreenY.applyAsInt(z2w);
                    RenderUtils.drawThickLine(g, x1, y1, x2, y2, color, thickness, left, top, right, bottom);
                }
            }
        }
    }

    public static void drawPlayerAvatar(GuiGraphics g,
                                        ResourceLocation skinTexture,
                                        int sx,
                                        int sy,
                                        float yawDeg,
                                        int size,
                                        int frameColor,
                                        int directionColor,
                                        int directionOutlineColor,
                                        int directionTipLen,
                                        int directionBaseOffset,
                                        int directionHalfWidth,
                                        int left,
                                        int top,
                                        int right,
                                        int bottom) {
        if (skinTexture == null) return;
        double rx = Math.toRadians(yawDeg);
        double dirX = -Math.sin(rx);
        double dirY = Math.cos(rx);
        double perpX = -dirY;
        double perpY = dirX;
        double tipX = sx + dirX * directionTipLen;
        double tipY = sy + dirY * directionTipLen;
        double baseX = sx + dirX * directionBaseOffset;
        double baseY = sy + dirY * directionBaseOffset;
        int ipx = (int) Math.round(tipX);
        int ipy = (int) Math.round(tipY);
        int ibx1 = (int) Math.round(baseX + perpX * directionHalfWidth);
        int iby1 = (int) Math.round(baseY + perpY * directionHalfWidth);
        int ibx2 = (int) Math.round(baseX - perpX * directionHalfWidth);
        int iby2 = (int) Math.round(baseY - perpY * directionHalfWidth);
        RenderUtils.fillTriangle(g, ipx - 1, ipy, ibx1 - 1, iby1, ibx2 - 1, iby2, directionOutlineColor, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx + 1, ipy, ibx1 + 1, iby1, ibx2 + 1, iby2, directionOutlineColor, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx, ipy - 1, ibx1, iby1 - 1, ibx2, iby2 - 1, directionOutlineColor, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx, ipy + 1, ibx1, iby1 + 1, ibx2, iby2 + 1, directionOutlineColor, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx, ipy, ibx1, iby1, ibx2, iby2, directionColor, left, top, right, bottom);

        int half = size / 2;
        int drawX = sx - half;
        int drawY = sy - half;
        if (drawX > right || drawX + size < left || drawY > bottom || drawY + size < top) {
            return;
        }
        g.fill(drawX - 2, drawY - 2, drawX + size + 2, drawY + size + 2, frameColor);
        g.fill(drawX - 1, drawY - 1, drawX + size + 1, drawY + size + 1, 0x66FFFFFF);
        PlayerFaceRenderer.draw(g, skinTexture, drawX, drawY, size);
    }

    public static void drawPlayerArrow(GuiGraphics g,
                                       int sx, int sy,
                                       float yawDeg,
                                       int tipLen,
                                       int baseLen,
                                       int baseHalfWidth,
                                       int color,
                                       int left, int top, int right, int bottom,
                                       double pxPerBlockX,
                                       double pxPerBlockZ) {
        double rx = Math.toRadians(yawDeg);
        double vx = -Math.sin(rx);
        double vz = Math.cos(rx);
        double dirX = vx * pxPerBlockX;
        double dirY = vz * pxPerBlockZ;
        double mag = Math.hypot(dirX, dirY);
        if (mag < 1e-6) { dirX = 1; dirY = 0; mag = 1; }
        dirX /= mag; dirY /= mag;
        double px = sx + dirX * tipLen;
        double py = sy + dirY * tipLen;
        double bx = sx - dirX * baseLen;
        double by = sy - dirY * baseLen;
        double perpX = -dirY;
        double perpY = dirX;
        double bx1 = bx + perpX * baseHalfWidth;
        double by1 = by + perpY * baseHalfWidth;
        double bx2 = bx - perpX * baseHalfWidth;
        double by2 = by - perpY * baseHalfWidth;
        int ipx = (int)Math.round(px);
        int ipy = (int)Math.round(py);
        int ibx1 = (int)Math.round(bx1);
        int iby1 = (int)Math.round(by1);
        int ibx2 = (int)Math.round(bx2);
        int iby2 = (int)Math.round(by2);
        int outline = 0xFFFFFFFF;
        RenderUtils.fillTriangle(g, ipx - 1, ipy,     ibx1 - 1, iby1,     ibx2 - 1, iby2,     outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx + 1, ipy,     ibx1 + 1, iby1,     ibx2 + 1, iby2,     outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy - 1, ibx1,     iby1 - 1, ibx2,     iby2 - 1, outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy + 1, ibx1,     iby1 + 1, ibx2,     iby2 + 1, outline, left, top, right, bottom);
        RenderUtils.fillTriangle(g, ipx,     ipy,     ibx1,     iby1,     ibx2,     iby2,     color,   left, top, right, bottom);
    }
}
