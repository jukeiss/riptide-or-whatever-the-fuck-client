package riptide.mixin;

import net.minecraft.client.renderer.blockentity.BannerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({BannerRenderer.class})
public abstract class RiptideNoRenderBannerMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noBanner(CallbackInfo ci) {
      if (NoRenderState.noBanners()) {
         ci.cancel();
      }
   }
}
