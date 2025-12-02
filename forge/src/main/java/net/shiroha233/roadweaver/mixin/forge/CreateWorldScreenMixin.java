package net.shiroha233.roadweaver.mixin.forge;

import net.shiroha233.roadweaver.client.forge.ConfigScreenFactoryImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Mixin 用于在创建世界界面添加 RoadWeaver 配置按钮
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldScreenMixin extends GridLayoutTab {

    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addConfigButton(CallbackInfo ci) {
        Button configButton = Button.builder(
                Objects.requireNonNull(Component.translatable("gui.roadweaver.config_button")),
                button -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.screen instanceof CreateWorldScreen screen) {
                        mc.setScreen(ConfigScreenFactoryImpl.createConfigScreen((Screen) screen));
                    }
                })
                .width(210)
                .build();
        int row = net.minecraft.SharedConstants.getCurrentVersion().isStable() ? 4 : 5;
        this.layout.addChild(Objects.requireNonNull(configButton), row, 0, Objects.requireNonNull(this.layout.newCellSettings().alignHorizontallyCenter()));
    }
}
