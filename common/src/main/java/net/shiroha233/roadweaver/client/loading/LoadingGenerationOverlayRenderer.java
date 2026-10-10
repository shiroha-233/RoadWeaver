/* 文件职责：渲染初始道路生成的单条总进度条与当前步骤文字。 */
package net.shiroha233.roadweaver.client.loading;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.client.tips.LoadingTipsRenderer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressSnapshot;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessionSnapshot;
import net.shiroha233.roadweaver.planning.terrain.TerrainSamplingSessions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 世界创建阶段的加载进度显示。
 * <p>
 * 默认只呈现一条总进度条与一行当前步骤文字；后端/瓦片/吞吐等明细仅在调试开关打开时叠加显示。
 */
public final class LoadingGenerationOverlayRenderer {
    private static final int MIN_CARD_WIDTH = 180;
    private static final int MAX_CARD_WIDTH = 460;
    private static final int CARD_PADDING_X = 10;
    private static final int CARD_PADDING_Y = 6;
    private static final int CARD_BG = 0xB010131A;
    private static final int CARD_BORDER = 0x80404B5A;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_ACCENT = 0xFF8CE0FF;
    private static final int TEXT_MUTED = 0xFF8D97A6;
    private static final int TEXT_WARNING = 0xFFFFB86B;
    private static final int BAR_BG = 0x60242D38;
    private static final int BAR_FILL = 0xFF4CC2FF;
    private static final int BAR_HEIGHT = 5;
    private static final int LINE_GAP = 4;
    private static final int DEBUG_LINE_STEP = 11;
    private static final int BOTTOM_MARGIN = 24;

    private LoadingGenerationOverlayRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        LoadingTipsRenderer.render(graphics);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        ModConfig config = ConfigService.get();
        if (config != null && !config.loadingProgressEnabled()) {
            return;
        }
        if (!InitialGenManager.isActive()) {
            return;
        }

        InitialGenerationProgressSnapshot snapshot = InitialGenManager.getProgressSnapshot();
        if (!snapshot.active()) {
            return;
        }

        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Component title = Component.translatable("gui.roadweaver.initgen.title");
        Component percent = Component.literal(snapshot.overallPercent() + "%");
        Component stage = Component.translatable(snapshot.stageLabel());
        List<DebugLine> debugLines = debugRequested(config) ? buildDebugLines(mc, config, snapshot) : List.of();

        int lineHeight = font.lineHeight;
        int percentWidth = font.width(percent.getString());
        int contentWidth = Math.max(
                font.width(title.getString()) + percentWidth + 10,
                Math.max(font.width(stage.getString()), debugWidth(font, debugLines)));

        int maxCardWidth = Math.max(120, Math.min(MAX_CARD_WIDTH, sw - 40));
        int cardWidth = clamp(contentWidth + CARD_PADDING_X * 2,
                Math.min(MIN_CARD_WIDTH, maxCardWidth),
                maxCardWidth);
        // 高度与下方绘制顺序保持一致：标题 + 进度条 + 步骤文字 (+ 调试明细)
        int cardHeight = CARD_PADDING_Y * 2
                + lineHeight
                + LINE_GAP + BAR_HEIGHT + LINE_GAP
                + lineHeight
                + (debugLines.isEmpty() ? 0 : debugLines.size() * DEBUG_LINE_STEP + lineHeight);

        int left = (sw - cardWidth) / 2;
        int top = Math.max(8, sh - cardHeight - BOTTOM_MARGIN);

        graphics.fill(left, top, left + cardWidth, top + cardHeight, CARD_BG);
        graphics.fill(left, top, left + cardWidth, top + 1, CARD_BORDER);
        graphics.fill(left, top + cardHeight - 1, left + cardWidth, top + cardHeight, CARD_BORDER);
        graphics.fill(left, top, left + 1, top + cardHeight, CARD_BORDER);
        graphics.fill(left + cardWidth - 1, top, left + cardWidth, top + cardHeight, CARD_BORDER);

        int x = left + CARD_PADDING_X;
        int right = left + cardWidth - CARD_PADDING_X;
        int innerWidth = cardWidth - CARD_PADDING_X * 2;
        int y = top + CARD_PADDING_Y;

        drawFittedString(graphics, font, title, x, y, Math.max(0, innerWidth - percentWidth - 6), TEXT_PRIMARY);
        graphics.drawString(font, percent, right - percentWidth, y, TEXT_PRIMARY, false);

        y += lineHeight + LINE_GAP;
        drawBar(graphics, x, y, innerWidth, BAR_HEIGHT, snapshot.overallPercent(), BAR_FILL);

        y += BAR_HEIGHT + LINE_GAP;
        drawFittedString(graphics, font, stage, x, y, innerWidth, TEXT_ACCENT);

        y += DEBUG_LINE_STEP;
        for (DebugLine line : debugLines) {
            drawFittedString(graphics, font, line.text(), x, y, innerWidth, line.color());
            y += DEBUG_LINE_STEP;
        }
    }

    /**
     * 调试开关：配置项常开，或加载界面按住 Shift 时临时展开。
     * <p>
     * {@link Screen#hasShiftDown()} 读的是全局按键状态，与当前是否激活某个 Screen 无关，
     * 故不额外判断 {@code mc.screen}。
     */
    private static boolean debugRequested(ModConfig config) {
        if (config != null && config.client().loadingProgressDebugEnabled()) {
            return true;
        }
        return Screen.hasShiftDown();
    }

    private static List<DebugLine> buildDebugLines(Minecraft mc,
                                                   ModConfig config,
                                                   InitialGenerationProgressSnapshot snapshot) {
        TerrainSamplingSessionSnapshot session = samplingSession(mc, config);
        String fallbackReason = session.hasFallbackReason() ? session.fallbackReason() : snapshot.fallbackReason();

        List<DebugLine> lines = new ArrayList<>(9);
        lines.add(muted(Component.translatable("gui.roadweaver.initgen.stage_progress", snapshot.stagePercent())));
        lines.add(muted(Component.translatable("gui.roadweaver.initgen.summary",
                snapshot.connectionsTotal(),
                snapshot.connectionsGenerating(),
                snapshot.connectionsDone(),
                snapshot.connectionsFailed())));
        lines.add(muted(Component.translatable("gui.roadweaver.initgen.sampling_modes",
                samplingModeLabel(session.configuredMode()),
                samplingModeLabel(session.effectiveMode()))));
        lines.add(muted(Component.translatable("gui.roadweaver.initgen.device",
                printable(snapshot.backend()),
                printableDevice(snapshot),
                printable(snapshot.devicePreference()))));

        lines.add(muted(switch (snapshot.stage()) {
            case EXACT_SAMPLING -> Component.translatable("gui.roadweaver.initgen.exact_batch",
                    snapshot.lastBatchSamples(),
                    snapshot.lastBatchMillis(),
                    exactBatchRate(snapshot));
            case EXACT_PATHING -> Component.translatable("gui.roadweaver.initgen.exact_paths",
                    snapshot.exactPathsDone(),
                    snapshot.exactPathsTotal());
            default -> Component.translatable("gui.roadweaver.initgen.tiles",
                    snapshot.tilesLoaded(),
                    snapshot.tilesTotal(),
                    snapshot.tilesFromMemory(),
                    snapshot.tilesFromDisk(),
                    snapshot.tilesSampled());
        }));

        lines.add(muted(switch (snapshot.stage()) {
            case EXACT_SAMPLING -> Component.translatable("gui.roadweaver.initgen.exact_throughput",
                    snapshot.samplesDone(),
                    snapshot.samplesTotal(),
                    format(snapshot.samplesPerSecond()),
                    exactSamplingEta(snapshot));
            case EXACT_PATHING -> Component.translatable("gui.roadweaver.initgen.exact_paths_remaining",
                    Math.max(0, snapshot.exactPathsTotal() - snapshot.exactPathsDone()),
                    snapshot.stagePercent());
            default -> Component.translatable("gui.roadweaver.initgen.throughput",
                    snapshot.lastBatchSamples(),
                    format(snapshot.samplesPerSecond()),
                    snapshot.initialThreads(),
                    snapshot.activeWorkers());
        }));

        if (snapshot.stage() == InitialGenerationStage.ROAD_GENERATION
                || snapshot.stage() == InitialGenerationStage.POST_PROCESSING) {
            lines.add(muted(Component.translatable("gui.roadweaver.initgen.debug",
                    snapshot.terrainCacheHitPercent(),
                    format(snapshot.terrainNoiseSamplesPerSec()),
                    snapshot.terrainNoiseSamplesTotal())));
            lines.add(muted(Component.translatable("gui.roadweaver.initgen.debug2",
                    snapshot.accurateCacheHitPercent(),
                    format(snapshot.accurateBaseHeightPerSec()),
                    snapshot.accurateBaseHeightTotal())));
        }

        if (fallbackReason != null && !fallbackReason.isBlank()) {
            lines.add(new DebugLine(
                    Component.translatable("gui.roadweaver.initgen.fallback", fallbackReason),
                    TEXT_WARNING));
        }
        return lines;
    }

    private static TerrainSamplingSessionSnapshot samplingSession(Minecraft minecraft, ModConfig config) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level != null) {
                return TerrainSamplingSessions.forLevel(level).snapshot();
            }
        }
        var configured = config == null ? null : config.planning().terrainSamplingMode();
        return new TerrainSamplingSessionSnapshot(configured, configured, "", "", "");
    }

    private static Component samplingModeLabel(TerrainSamplingMode mode) {
        if (mode == null) {
            return Component.literal("-");
        }
        return Component.translatable(
                "config.roadweaver.terrain_sampling_mode.option."
                        + mode.name().toLowerCase(Locale.ROOT));
    }

    private static DebugLine muted(Component text) {
        return new DebugLine(text, TEXT_MUTED);
    }

    private static int debugWidth(Font font, List<DebugLine> lines) {
        int width = 0;
        for (DebugLine line : lines) {
            width = Math.max(width, font.width(line.text().getString()));
        }
        return width;
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, int height, int percent, int fillColor) {
        int clamped = clamp(percent, 0, 100);
        int filled = Math.max(0, Math.min(width, Math.round(width * (clamped / 100.0f))));
        graphics.fill(x, y, x + width, y + height, BAR_BG);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, fillColor);
        }
    }

    private static void drawFittedString(GuiGraphics graphics,
                                         Font font,
                                         Component text,
                                         int x,
                                         int y,
                                         int maxWidth,
                                         int color) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) {
            graphics.drawString(font, raw, x, y, color, false);
            return;
        }
        int suffixWidth = font.width("...");
        String clipped = font.plainSubstrByWidth(raw, Math.max(0, maxWidth - suffixWidth));
        graphics.drawString(font, clipped + "...", x, y, color, false);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String printable(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String printableDevice(InitialGenerationProgressSnapshot snapshot) {
        String deviceName = printable(snapshot.deviceName());
        if (!"-".equals(deviceName)) {
            return deviceName;
        }
        return printable(snapshot.backend());
    }

    private static String exactSamplingEta(InitialGenerationProgressSnapshot snapshot) {
        double rate = snapshot.samplesPerSecond();
        if (rate <= 0.0) {
            return "--";
        }
        long remaining = Math.max(0L, snapshot.samplesTotal() - snapshot.samplesDone());
        long seconds = (long) Math.ceil(remaining / rate);
        if (seconds >= 60L) {
            return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
        }
        return seconds + "s";
    }

    private static String exactBatchRate(InitialGenerationProgressSnapshot snapshot) {
        if (snapshot.lastBatchMillis() <= 0L) {
            return "--";
        }
        return format(snapshot.lastBatchSamples() * 1000.0 / snapshot.lastBatchMillis());
    }

    /** 一行调试明细及其显示颜色。 */
    private record DebugLine(Component text, int color) {
    }
}