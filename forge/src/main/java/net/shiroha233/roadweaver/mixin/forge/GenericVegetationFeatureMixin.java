/* 文件职责：在 Forge 植被 Feature 入口复用道路占用查询并缓存特征分类。 */
package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * 通用植物 Feature 拦截器
 * 拦截 ConfiguredFeature.place() 方法，这是所有 Feature 生成的统一入口
 * 兼容 C2ME 的多线程世界生成和对象池优化
 */
@Mixin(ConfiguredFeature.class)
public class GenericVegetationFeatureMixin<FC extends FeatureConfiguration, F extends Feature<FC>> {

    @Shadow @Final public F feature;
    @Shadow @Final public FC config;

    @Unique
    private volatile Boolean roadweaver$treeLike;

    /**
     * 拦截 ConfiguredFeature.place() 方法
     * 这是所有 Feature 生成的入口点，包括 C2ME 优化后的调用
     */
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void roadweaver$blockTreesOnRoad(WorldGenLevel level,
                                              ChunkGenerator generator,
                                              RandomSource random,
                                              BlockPos pos,
                                              CallbackInfoReturnable<Boolean> cir) {
        try {
            if (roadweaver$isTreeLikeFeature()) {
                if (RoadPositionQuery.isOnRoad(level, pos)) {
                    cir.setReturnValue(false);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 判断是否为树木类 Feature
     */
    @Unique
    private boolean roadweaver$isTreeLikeFeature() {
        Boolean cached = this.roadweaver$treeLike;
        if (cached != null) return cached;
        if (this.feature == null) return false;
        
        String featureClassName = this.feature.getClass().getName().toLowerCase();
        
        // 排除已有专门 Mixin 的原版类（避免重复检查）
        if (roadweaver$isVanillaTreeFeature(featureClassName)) {
            this.roadweaver$treeLike = false;
            return false;
        }
        
        // 检查 Feature 类名
        if (roadweaver$containsTreeKeyword(featureClassName)) {
            this.roadweaver$treeLike = true;
            return true;
        }
        
        // 检查配置类名
        if (this.config != null) {
            String configName = this.config.getClass().getName().toLowerCase();
            if (roadweaver$containsTreeKeyword(configName)) {
                this.roadweaver$treeLike = true;
                return true;
            }
        }
        
        this.roadweaver$treeLike = false;
        return false;
    }

    /**
     * 判断是否为已有专门 Mixin 的原版树木类
     */
    @Unique
    private boolean roadweaver$isVanillaTreeFeature(String className) {
        return className.equals("net.minecraft.world.level.levelgen.feature.treefeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.abstracthugemushroomfeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.hugefungusfeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.bamboofeature");
    }

    @Unique
    private boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("cactus") ||
               name.contains("chorus") ||
               name.contains("nbt") ||
               name.contains("structure") ||
               name.contains("fromstructure") ||
               name.contains("template") ||
               name.contains("bush");
    }
}
