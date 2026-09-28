package riptide.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({Screen.class})
public abstract class RiptideNoRenderScreenMixin {
   @Inject(
      method = {"extractTransparentBackground"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noGuiBackground(CallbackInfo ci) {
      if (NoRenderState.noGuiBackground()) {
         ci.cancel();
      }
   }
}
