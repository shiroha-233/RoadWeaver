package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用 Feature 拦截器，阻止树木类 Feature 在道路上生成。
 * 注入到非抽象的 5 参数 place 方法，通过类名和配置类型判断是否为树木相关的 Feature。
 */
@Mixin(Feature.class)
public abstract class GenericFeatureMixin {

    /**
     * 注入到 place(FC, WorldGenLevel, ChunkGenerator, RandomSource, BlockPos) 方法
     */
    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void roadweaver$skipTreeLikeFeaturesOnRoad(FeatureConfiguration config,
                                                        WorldGenLevel level,
                                                        ChunkGenerator chunkGenerator,
                                                        RandomSource random,
                                                        BlockPos pos,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (roadweaver$isTreeLikeFeature(config)) {
            if (RoadPositionQuery.isOnRoad(level, pos)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * 判断是否为树木类 Feature（通过类名和配置类型）
     */
    @Unique
    private boolean roadweaver$isTreeLikeFeature(FeatureConfiguration config) {
        // 检查 Feature 类名
        String className = this.getClass().getName().toLowerCase();
        if (roadweaver$containsTreeKeyword(className)) {
            return true;
        }

        // 检查配置类型
        if (config != null) {
            String configClassName = config.getClass().getName().toLowerCase();
            if (roadweaver$containsTreeKeyword(configClassName)) {
                return true;
            }
            // 原版 TreeConfiguration 及其子类
            if (config instanceof TreeConfiguration) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查类名是否包含树木相关关键字
     */
    @Unique
    private boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("chorus") ||
               name.contains("cactus") ||
               name.contains("nbt");  // 覆盖 NBT 结构树（如 Oh-The-Biomes-We've-Gone）
    }
}
