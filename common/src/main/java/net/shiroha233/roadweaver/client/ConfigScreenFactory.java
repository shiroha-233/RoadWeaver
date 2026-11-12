package net.shiroha233.roadweaver.client;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.gui.screens.Screen;

/**
 * 配置屏幕工厂 - 平台抽象接口
 * 使用 Architectury 的 ExpectPlatform 机制实现跨平台调用
 */
public class ConfigScreenFactory {
    
    @ExpectPlatform
    public static Screen createConfigScreen(Screen parent) {
        throw new AssertionError("该方法应该由平台特定代码实现！");
    }
}
