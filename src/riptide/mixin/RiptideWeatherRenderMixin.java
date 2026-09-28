package riptide.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.WeatherControlModule;

@Mixin({WeatherEffectRenderer.class})
public class RiptideWeatherRenderMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipWeatherExtract(CallbackInfo var1) {
      if (WeatherControlModule.suppressWeatherRender()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipWeatherRender(CallbackInfo var1) {
      if (WeatherControlModule.suppressWeatherRender()) {
         var1.cancel();
      }
   }
}
