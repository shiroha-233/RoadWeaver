package net.shiroha233.roadweaver.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 简单文本输入界面
 */
public class SimpleTextInputScreen extends RoadWeaverScreen {
    private final Component titleText;
    private final String initial;
    private final Consumer<String> onSubmit;
    private final Screen parent;

    private EditBox box;

    public SimpleTextInputScreen(Component titleText, String initial, Consumer<String> onSubmit) {
        this(titleText, initial, onSubmit, null);
    }

    public SimpleTextInputScreen(Component titleText, String initial, Consumer<String> onSubmit, Screen parent) {
        super(titleText);
        this.titleText = titleText;
        this.initial = initial != null ? initial : "";
        this.onSubmit = onSubmit;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 240;
        int x = (this.width - w) / 2;
        int y = this.height / 2 - 20;
        box = new EditBox(this.font, x, y, w, 20, titleText);
        box.setMaxLength(512);
        box.setValue(initial);
        this.addRenderableWidget(box);

        int bw = 80;
        int by = y + 28;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.ok"), b -> submit())
                .bounds(x, by, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.roadweaver.common.cancel"), b -> cancel())
                .bounds(x + w - bw, by, bw, 20).build());

        this.setInitialFocus(box);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     float partialTick) {
        ScreenBackgrounds.render(graphics, this.width, this.height);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.titleText, this.width / 2, this.height / 2 - 34, 0xFFFFFF);
    }

    private void submit() {
        if (onSubmit != null) onSubmit.accept(box.getValue());
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void cancel() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();

        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        if (keyCode == 256) {
            cancel();
            return true;
        }
        return super.keyPressed(event);
    }
}
