package net.countered.settlementroads.client.gui;

import net.countered.settlementroads.helpers.Records;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * UI渲染器 - 负责绘制标题、统计面板、图例等UI元素
 */
public class UIRenderer {
    
    private static final int PADDING = 20;
    private final Map<String, Integer> statusColors;
    
    public UIRenderer(Map<String, Integer> statusColors) {
        this.statusColors = statusColors;
    }
    
    /**
     * 绘制标题
     */
    public void drawTitle(GuiGraphics ctx, int width) {
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("gui.roadweaver.debug_map.title");
        int tw = font.width(title);
        int x = (width - tw) / 2;
        int y = PADDING + 8;
        
        RenderUtils.drawPanel(ctx, x - 10, y - 5, x + tw + 10, y + 14, 0xC0000000, 0xFF4A90E2);
        ctx.drawString(font, title, x, y, 0xFFFFFFFF, true);
    }
    
    /**
     * 绘制统计面板
     */
    public void drawStatsPanel(GuiGraphics ctx, int width, 
                              List<BlockPos> structures,
                              List<Records.StructureConnection> connections,
                              List<Records.RoadData> roads,
                              double zoom) {
        Font font = Minecraft.getInstance().font;
        
        int planned = 0, generating = 0, completed = 0, failed = 0;
        for (Records.StructureConnection conn : connections) {
            switch (conn.status()) {
                case PLANNED -> planned++;
                case GENERATING -> generating++;
                case COMPLETED -> completed++;
                case FAILED -> failed++;
            }
        }
        
        String[] labels = {
            "结构: " + structures.size(),
            "计划中: " + planned,
            "生成中: " + generating,
            "已完成: " + completed,
            "失败: " + failed,
            "道路: " + roads.size(),
            "缩放: " + String.format("%.1fx", zoom)
        };
        
        int[] colors = {
            statusColors.get("structure"),
            statusColors.get("planned"),
            statusColors.get("generating"),
            statusColors.get("completed"),
            statusColors.get("failed"),
            statusColors.get("road"),
            0xFFFFFFFF
        };
        
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, font.width(label));
        }
        
        int panelX = width - maxWidth - PADDING - 20;
        int panelY = PADDING + 40;
        int panelW = maxWidth + 16;
        int panelH = labels.length * 12 + 8;
        
        RenderUtils.drawPanel(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0000000, 0xFF2C3E50);
        
        for (int i = 0; i < labels.length; i++) {
            int textY = panelY + 8 + i * 12;
            ctx.drawString(font, labels[i], panelX + 8, textY, colors[i], false);
        }
    }
    
    /**
     * 绘制图例面板
     */
    public void drawLegendPanel(GuiGraphics ctx, int height) {
        Font font = Minecraft.getInstance().font;
        
        String[] labels = {"结构", "计划中", "生成中", "失败", "道路", "玩家"};
        int[] colors = {
            statusColors.get("structure"),
            statusColors.get("planned"),
            statusColors.get("generating"),
            statusColors.get("failed"),
            statusColors.get("road"),
            0xFFE74C3C
        };
        
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, font.width(label));
        }
        
        int panelX = PADDING;
        int panelY = height - labels.length * 12 - PADDING - 16;
        int panelW = maxWidth + 32;
        int panelH = labels.length * 12 + 8;
        
        RenderUtils.drawPanel(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 0xE0000000, 0xFF34495E);
        
        for (int i = 0; i < labels.length; i++) {
            int textY = panelY + 8 + i * 12;
            
            RenderUtils.fillCircle(ctx, panelX + 12, textY + 4, 3, colors[i]);
            ctx.drawString(font, labels[i], panelX + 24, textY, 0xFFFFFFFF, false);
        }
    }
    
    /**
     * 绘制工具提示
     */
    public void drawTooltip(GuiGraphics ctx, BlockPos structure, int mouseX, int mouseY, int width) {
        Font font = Minecraft.getInstance().font;
        String text = "结构: " + structure.getX() + ", " + structure.getZ();
        
        int tooltipWidth = font.width(text) + 8;
        int tooltipHeight = 18;
        
        int x = mouseX + 10;
        int y = mouseY - 25;
        
        if (x + tooltipWidth > width) x = mouseX - tooltipWidth - 10;
        if (y < 0) y = mouseY + 10;
        
        RenderUtils.drawPanel(ctx, x, y, x + tooltipWidth, y + tooltipHeight, 0xF0000000, 0xFF555555);
        ctx.drawString(font, text, x + 4, y + 5, 0xFFFFFFFF, false);
    }
}
