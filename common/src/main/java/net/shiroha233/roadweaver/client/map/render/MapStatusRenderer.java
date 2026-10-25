package net.shiroha233.roadweaver.client.map.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.MapLoadSession;
import net.shiroha233.roadweaver.client.map.MapTheme;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;

import java.util.Locale;

public final class MapStatusRenderer {
    private static final int SCREEN_MARGIN = MapTheme.HUD_TOP_MARGIN;
    private static final int CARD_GAP = 6;
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 7;
    private static final int LINE_STEP = 13;
    private static final int LINE_GAP = 1;
    private static final int ACCENT_WIDTH = 2;
    private static final int ACCENT_GAP = 7;
    private static final int MAX_CARD_WIDTH = 290;

    private MapStatusRenderer() {}

    public static void render(GuiGraphics graphics,
                              Font font,
                              int screenWidth,
                              int unobstructedRight,
                              MapLoadSession loadSession,
                              TerrainSamplingSessionSnapshot samplingSession) {
        int maxTextWidth = maxTextWidth(screenWidth, unobstructedRight);
        if (maxTextWidth <= 0) return;

        int y = SCREEN_MARGIN;
        if (loadSession != null) {
            StatusLine[] lines = loadingLines(font, loadSession, maxTextWidth);
            y += renderCard(graphics, font, SCREEN_MARGIN, y, lines, MapTheme.COLOR_GENERATING);
            y += CARD_GAP;
        }
        if (samplingSession != null) {
            renderCard(graphics, font, SCREEN_MARGIN, y,
                    samplingLines(font, samplingSession, maxTextWidth), MapTheme.DOCK_INDICATOR);
        }
    }

    static int maxCardWidth(int screenWidth, int unobstructedRight) {
        int sharedTopWidth = Math.max(1, (screenWidth - SCREEN_MARGIN * 3) / 2);
        int unobstructedWidth = Math.max(0, unobstructedRight - SCREEN_MARGIN * 2);
        return Math.min(MAX_CARD_WIDTH, Math.min(sharedTopWidth, unobstructedWidth));
    }

    static int maxTextWidth(int screenWidth, int unobstructedRight) {
        return Math.max(0,
                maxCardWidth(screenWidth, unobstructedRight)
                        - PADDING_X * 2 - ACCENT_WIDTH - ACCENT_GAP);
    }

    private static StatusLine[] loadingLines(Font font,
                                              MapLoadSession session,
                                              int maxTextWidth) {
        Component phase = phaseLabel(session.lastPhase());
        return new StatusLine[] {
                line(font, Component.translatable("gui.roadweaver.map.loading.progress",
                        session.completedResponses(), session.totalResponses()),
                        MapTheme.PANEL_TEXT, maxTextWidth),
                line(font, Component.translatable("gui.roadweaver.map.loading.phase",
                        phase, session.elapsedMs()), MapTheme.PANEL_MUTED, maxTextWidth)
        };
    }

    private static StatusLine[] samplingLines(Font font,
                                              TerrainSamplingSessionSnapshot session,
                                              int maxTextWidth) {
        String backend = session.backend().isBlank() ? "-" : session.backend();
        String device = session.device().isBlank() ? "" : " / " + session.device();
        Component modes = Component.translatable(
                "gui.roadweaver.map.sampling_modes",
                samplingModeLabel(session.configuredMode()),
                samplingModeLabel(session.effectiveMode()));
        Component runtime = Component.translatable(
                "gui.roadweaver.map.sampling_backend", backend + device);
        if (!session.hasFallbackReason()) {
            return new StatusLine[] {
                    line(font, modes, MapTheme.PANEL_TEXT, maxTextWidth),
                    line(font, runtime, MapTheme.PANEL_MUTED, maxTextWidth)
            };
        }
        return new StatusLine[] {
                line(font, modes, MapTheme.PANEL_TEXT, maxTextWidth),
                line(font, runtime, MapTheme.PANEL_MUTED, maxTextWidth),
                line(font, Component.translatable(
                        "gui.roadweaver.map.sampling_fallback", session.fallbackReason()),
                        MapTheme.COLOR_FAILED, maxTextWidth)
        };
    }

    private static StatusLine line(Font font, Component text, int color, int maxWidth) {
        return new StatusLine(fitText(font, text, maxWidth), color);
    }

    private static Component fitText(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (suffixWidth >= maxWidth) {
            return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth)));
        }
        String prefix = font.plainSubstrByWidth(text.getString(), maxWidth - suffixWidth);
        return Component.literal(prefix + suffix);
    }

    private static Component phaseLabel(MapLoadPhase phase) {
        String key = phase == null ? "starting" : phase.name().toLowerCase(Locale.ROOT);
        return Component.translatable("gui.roadweaver.map.loading.phase." + key);
    }

    private static int renderCard(GuiGraphics graphics,
                                  Font font,
                                  int x,
                                  int y,
                                  StatusLine[] lines,
                                  int accentColor) {
        int textWidth = 0;
        for (StatusLine line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }
        int width = PADDING_X * 2 + ACCENT_WIDTH + ACCENT_GAP + textWidth;
        int height = PADDING_Y * 2 + lines.length * LINE_STEP - LINE_GAP;
        renderSurface(graphics, x, y, width, height);
        graphics.fill(x + PADDING_X, y + PADDING_Y,
                x + PADDING_X + ACCENT_WIDTH, y + height - PADDING_Y, accentColor);
        int textX = x + PADDING_X + ACCENT_WIDTH + ACCENT_GAP;
        int textY = y + PADDING_Y;
        for (StatusLine line : lines) {
            graphics.drawString(font, line.text(), textX, textY, line.color(), false);
            textY += LINE_STEP;
        }
        return height;
    }

    private static Component samplingModeLabel(TerrainSamplingMode mode) {
        return Component.translatable(
                "config.roadweaver.terrain_sampling_mode.option."
                        + mode.name().toLowerCase(Locale.ROOT));
    }

    private static void renderSurface(GuiGraphics graphics, int x, int y, int width, int height) {
        MapDockRenderer.fillRounded(graphics, x + 2, y + 3, width, height, 10, MapTheme.PANEL_SHADOW);
        MapDockRenderer.fillRounded(graphics, x, y, width, height, 10, MapTheme.PANEL_BG);
        MapDockRenderer.fillRounded(graphics, x + 1, y + 1, width - 2, height - 2, 9, MapTheme.PANEL_HIGHLIGHT);
        MapDockRenderer.fillRounded(graphics, x + 2, y + 2, width - 4, height - 4, 8, MapTheme.PANEL_BG);
    }

    private record StatusLine(Component text, int color) {}
}
