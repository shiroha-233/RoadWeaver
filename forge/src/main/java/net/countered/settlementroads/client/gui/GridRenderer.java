package net.countered.settlementroads.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * 网格渲染器 - 负责绘制背景网格
 */
public class GridRenderer {
    
    private static final int TARGET_GRID_PX = 80;
    private final Map<Integer, String> gridLabelCache = new HashMap<>();
    
    /**
     * 绘制网格
     */
    public void drawGrid(GuiGraphics ctx, MapRenderer.LODLevel lod, 
                        int width, int height, int padding,
                        double baseScale, double zoom, double offsetX, double offsetY,
                        int minX, int minZ, RoadDebugScreen.ScreenBounds bounds) {
        if (lod == MapRenderer.LODLevel.MINIMAL) return;
        
        int w = width - padding * 2;
        int h = height - padding * 2;

        double worldX0 = minX + (-offsetX) / (baseScale * zoom);
        double worldZ0 = minZ + (-offsetY) / (baseScale * zoom);
        double worldX1 = minX + (w - offsetX) / (baseScale * zoom);
        double worldZ1 = minZ + (h - offsetY) / (baseScale * zoom);

        int spacing = computeGridSpacing(baseScale, zoom);

        int startWX = (int) Math.floor(worldX0 / spacing) * spacing;
        int startWZ = (int) Math.floor(worldZ0 / spacing) * spacing;

        int maxGridLines = switch (lod) {
            case HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 25;
            case MINIMAL -> 0;
        };
        
        boolean showLabels = lod == MapRenderer.LODLevel.HIGH || lod == MapRenderer.LODLevel.MEDIUM;
        int gridLineCount = 0;

        // 绘制垂直线
        for (int x = startWX; x <= worldX1 && gridLineCount < maxGridLines; x += spacing) {
            int sx = padding + (int) ((x - worldX0) * baseScale * zoom);
            if (sx >= bounds.left() && sx <= bounds.right()) {
                RenderUtils.fillV(ctx, sx, bounds.top(), bounds.bottom(), 0x40444444);
                if (showLabels) {
                    String label = gridLabelCache.computeIfAbsent(x, String::valueOf);
                    RenderUtils.drawSmallLabel(ctx, label, sx + 2, bounds.top() + 2);
                }
                gridLineCount++;
            }
        }

        // 绘制水平线
        gridLineCount = 0;
        for (int z = startWZ; z <= worldZ1 && gridLineCount < maxGridLines; z += spacing) {
            int sz = padding + (int) ((z - worldZ0) * baseScale * zoom);
            if (sz >= bounds.top() && sz <= bounds.bottom()) {
                RenderUtils.fillH(ctx, bounds.left(), bounds.right(), sz, 0x40444444);
                if (showLabels) {
                    String label = gridLabelCache.computeIfAbsent(z, String::valueOf);
                    RenderUtils.drawSmallLabel(ctx, label, bounds.left() + 2, sz + 2);
                }
                gridLineCount++;
            }
        }
    }
    
    private int computeGridSpacing(double baseScale, double zoom) {
        double worldPerPixel = 1.0 / (baseScale * zoom);
        double worldPerGrid = worldPerPixel * TARGET_GRID_PX;
        
        int[] spacings = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
        for (int spacing : spacings) {
            if (spacing >= worldPerGrid * 0.8) {
                return spacing;
            }
        }
        return 10000;
    }
}
