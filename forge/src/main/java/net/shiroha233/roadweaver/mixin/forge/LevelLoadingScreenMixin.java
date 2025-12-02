package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.client.tips.LoadingTipsRenderer;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void roadweaver$renderProgress(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        LoadingTipsRenderer.render(graphics);
        if (!InitialGenManager.isActive())
            return;
        int total = InitialGenManager.getTotal();
        int done = InitialGenManager.getDone();

        int generating = InitialGenManager.getGenerating();
        int failed = InitialGenManager.getFailed();
        int percent = (total <= 0) ? 0 : (int) Math.round(100.0 * done / Math.max(1, total));

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Component title = Objects.requireNonNull(Component.translatable("gui.roadweaver.initgen.title"));
        Component summary = Objects.requireNonNull(Component.translatable("gui.roadweaver.initgen.summary", total, generating, done,
                failed));
        Component progress = Objects.requireNonNull(Component.translatable("gui.roadweaver.initgen.progress", done, total, percent));

        int y = sh - 60;

        int x = (sw - font.width(title)) / 2;
        graphics.drawString(font, title, x, y, 0xFFFFFF, false);
        y += 12;

        x = (sw - font.width(summary)) / 2;
        graphics.drawString(font, summary, x, y, 0xA0A0A0, false);
        y += 12;

        x = (sw - font.width(progress)) / 2;
        graphics.drawString(font, progress, x, y, 0xA0FFA0, false);
    }
}
