package net.shiroha233.roadweaver.mixin.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.shiroha233.roadweaver.worldgen.road.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 巨型蘑菇生成阻拦
 *
 * 26.3 起 AbstractHugeMushroomFeature 变为接口，place 是接口默认方法，
 * 因此 mixin 本身也必须是接口才能注入默认方法。
 */
@Mixin(AbstractHugeMushroomFeature.class)
public interface HugeMushroomFeatureMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    default void roadweaver$blockMushroomOnRoad(WorldGenLevel level,
                                                ChunkGenerator generator,
                                                RandomSource random,
                                                BlockPos pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(level, pos)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
