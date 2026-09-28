package riptide.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.GoldenLeverModule;
import riptide.modules.NameCensorModule;

@Mixin({ItemStack.class})
public class RiptideItemStackMixin {
   @Inject(
      method = {"getHoverName"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$goldenLeverName(CallbackInfoReturnable<Component> cir) {
      ItemStack stack = (ItemStack)this;
      if (GoldenLeverModule.shouldStyle(stack)) {
         cir.setReturnValue(GoldenLeverModule.leverName());
      }
   }

   @Inject(
      method = {"getHoverName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$censorHoverName(CallbackInfoReturnable<Component> cir) {
      cir.setReturnValue(NameCensorModule.censorComponent((Component)cir.getReturnValue()));
   }
}
