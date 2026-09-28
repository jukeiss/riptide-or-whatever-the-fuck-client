package riptide.mixin;

import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({WorldBorderRenderer.class})
public abstract class RiptideNoRenderWorldBorderMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noWorldBorder(CallbackInfo ci) {
      if (NoRenderState.noWorldBorder()) {
         ci.cancel();
      }
   }
}
