package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.EntityControlModule;

@Mixin({Pig.class, Strider.class})
public abstract class RiptideSteerableEntityControlMixin {
   @ModifyReturnValue(
      method = {"getControllingPassenger"},
      at = {@At("RETURN")}
   )
   private LivingEntity riptide$entityControlSteer(LivingEntity original) {
      return (LivingEntity)(original == null && EntityControlModule.shouldControlSteer((Entity)this) ? Minecraft.getInstance().player : original);
   }
}
