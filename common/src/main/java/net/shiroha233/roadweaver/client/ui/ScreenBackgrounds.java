package net.shiroha233.roadweaver.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 屏幕背景渲染工具类
 */
public final class ScreenBackgrounds {
    private static final int TOP_COLOR = 0xC0101010;
    private static final int BOTTOM_COLOR = 0xD0101010;

    private ScreenBackgrounds() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, TOP_COLOR, BOTTOM_COLOR);
    }
}
