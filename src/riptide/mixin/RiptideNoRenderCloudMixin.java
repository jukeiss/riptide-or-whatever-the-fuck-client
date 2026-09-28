package riptide.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({CloudRenderer.class})
public abstract class RiptideNoRenderCloudMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noClouds(CallbackInfo ci) {
      if (NoRenderState.noClouds()) {
         ci.cancel();
      }
   }
}
