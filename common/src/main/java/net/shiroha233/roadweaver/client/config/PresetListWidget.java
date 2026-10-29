package net.shiroha233.roadweaver.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;
import java.util.function.Consumer;

/**
 * 预设列表组件
 */
public class PresetListWidget extends RoadWeaverSelectionList<PresetListWidget.Entry> {
    private static final int ROW_HEIGHT = 30;
    private static final float TITLE_SCALE = 1.15f;
    private static final float SUBTITLE_SCALE = 0.85f;
    
    private final Consumer<PresetEntry> onSelect;

    public PresetListWidget(Minecraft minecraft, int width, int height, int top, Consumer<PresetEntry> onSelect) {
        super(minecraft, width, height, top, ROW_HEIGHT, 10);
        this.onSelect = onSelect;
        setBackgroundColor(0xAA14181D);
        setRenderBackground(false);
        setRenderTopAndBottom(false);
    }

    public void addPreset(String id, String name, boolean active) {
        this.addEntry(new PresetEntry(id, name, null, active));
    }

    public void addPreset(String id, String title, String subtitle, boolean active) {
        this.addEntry(new PresetEntry(id, title, subtitle, active));
    }

    public void clearPresets() {
        super.clearEntries();
    }

    public class PresetEntry extends Entry {
        private final String id;
        private final String title;
        private final String subtitle;
        private final boolean active;

        public PresetEntry(String id, String title, String subtitle, boolean active) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.active = active;
        }

        public String getId() {
            return id;
        }
        
        public String getName() {
            return title;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            if (active) {
                g.fill(left, top, left + width, top + height, 0xE6333C47);
                g.renderOutline(left, top, width, height, 0xFFCDD7E4);
            } else if (isHovered) {
                g.fill(left, top, left + width, top + height, 0xB4222830);
            }

            Minecraft mc = Minecraft.getInstance();
            int color = active ? 0xFFFFFF : 0xD7DEE8;
            int textX = left + 6;
            int maxTextWidth = Math.max(10, width - 14);

            String safeTitle = title == null ? "" : title;
            String safeSubtitle = subtitle;
            if (safeSubtitle != null && safeSubtitle.isBlank()) safeSubtitle = null;
            if (safeSubtitle != null && safeTitle.equals(safeSubtitle)) safeSubtitle = null;

            if (safeSubtitle != null) {
                g.pose().pushPose();
                g.pose().translate(textX, top + 3, 0);
                g.pose().scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
                String titleCut = mc.font.plainSubstrByWidth(safeTitle, (int) (maxTextWidth / TITLE_SCALE));
                g.drawString(mc.font, titleCut, 0, 0, color, false);
                g.pose().popPose();

                g.pose().pushPose();
                g.pose().translate(textX, top + 19, 0);
                g.pose().scale(SUBTITLE_SCALE, SUBTITLE_SCALE, 1.0F);
                String subCut = mc.font.plainSubstrByWidth(safeSubtitle, (int) (maxTextWidth / SUBTITLE_SCALE));
                g.drawString(mc.font, subCut, 0, 0, 0xAAB4C0, false);
                g.pose().popPose();
            } else {
                String titleCut = mc.font.plainSubstrByWidth(safeTitle, maxTextWidth);
                g.drawString(mc.font, titleCut, textX, top + 11, color, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                onSelect.accept(this);
                return true;
            }
            return false;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {}
}
