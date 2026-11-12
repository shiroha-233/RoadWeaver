package net.shiroha233.roadweaver.mixin.fabric;

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
