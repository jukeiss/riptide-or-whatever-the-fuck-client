package riptide.mixin;

import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({AbstractSignRenderer.class})
public abstract class RiptideNoRenderSignMixin {
   @Inject(
      method = {"submitSignText"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noSignText(CallbackInfo ci) {
      if (NoRenderState.noSignText()) {
         ci.cancel();
      }
   }
}
