package riptide.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideSilentAim;

@Mixin({Entity.class})
public abstract class RiptideScaffoldVelocityMixin {
   @ModifyArg(
      method = {"moveRelative"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
      ),
      index = 2
   )
   private float riptide$scaffoldSilentMovementYaw(float vanillaYaw) {
      Entity entity = (Entity)this;
      float scaffold = ScaffoldModule.correctedMovementYaw(entity, vanillaYaw);
      return RiptideSilentAim.correctedMovementYaw(entity, scaffold);
   }
}
