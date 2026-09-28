package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.NoFogModule;

@Mixin({FogRenderer.class})
public class RiptideFogDistanceMixin {
   @ModifyReturnValue(
      method = {"setupFog"},
      at = {@At("RETURN")}
   )
   private FogData riptide$pushFogOut(FogData var1) {
      if (var1 == null) {
         return var1;
      } else {
         float var2 = NoFogModule.farDistance();
         if (var2 < 0.0F) {
            return var1;
         } else {
            var1.environmentalStart = var2;
            var1.environmentalEnd = var2 * 2.0F;
            var1.renderDistanceStart = var2;
            var1.renderDistanceEnd = var2 * 2.0F;
            return var1;
         }
      }
   }
}
