package riptide.mixin;

import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.util.RiptideSharedState;

@Mixin({ResultSlot.class})
public abstract class RiptideResultSlotMixin {
   @Inject(
      method = {"mayPlace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$xcarryMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      RiptideSharedState shared = RiptideSharedState.get();
      RiptideModule module = RiptideModule.get();
      if (shared.isXCarryForced() || module != null && module.isXCarryEnabled() && module.isXCarryUseCrafting()) {
         cir.setReturnValue(true);
      }
   }
}
