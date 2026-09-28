package riptide.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.RiptideModule;
import riptide.util.RiptideSharedState;

@Mixin({InventoryMenu.class})
public abstract class RiptideInventoryMenuRemovedMixin {
   @Inject(
      method = {"removed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipDrainWhenXCarryForced(Player player, CallbackInfo ci) {
      if (player instanceof LocalPlayer) {
         RiptideSharedState shared = RiptideSharedState.get();
         RiptideModule module = RiptideModule.get();
         boolean passive = module != null && module.isXCarryEnabled();
         if (passive || shared.isXCarryForced()) {
            InventoryMenu self = (InventoryMenu)this;
            if (player.inventoryMenu == self) {
               ci.cancel();
            }
         }
      }
   }
}
