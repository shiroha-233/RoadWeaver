package net.shiroha233.roadweaver.client.tips;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import org.apache.maven.artifact.versioning.*;

import java.util.List;

/**
 * 负责在世界加载界面渲染 RoadWeaver 提示文案。
 */
public final class LoadingTipsRenderer {

    private static final long INTERVAL_MILLIS = 3000L;
    private static final String OPEN_MAP_KEY = "key.roadweaver.open_map";
    private static final int TOP_WARNING_Y = 10;
    private static final int TOP_WARNING_PADDING_X = 6;
    private static final int TOP_WARNING_PADDING_Y = 4;
    private static final int TOP_WARNING_BG = 0x90000000;
    private static final int TOP_WARNING_COLOR = 0xFFF6D365;

    private static final List<Component> TIPS = List.of(
            Component.translatable("tip.roadweaver.loading.1", Component.keybind(OPEN_MAP_KEY)),
            Component.translatable("tip.roadweaver.loading.2"),
            Component.translatable("tip.roadweaver.loading.3"),
            Component.translatable("tip.roadweaver.loading.4"),
            Component.translatable("tip.roadweaver.loading.5")
    );

    /**
     * Specify which mod ID should be searched when detecting Tectonic mod conflict. This value should always be
     * {@code tectonic} unless (very unlikely) the maintainers of Tectonic mod decides to change their mod ID.
     */
    private static final String TECTONIC_MOD_ID = "tectonic";

    /**
     * Specify which versions of Tectonic conflicts with this mod. Currently, any version above {@code 3.0.0} falls into
     * this range.
     */
    private static final VersionRange TECTONIC_CONFLICT_VERSIONS;

	static {
		try {
			TECTONIC_CONFLICT_VERSIONS = VersionRange.createFromVersionSpec("[3.0.0,)");
		} catch (InvalidVersionSpecificationException e) {
            // The version specification should be always valid! This code block should never be reached!
			throw new RuntimeException(e);
		}
	}

    /**
     * {@code true} if detects Tectonic mod with version falls into {@link #TECTONIC_CONFLICT_VERSIONS} installed,
     * {@code false} otherwise.
     */
	private static final boolean HAS_CONFLICT_TECTONIC = Platform.getOptionalMod(TECTONIC_MOD_ID)
                                                             .map(Mod::getVersion)
                                                             .map(DefaultArtifactVersion::new)
                                                             .map(TECTONIC_CONFLICT_VERSIONS::containsVersion)
                                                             .orElse(false); // If no Tectonic mod is installed, no conflict will happen

    private static int currentIndex = 0;
    private static long lastSwitchTimeMillis = 0L;

    private LoadingTipsRenderer() {
    }

    /**
     * 在世界加载界面右下角渲染一条提示文本。
     */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || TIPS.isEmpty()) {
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
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int marginX = 6;
        int marginY = 6;
        int x = screenWidth - font.width(tip) - marginX;
        int y = screenHeight - font.lineHeight - marginY;

        graphics.drawString(font, tip, x, y, 0xFFFFFF, false);
    }

    public static void reset() {
        currentIndex = 0;
        lastSwitchTimeMillis = 0L;
    }

    private static void renderTectonicWarning(GuiGraphics graphics, Minecraft mc) {
        if (!HAS_CONFLICT_TECTONIC) {
            return;
        }

        Component warning = Component.translatable("tip.roadweaver.loading.tectonic");
        var font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = font.width(warning);
        int centerX = screenWidth / 2;
        int left = centerX - textWidth / 2 - TOP_WARNING_PADDING_X;
        int right = centerX + textWidth / 2 + TOP_WARNING_PADDING_X;
        int top = TOP_WARNING_Y - TOP_WARNING_PADDING_Y;
        int bottom = TOP_WARNING_Y + font.lineHeight + TOP_WARNING_PADDING_Y;
        graphics.fill(left, top, right, bottom, TOP_WARNING_BG);
        graphics.drawCenteredString(font, warning, centerX, TOP_WARNING_Y, TOP_WARNING_COLOR);
    }
}
