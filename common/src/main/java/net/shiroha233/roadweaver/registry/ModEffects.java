package net.shiroha233.roadweaver.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.shiroha233.roadweaver.content.effects.PowerOfTheRoad;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create("roadweaver", Registries.MOB_EFFECT);

    public static final RegistrySupplier<MobEffect> POWER_OF_THE_ROAD =
            EFFECTS.register("power_of_the_road", PowerOfTheRoad::new);

    public static void register() {
        EFFECTS.register();
    }
}
