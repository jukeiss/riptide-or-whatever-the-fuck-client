package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.WeatherControlModule;

@Mixin({Level.class})
public class RiptideWeatherLevelMixin {
   @ModifyReturnValue(
      method = {"getRainLevel"},
      at = {@At("RETURN")}
   )
   private float riptide$clearRain(float var1) {
      float var2 = WeatherControlModule.rainLevelOverride();
      return var2 < 0.0F ? var1 : var2;
   }

   @ModifyReturnValue(
      method = {"getThunderLevel"},
      at = {@At("RETURN")}
   )
   private float riptide$clearThunder(float var1) {
      float var2 = WeatherControlModule.thunderLevelOverride();
      return var2 < 0.0F ? var1 : var2;
   }
}
