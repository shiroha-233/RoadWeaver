package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.shiroha233.roadweaver.worldgen.road.RoadPositionQuery;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 通用植物 Feature 拦截器
 * 26.3 移除了 ConfiguredFeature，改为拦截 PlacedFeature.place()——所有已放置 Feature 的统一入口
 * 以入口坐标为判定点，兼容 C2ME 的多线程世界生成
 */
@Mixin(PlacedFeature.class)
public abstract class GenericVegetationFeatureMixin {

    @Shadow
    @Final
    private Holder<Feature> feature;

    @Unique
    private volatile Boolean roadweaver$treeLike;

    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
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

    @Unique
    private boolean roadweaver$isTreeLikeFeature() {
        Boolean cached = this.roadweaver$treeLike;
        if (cached != null) return cached;
        if (this.feature == null) return false;

        String featureClassName = this.feature.value().getClass().getName().toLowerCase();

        if (roadweaver$isVanillaTreeFeature(featureClassName)) {
            this.roadweaver$treeLike = false;
            return false;
        }

        if (roadweaver$containsTreeKeyword(featureClassName)) {
            this.roadweaver$treeLike = true;
            return true;
        }

        this.roadweaver$treeLike = false;
        return false;
    }

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
