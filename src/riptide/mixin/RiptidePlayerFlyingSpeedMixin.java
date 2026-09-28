package riptide.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.ModuleMovementUtil;

@Mixin({Player.class})
public class RiptidePlayerFlyingSpeedMixin {
   @Inject(
      method = {"getFlyingSpeed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$flightFlyingSpeed(CallbackInfoReturnable<Float> cir) {
      float speed = ModuleMovementUtil.flightFlyingSpeed((Player)this);
      if (speed != -1.0F) {
         cir.setReturnValue(speed);
      }
   }
}
