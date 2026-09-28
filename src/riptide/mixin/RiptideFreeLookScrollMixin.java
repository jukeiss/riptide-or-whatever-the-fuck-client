package riptide.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.FreeLookZoomModule;

@Mixin({MouseHandler.class})
public abstract class RiptideFreeLookScrollMixin {
   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$freeLookScroll(long var1, double var3, double var5, CallbackInfo var7) {
      try {
         if (FreeLookZoomModule.handleScroll(var5)) {
            var7.cancel();
         }
      } catch (Throwable var9) {
      }
   }
}
