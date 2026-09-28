package riptide.mixin;

import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({SkyRenderer.class})
public abstract class RiptideNoRenderSkyMixin {
   @Inject(
      method = {"renderSkyDisc"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noSkyDisc(CallbackInfo ci) {
      if (NoRenderState.noSky()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderEndSky"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noEndSky(CallbackInfo ci) {
      if (NoRenderState.noSky()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderStars"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noStars(CallbackInfo ci) {
      if (NoRenderState.noStars()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSun"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noSun(CallbackInfo ci) {
      if (NoRenderState.noSun()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderMoon"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noMoon(CallbackInfo ci) {
      if (NoRenderState.noMoon()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSunriseAndSunset"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noSunriseGlow(CallbackInfo ci) {
      if (NoRenderState.noSunriseGlow()) {
         ci.cancel();
      }
   }
}
