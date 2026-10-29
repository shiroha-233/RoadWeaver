package net.shiroha233.roadweaver.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class RoadWeaverUi {
    private static final int PANEL_BORDER = 0xCC5C6470;
    private static final int PANEL_FILL = 0xB014171C;
    private static final int PANEL_TOP = 0xAA20252C;

    private RoadWeaverUi() {
    }

    public static void drawScreenHeader(GuiGraphics graphics, Font font, int width, Component title, Component subtitle) {
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (subtitle != null) {
            graphics.drawCenteredString(font, subtitle, width / 2, 22, 0xAEB7C2);
        }
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 1 || height <= 1) {
            return;
        }
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL_FILL);
        graphics.fillGradient(x + 1, y + 1, right - 1, Math.min(bottom - 1, y + 18), PANEL_TOP, PANEL_FILL);
    }
}
