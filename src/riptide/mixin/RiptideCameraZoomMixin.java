package riptide.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import riptide.modules.ZoomModule;

@Mixin({Camera.class})
public class RiptideCameraZoomMixin {
   @ModifyReturnValue(
      method = {"getFov"},
      at = {@At("RETURN")}
   )
   private float riptide$applyZoom(float var1) {
      return ZoomModule.apply(var1);
   }
}
