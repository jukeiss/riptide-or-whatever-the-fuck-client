package riptide.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({LevelRenderer.class})
public abstract class RiptideNoRenderBlockBreakMixin {
   @Inject(
      method = {"submitBlockDestroyAnimation"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noBlockBreakOverlay(CallbackInfo ci) {
      if (NoRenderState.noBlockBreakOverlay()) {
         ci.cancel();
      }
   }
}
