package net.shiroha233.roadweaver.client.map.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 结构点图标的分类与扁平化绘制。
 */
public final class StructureIconRenderer {
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");
    private static final int BORDER_COLOR = 0xE0101214;
    private static final int UNKNOWN_BACKGROUND = 0xE0282C31;
    private static final int UNKNOWN_TEXT = 0xFFF4F1E8;

    private StructureIconRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              String structureId,
                              int centerX,
                              int centerY,
                              int size,
                              int left,
                              int top,
                              int right,
                              int bottom) {
        int iconSize = Math.max(10, size);
        int drawX = centerX - iconSize / 2;
        int drawY = centerY - iconSize / 2;
        if (drawX > right || drawX + iconSize < left || drawY > bottom || drawY + iconSize < top) return;

        graphics.fill(drawX - 1, drawY - 1, drawX + iconSize + 1, drawY + iconSize + 1, BORDER_COLOR);
        if (isVillageStructure(structureId)) {
            drawVillagerFace(graphics, drawX, drawY, iconSize);
            return;
        }
        drawUnknown(graphics, font, centerX, drawX, drawY, iconSize);
    }

    public static void renderOutline(GuiGraphics graphics,
                                     int centerX,
                                     int centerY,
                                     int size,
                                     int color,
                                     int left,
                                     int top,
                                     int right,
                                     int bottom) {
        int half = Math.max(6, size / 2);
        int x0 = Math.max(left, centerX - half);
        int y0 = Math.max(top, centerY - half);
        int x1 = Math.min(right, centerX + half);
        int y1 = Math.min(bottom, centerY + half);
        if (x0 > x1 || y0 > y1) return;
        graphics.fill(x0, y0, x1 + 1, y0 + 1, color);
        graphics.fill(x0, y1, x1 + 1, y1 + 1, color);
        graphics.fill(x0, y0, x0 + 1, y1 + 1, color);
        graphics.fill(x1, y0, x1 + 1, y1 + 1, color);
    }

    private static boolean isVillageStructure(String structureId) {
        return structureId != null
                && !structureId.isBlank()
                && structureId.toLowerCase(Locale.ROOT).contains("village");
    }

    private static void drawVillagerFace(GuiGraphics graphics, int x, int y, int size) {
        RenderSystem.enableBlend();
        graphics.blit(VILLAGER_TEXTURE, x, y, size, size,
                8.0F, 8.0F,
                8, 10,
                64, 64);
        RenderSystem.disableBlend();
    }

    private static void drawUnknown(GuiGraphics graphics, Font font, int centerX, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, UNKNOWN_BACKGROUND);
        String text = "?";
        int textX = centerX - font.width(text) / 2;
        int textY = y + Math.max(0, (size - font.lineHeight) / 2);
        graphics.drawString(font, text, textX, textY, UNKNOWN_TEXT, false);
    }
}
