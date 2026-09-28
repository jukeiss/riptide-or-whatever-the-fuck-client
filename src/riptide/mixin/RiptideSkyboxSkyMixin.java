package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.SkyboxRenderer;

@Mixin({SkyRenderer.class})
public abstract class RiptideSkyboxSkyMixin {
   @Inject(
      method = {"renderSkyDisc"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$skyboxPanorama(CallbackInfo ci) {
      if (SkyboxRenderer.isActive()) {
         SkyboxRenderer.render(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSunMoonAndStars"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$skyboxNoSunMoonStars(CallbackInfo ci) {
      if (SkyboxRenderer.isActive()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSunriseAndSunset"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$skyboxNoSunriseGlow(CallbackInfo ci) {
      if (SkyboxRenderer.isActive()) {
         ci.cancel();
      }
   }
}
