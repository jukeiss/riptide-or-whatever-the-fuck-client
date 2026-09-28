package riptide.mixin;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.SkyboxRenderer;

@Mixin({CloudRenderer.class})
public abstract class RiptideSkyboxCloudMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$skyboxNoClouds(CallbackInfo ci) {
      if (SkyboxRenderer.isActive()) {
         ci.cancel();
      }
   }
}
