package net.shiroha233.roadweaver.mixin.forge.rtf;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ReTerraForged 模组灌木生成阻拦
 * 拦截 BushFeature.place(FeaturePlaceContext) 方法
 * 注意：BushFeature 有两个 place 方法，需要精确匹配
 */
@Pseudo
@Mixin(targets = "raccoonman.reterraforged.world.worldgen.feature.BushFeature")
public class RTFBushFeatureMixin {

    /**
     * 拦截 place 方法，阻止在道路上生成灌木
     */
    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void roadweaver$blockRTFBushOnRoad(FeaturePlaceContext<?> ctx,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(ctx.level(), ctx.origin())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
