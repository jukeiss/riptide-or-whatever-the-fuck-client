package riptide.mixin;

import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideChamsContext;

@Mixin({CapeLayer.class})
public abstract class RiptideChamsCapeLayerMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$hideCapeForChams(CallbackInfo ci) {
      if (RiptideChamsContext.active()) {
         ci.cancel();
      }
   }
}
