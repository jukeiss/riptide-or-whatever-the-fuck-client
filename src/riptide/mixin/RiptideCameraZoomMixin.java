package riptide.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.CustomFovModule;
import riptide.modules.ZoomModule;

// Plain Sponge Mixin (no MixinExtras): inject at getFov's RETURN, read the
// current value and overwrite it with Zoom then Custom FOV applied.
@Mixin({Camera.class})
public class RiptideCameraZoomMixin {
   @Inject(
      method = {"getFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$applyZoom(CallbackInfoReturnable<Float> cir) {
      cir.setReturnValue(ZoomModule.apply(CustomFovModule.apply(cir.getReturnValueF())));
   }
}
