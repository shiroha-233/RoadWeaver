package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeGrassColorFailSafeMixin {

    @Shadow
    public abstract BiomeSpecialEffects getSpecialEffects();

    @Invoker("getGrassColorFromTexture")
    protected abstract int roadweaver$invokeGetGrassColorFromTexture();

    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private void roadweaver$failsafeGrassColor(double x, double z, CallbackInfoReturnable<Integer> cir) {
        // 兼容性兜底：部分第三方模组会动态扩展 GrassColorModifier 枚举，若实现/变换异常可能触发 AbstractMethodError。
        // 我们在渲染线程上兜底，避免直接崩溃（退化为基础草色）。
        BiomeSpecialEffects effects = this.getSpecialEffects();
        int base = effects.getGrassColorOverride().orElseGet(this::roadweaver$invokeGetGrassColorFromTexture);
        try {
            int out = effects.getGrassColorModifier().modifyColor(x, z, base);
            cir.setReturnValue(out);
        } catch (AbstractMethodError e) {
            cir.setReturnValue(base);
        }
    }
}
