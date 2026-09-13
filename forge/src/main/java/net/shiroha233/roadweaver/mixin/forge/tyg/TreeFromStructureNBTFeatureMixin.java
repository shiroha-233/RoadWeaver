package net.shiroha233.roadweaver.mixin.forge.tyg;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Oh-The-Trees-Youll-Grow 模组树木生成阻拦
 * 拦截 TreeFromStructureNBTFeature.place() 方法
 */
@Pseudo
@Mixin(targets = "dev.corgitaco.ohthetreesyoullgrow.world.level.levelgen.feature.TreeFromStructureNBTFeature")
public class TreeFromStructureNBTFeatureMixin {

    /**
     * 拦截 place 方法，阻止在道路上生成树木
     */
    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void roadweaver$blockTYGTreeOnRoad(FeaturePlaceContext<?> ctx,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(ctx.level(), ctx.origin())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
