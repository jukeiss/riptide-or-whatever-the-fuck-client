package riptide.mixin;

import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({CustomHeadLayer.class})
public abstract class RiptideNoRenderHeadLayerMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noHeadArmor(CallbackInfo ci) {
      if (NoRenderState.noArmor()) {
         ci.cancel();
      }
   }
}
