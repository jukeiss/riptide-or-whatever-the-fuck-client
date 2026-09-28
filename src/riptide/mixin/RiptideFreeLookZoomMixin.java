package riptide.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.FreeLookZoomModule;

@Mixin({Camera.class})
public abstract class RiptideFreeLookZoomMixin {
   @ModifyVariable(
      method = {"getMaxZoom"},
      at = {@At("HEAD")},
      argsOnly = true
   )
   private float riptide$freeLookDistance(float var1) {
      try {
         float var2 = FreeLookZoomModule.activeDistance();
         return var2 > 0.0F ? var2 : var1;
      } catch (Throwable var3) {
         return var1;
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$freeLookRenderAround(CameraRenderState var1, float var2, CallbackInfo var3) {
      try {
         if (FreeLookZoomModule.renderAround()) {
            var1.smartCull = false;
         }
      } catch (Throwable var5) {
      }
   }
}
