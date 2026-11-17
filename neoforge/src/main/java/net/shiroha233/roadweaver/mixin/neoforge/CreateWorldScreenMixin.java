package net.shiroha233.roadweaver.mixin.neoforge;

import net.shiroha233.roadweaver.client.neoforge.ConfigScreenFactoryImpl;
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
                Component.translatable("gui.roadweaver.config_button"),
                button -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui.screen() instanceof CreateWorldScreen screen) {
                        mc.gui.setScreen(ConfigScreenFactoryImpl.createConfigScreen((Screen) screen));
                    }
                })
                .width(210)
                .build();
        int row = net.minecraft.SharedConstants.getCurrentVersion().stable() ? 4 : 5;
        this.layout.addChild(configButton, row, 0, this.layout.newCellSettings().alignHorizontallyCenter());
    }
}
