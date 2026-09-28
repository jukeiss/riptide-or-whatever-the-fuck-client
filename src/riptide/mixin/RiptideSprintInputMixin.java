package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.ModuleMovementUtil;

@Mixin({KeyboardInput.class})
public abstract class RiptideSprintInputMixin {
   @ModifyExpressionValue(
      method = {"tick"},
      at = {@At(
         value = "NEW",
         target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"
      )}
   )
   private Input riptide$sprintInputRecord(Input original) {
      if (!original.forward()) {
         return original;
      } else {
         return !original.sprint() && ModuleMovementUtil.sprintDecision(false, false)
            ? new Input(original.forward(), original.backward(), original.left(), original.right(), original.jump(), original.shift(), true)
            : original;
      }
   }
}
