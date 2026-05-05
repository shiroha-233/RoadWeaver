package net.shiroha233.roadweaver.client.tips;

import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.Util;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * 世界加载界面 Tips 渲染器
 */
public final class LoadingTipsRenderer {

    private static final long INTERVAL_MILLIS = 3000L;
    private static final String TECTONIC_MOD_ID = "tectonic";
    private static final int TOP_WARNING_Y = 10;
    private static final int TOP_WARNING_PADDING_X = 6;
    private static final int TOP_WARNING_PADDING_Y = 4;
    private static final int TOP_WARNING_BG = 0x90000000;
    private static final int TOP_WARNING_COLOR = 0xFFF6D365;

    private static final List<Component> TIPS = List.of(
            Component.translatable("tip.roadweaver.loading.1", Util.getKey("key.roadweaver.open_map").getTranslatedKeyMessage().getString()),
            Component.translatable("tip.roadweaver.loading.2"),
            Component.translatable("tip.roadweaver.loading.3"),
            Component.translatable("tip.roadweaver.loading.4"),
            Component.translatable("tip.roadweaver.loading.5")
    );

    private static int currentIndex = 0;
    private static long lastSwitchTimeMillis = 0L;

    private LoadingTipsRenderer() {
    }

    /**
     * 在世界加载界面右下角渲染一条提示文本
     */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (TIPS.isEmpty()) {
            return;
        }

        ModConfig config = ConfigService.get();
        if (config != null && !config.client().loadingTipsEnabled()) {
            return;
        }

        renderTectonicWarning(graphics, mc);

        long now = System.currentTimeMillis();
        if (lastSwitchTimeMillis == 0L) {
            lastSwitchTimeMillis = now;
        }
        if (now - lastSwitchTimeMillis >= INTERVAL_MILLIS) {
            lastSwitchTimeMillis = now;
            currentIndex = (currentIndex + 1) % TIPS.size();
        }

        if (currentIndex >= TIPS.size()) {
            currentIndex = 0;
        }
        Component tip = TIPS.get(currentIndex);

        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int marginX = 6;
        int marginY = 6;
        int x = sw - font.width(tip) - marginX;
        int y = sh - font.lineHeight - marginY;

        graphics.drawString(font, tip, x, y, 0xFFFFFF, false);
    }

    public static void reset() {
        currentIndex = 0;
        lastSwitchTimeMillis = 0L;
    }

    private static void renderTectonicWarning(GuiGraphics graphics, Minecraft mc) {
        if (Platform.getOptionalMod(TECTONIC_MOD_ID).isEmpty()) {
            return;
        }
        Component warning = Component.translatable("tip.roadweaver.loading.tectonic");
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int textWidth = font.width(warning);
        int centerX = sw / 2;
        int left = centerX - textWidth / 2 - TOP_WARNING_PADDING_X;
        int right = centerX + textWidth / 2 + TOP_WARNING_PADDING_X;
        int top = TOP_WARNING_Y - TOP_WARNING_PADDING_Y;
        int bottom = TOP_WARNING_Y + font.lineHeight + TOP_WARNING_PADDING_Y;
        graphics.fill(left, top, right, bottom, TOP_WARNING_BG);
        graphics.drawCenteredString(font, warning, centerX, TOP_WARNING_Y, TOP_WARNING_COLOR);
    }
}
