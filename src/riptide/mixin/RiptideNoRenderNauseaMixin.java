package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.NoRenderState;

@Mixin({GameRenderer.class})
public abstract class RiptideNoRenderNauseaMixin {
   @ModifyExpressionValue(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Math;max(FF)F",
         ordinal = 0
      )},
      require = 0
   )
   private float riptide$noNauseaWarp(float original) {
      return NoRenderState.noNausea() ? 0.0F : original;
   }
}
