package net.shiroha233.roadweaver.content.effects;


import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.shiroha233.roadweaver.config.ConfigProvider;
import net.shiroha233.roadweaver.config.IModConfig;

public class PowerOfTheRoad extends MobEffect {
    public PowerOfTheRoad() {

        super(MobEffectCategory.BENEFICIAL, 0xA8E6FF);
         }
    @Override
    public String getDescriptionId() {

        return "effect.roadweaver.power_of_the_road";
    }
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {

        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {

        IModConfig cfg = ConfigProvider.get();

        if (entity instanceof Player player && !player.level().isClientSide) {
            if (entity.isRemoved() || entity.isDeadOrDying() || !entity.isAlive()) {
                return;
            }
            if(cfg.getPowerOfTheRoadJumpLevel()>0) {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 2, cfg.getPowerOfTheRoadJumpLevel() - 1, false, false, false));
            }
            if(cfg.getPowerOfTheRoadSpeedLevel()>0) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2, cfg.getPowerOfTheRoadSpeedLevel() - 1, false, false, false));
            }
            if(cfg.getPowerOfTheRoadRegenerationLevel()>0)
            {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 2, cfg.getPowerOfTheRoadRegenerationLevel()-1, false, false, false));
            long time = player.level().getGameTime();
            if (player.getHealth() < player.getMaxHealth()&&(time % 50 == 0)) {
                player.setHealth(player.getHealth() + 1.0F);
            }
            }
        }
    }

}
