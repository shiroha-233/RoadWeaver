package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
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

    /** 注入 init 尾部，避免绑定容易变化的构造器签名。 */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitEnd(CallbackInfo ci) {
        // 从 WorldCreationContext 获取 RegistryAccess 并发现结构。
        try {
            WorldCreationContext settings = uiState != null ? uiState.getSettings() : null;
            if (settings != null) {
                RegistryAccess.Frozen registryAccess = settings.worldgenLoadContext();
                if (registryAccess == null) return;

                Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
                Registry<LevelStem> levelStemRegistry = null;
                try {
                    levelStemRegistry = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
                } catch (Exception ignored) {
                }

                if (levelStemRegistry != null) {
                    StructureDiscoveryService.discoverFromRegistries(structureRegistry, levelStemRegistry);
                } else {
                    StructureDiscoveryService.discoverFromRegistryAccess(registryAccess);
                }
            }
        } catch (Exception e) {
            // 蹇界暐閿欒锛屼笉褰卞搷姝ｅ父娴佺▼
        }
    }
}

