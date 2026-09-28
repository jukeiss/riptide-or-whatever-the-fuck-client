package riptide.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({WeatherEffectRenderer.class})
public abstract class RiptideNoRenderWeatherMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noWeather(CallbackInfo ci) {
      if (NoRenderState.noWeather()) {
         ci.cancel();
      }
   }
}
