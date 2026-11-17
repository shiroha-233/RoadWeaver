package net.shiroha233.roadweaver.mixin.neoforge;

import net.shiroha233.roadweaver.client.loading.LoadingGenerationOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void roadweaver$renderProgress(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        LoadingGenerationOverlayRenderer.render(graphics);
    }
}
