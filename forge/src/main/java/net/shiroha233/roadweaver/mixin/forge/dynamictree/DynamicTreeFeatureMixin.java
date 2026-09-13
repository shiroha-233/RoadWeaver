package net.shiroha233.roadweaver.mixin.forge.dynamictree;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelSimulatedReader;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.dtteam.dynamictrees.worldgen.feature.DynamicTreeFeature")
public class DynamicTreeFeatureMixin {
    @SuppressWarnings("deprecation") // 屏蔽弃用提示
    @Inject(remap = false, method = "validTreePos",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void roadweaver$validTreePosWithRoad(LevelSimulatedReader pLevel, BlockPos pPos, CallbackInfoReturnable<Boolean> cir) {
        if (RoadPositionQuery.isOnRoad(pLevel, pPos)) {
            cir.setReturnValue(false);
        }
    }
}
