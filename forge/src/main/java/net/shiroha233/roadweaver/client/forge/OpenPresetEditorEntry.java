package net.shiroha233.roadweaver.client.forge;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.config.MaterialPresetEditorScreen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OpenPresetEditorEntry extends AbstractConfigListEntry<Void> {

    private Rectangle lastArea;

    public OpenPresetEditorEntry() {
        super(Component.translatable("config.roadweaver.open_preset_editor"), false);
    }

    @Override
    public Void getValue() {
        return null;
    }

    public void setValue(Void value) {
        // no-op: this entry only acts as a button
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.render(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        this.lastArea = getEntryArea(x, y, entryWidth, entryHeight);

        Font font = Minecraft.getInstance().font;
        Component label = getDisplayedFieldName();
        if (label != null) {
            int color = getPreferredTextColor();
            int textY = y + (entryHeight - font.lineHeight) / 2;
            g.drawString(font, label, x + 4, textY, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && lastArea != null && lastArea.contains(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(new MaterialPresetEditorScreen(mc.screen));
            }
            return true;
        }
        return false;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return Collections.emptyList();
    }
}
