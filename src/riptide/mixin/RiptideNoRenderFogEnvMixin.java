package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.NoRenderState;

@Mixin({MobEffectFogEnvironment.class})
public abstract class RiptideNoRenderFogEnvMixin {
   @ModifyReturnValue(
      method = {"isApplicable"},
      at = {@At("RETURN")},
      require = 0
   )
   private boolean riptide$noEffectFog(boolean original) {
      if (!original) {
         return false;
      } else {
         return this instanceof BlindnessFogEnvironment && NoRenderState.noBlindness()
            ? false
            : !(this instanceof DarknessFogEnvironment) || !NoRenderState.noDarkness();
      }
   }
}
