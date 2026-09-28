package riptide.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ScreenEffectsModule;

@Mixin({ScreenEffectRenderer.class})
public class RiptideScreenEffectMixin {
   @Inject(
      method = {"submitFire"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$skipFire(CallbackInfo var0) {
      if (ScreenEffectsModule.suppressFire()) {
         var0.cancel();
      }
   }

   @Inject(
      method = {"submitWater"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$skipWater(CallbackInfo var0) {
      if (ScreenEffectsModule.suppressWater()) {
         var0.cancel();
      }
   }

   @Inject(
      method = {"submitBlockSprite"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$skipBlockOverlay(CallbackInfo var0) {
      if (ScreenEffectsModule.suppressBlockOverlay()) {
         var0.cancel();
      }
   }
}
