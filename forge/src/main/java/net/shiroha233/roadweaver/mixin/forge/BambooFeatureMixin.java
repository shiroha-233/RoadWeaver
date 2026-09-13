package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 竹子生成阻拦
 */
@Mixin(BambooFeature.class)
public class BambooFeatureMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void roadweaver$blockBambooOnRoad(FeaturePlaceContext<ProbabilityFeatureConfiguration> ctx,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(ctx.level(), ctx.origin())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
