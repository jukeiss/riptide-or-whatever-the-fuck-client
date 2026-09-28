package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.EntityControlModule;

@Mixin({AbstractBoat.class})
public abstract class RiptideAbstractBoatEntityControlMixin {
   @ModifyExpressionValue(
      method = {"controlBoat"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputLeft:Z",
         opcode = 180
      )}
   )
   private boolean riptide$lockLeftTurn(boolean original) {
      return EntityControlModule.shouldLockBoatYaw() ? false : original;
   }

   @ModifyExpressionValue(
      method = {"controlBoat"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputRight:Z",
         opcode = 180
      )}
   )
   private boolean riptide$lockRightTurn(boolean original) {
      return EntityControlModule.shouldLockBoatYaw() ? false : original;
   }
}
