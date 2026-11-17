package net.shiroha233.roadweaver.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.config.StructureSelectionScreen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 结构选择界面入口
 */
public class OpenStructureSelectionEntry extends AbstractConfigListEntry<Void> {
    private Rectangle lastArea;

    public OpenStructureSelectionEntry() {
        super(Component.translatable("config.roadweaver.open_structure_selection"), false);
    }

    @Override
    public Void getValue() {
        return null;
    }

    public void setValue(Void value) {}

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        super.extractRenderState(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        this.lastArea = getEntryArea(x, y, entryWidth, entryHeight);

        Font font = Minecraft.getInstance().font;
        Component label = getDisplayedFieldName();
        int color = getPreferredTextColor();
        int textY = y + (entryHeight - font.lineHeight) / 2;
        g.text(font, label, x + 4, textY, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && lastArea != null && lastArea.contains(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.gui.setScreen(new StructureSelectionScreen(mc.gui.screen()));
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
