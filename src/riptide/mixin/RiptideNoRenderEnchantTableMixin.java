package riptide.mixin;

import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({EnchantTableRenderer.class})
public abstract class RiptideNoRenderEnchantTableMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noEnchantTableBook(CallbackInfo ci) {
      if (NoRenderState.noEnchantTableBook()) {
         ci.cancel();
      }
   }
}
