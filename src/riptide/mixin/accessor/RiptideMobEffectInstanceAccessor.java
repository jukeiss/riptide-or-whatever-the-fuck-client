package riptide.mixin.accessor;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MobEffectInstance.class})
public interface RiptideMobEffectInstanceAccessor {
   @Accessor("duration")
   void riptide$setDuration(int var1);
}
