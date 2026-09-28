package riptide.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({PlayerTeam.class})
public class RiptidePlayerTeamMixin {
   @Inject(
      method = {"getFormattedName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorTeamName(CallbackInfoReturnable<MutableComponent> cir) {
      if (NameCensorModule.isActive()) {
         MutableComponent original = (MutableComponent)cir.getReturnValue();
         Component censored = NameCensorModule.censorServerComponent(original);
         if (censored != original) {
            cir.setReturnValue(censored.copy());
         }
      }
   }
}
