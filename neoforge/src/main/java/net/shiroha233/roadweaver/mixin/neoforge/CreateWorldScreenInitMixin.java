package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.structure.StructureDiscoveryService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * 在创建世界界面初始化后读取 RegistryAccess，供结构选择界面提前发现结构。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenInitMixin extends Screen {

    @Shadow
    @Final
    WorldCreationUiState uiState;

    protected CreateWorldScreenInitMixin() {
        super(Component.empty());
    }

    /** 注入 init 尾部，避免绑定 1.21.1 中容易变化的构造器签名。 */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitEnd(CallbackInfo ci) {
        try {
            WorldCreationContext settings = uiState != null ? uiState.getSettings() : null;
            if (settings == null) return;
            RegistryAccess.Frozen registryAccess = settings.worldgenLoadContext();
            if (registryAccess == null) return;
            StructureDiscoveryService.discoverFromRegistryAccess(registryAccess);
        } catch (Exception e) {
            // 忽略错误，不影响正常流程
        }
    }
}


