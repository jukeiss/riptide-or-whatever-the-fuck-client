package riptide.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.AutoTotemModule;
import riptide.modules.NoRenderState;

@Mixin({GameRenderer.class})
public abstract class RiptideNoRenderTotemMixin {
   @Inject(
      method = {"displayItemActivation"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noTotemAnimation(ItemStack stack, CallbackInfo ci) {
      if (NoRenderState.noTotemAnimation() || AutoTotemModule.hidesTotemAnimation()) {
         ci.cancel();
      }
   }
}
