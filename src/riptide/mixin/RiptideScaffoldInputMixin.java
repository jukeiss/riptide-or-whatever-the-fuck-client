package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.AutoArmorModule;
import riptide.modules.AutoTotemModule;
import riptide.modules.BedDefenderModule;
import riptide.modules.SafeWalkModule;
import riptide.modules.ScaffoldModule;
import riptide.util.RiptideSilentAim;

@Mixin({KeyboardInput.class})
public abstract class RiptideScaffoldInputMixin extends ClientInput {
   @ModifyExpressionValue(
      method = {"tick"},
      at = {@At(
         value = "NEW",
         target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"
      )}
   )
   private Input riptide$optionalScaffoldStabilization(Input original) {
      Input scaffold = ScaffoldModule.modifyMovementInput(this, original);
      Input aura = RiptideSilentAim.modifyMovementInput(this, scaffold);
      Input bed = BedDefenderModule.modifyMovementInput(this, aura);
      Input safe = SafeWalkModule.modifyMovementInput(this, bed);
      Input totem = AutoTotemModule.modifyMovementInput(this, safe);
      return AutoArmorModule.modifyMovementInput(this, totem);
   }
}
