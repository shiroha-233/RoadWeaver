package net.shiroha233.roadweaver.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件，用于条件加载可选模组的 Mixin。
 * <p>
 * 当目标模组（如 DynamicTrees）未安装时，跳过对应的 Mixin，避免类加载失败。
 * </p>
 * <p>
 * 注意：MixinConfigPlugin 在 Mixin 子系统启动时非常早期就被加载，
 * 此时 ModList 还未初始化，只能使用 LoadingModList。
 * </p>
 */
public class MixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // DynamicTrees 相关的 Mixin 只在模组存在时加载
        if (mixinClassName.contains("DynamicTree")) {
            return isModLoaded("dynamictrees");
        }
        
        // Oh-The-Trees-Youll-Grow 相关的 Mixin
        if (mixinClassName.contains("TreeFromStructureNBTFeatureMixin")) {
            return isModLoaded("ohthetreesyoullgrow");
        }
        
        // ReTerraForged 相关的 Mixin
        if (mixinClassName.contains("RTFTemplateFeatureMixin") || 
            mixinClassName.contains("RTFBushFeatureMixin")) {
            return isModLoaded("reterraforged");
        }

        return true;
    }

    /**
     * 检查模组是否已加载。
     * <p>
     * 在 Mixin 加载阶段，ModList 还未初始化，只能使用 LoadingModList。
     * </p>
     */
    private boolean isModLoaded(String modid) {
        // 在 Mixin 加载阶段，只有 LoadingModList 可用
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        if (loadingModList != null) {
            return loadingModList.getModFileById(modid) != null;
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
