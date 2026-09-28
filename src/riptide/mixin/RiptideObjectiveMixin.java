package riptide.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.NameCensorModule;

@Mixin({Objective.class})
public class RiptideObjectiveMixin {
   @Inject(
      method = {"getDisplayName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorObjectiveName(CallbackInfoReturnable<Component> cir) {
      cir.setReturnValue(NameCensorModule.censorServerComponent((Component)cir.getReturnValue()));
   }
}
