package riptide.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ScreenEffectsModule;

@Mixin({GameRenderer.class})
public class RiptideHurtCameraMixin {
   @Inject(
      method = {"bobHurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipHurtCamera(CallbackInfo var1) {
      if (ScreenEffectsModule.suppressHurtCamera()) {
         var1.cancel();
      }
   }
}
