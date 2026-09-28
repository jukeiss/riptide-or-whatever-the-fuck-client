package riptide.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({HumanoidArmorLayer.class})
public abstract class RiptideNoRenderArmorLayerMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noArmor(CallbackInfo ci) {
      if (NoRenderState.noArmor()) {
         ci.cancel();
      }
   }
}
