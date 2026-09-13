package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 下界真菌生成阻拦
 */
@Mixin(HugeFungusFeature.class)
public class HugeFungusFeatureMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void roadweaver$blockFungusOnRoad(FeaturePlaceContext<HugeFungusConfiguration> ctx,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(ctx.level(), ctx.origin())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
