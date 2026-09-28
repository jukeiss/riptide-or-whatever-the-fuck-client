package riptide.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LivingEntity.class})
public interface RiptideLivingEntityAccessor {
   @Accessor("attackStrengthTicker")
   int riptide$getAttackStrengthTicker();

   @Accessor("autoSpinAttackDmg")
   float riptide$getAutoSpinAttackDmg();

   @Invoker("getDamageAfterArmorAbsorb")
   float riptide$getDamageAfterArmorAbsorb(DamageSource var1, float var2);

   @Invoker("getDamageAfterMagicAbsorb")
   float riptide$getDamageAfterMagicAbsorb(DamageSource var1, float var2);

   @Invoker("calculateFallDamage")
   int riptide$calculateFallDamage(double var1, float var3);
}
