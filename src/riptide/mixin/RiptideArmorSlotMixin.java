package riptide.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.util.RiptideSharedState;

@Mixin(
   targets = {"net.minecraft.world.inventory.ArmorSlot"}
)
public abstract class RiptideArmorSlotMixin {
   @Inject(
      method = {"mayPlace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$mayPlaceForXCarry(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
      if (riptide$armorAllowed()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"getMaxStackSize()I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$maxStackSizeForXCarry(CallbackInfoReturnable<Integer> cir) {
      if (riptide$armorAllowed()) {
         cir.setReturnValue(64);
      }
   }

   @Unique
   private static boolean riptide$armorAllowed() {
      RiptideModule mod = RiptideModule.get();
      boolean modAllow = mod != null && mod.isXCarryUseArmor();
      boolean bypass = RiptideSharedState.get().isXCarryArmorBypass();
      return modAllow || bypass;
   }
}
