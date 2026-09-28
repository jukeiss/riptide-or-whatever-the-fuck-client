package riptide.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface RiptideEntityAccessor {
   @Invoker("isInvulnerableToBase")
   boolean riptide$isInvulnerableToBase(DamageSource var1);

   @Accessor("position")
   void riptide$setPosition(Vec3 var1);

   @Invoker("getInputVector")
   static Vec3 riptide$getInputVector(Vec3 relative, float motion, float facing) {
      throw new AssertionError();
   }
}
