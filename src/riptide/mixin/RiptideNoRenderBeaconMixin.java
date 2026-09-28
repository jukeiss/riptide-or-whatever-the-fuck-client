package riptide.mixin;

import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({BeaconRenderer.class})
public abstract class RiptideNoRenderBeaconMixin {
   @Inject(
      method = {"submitBeaconBeam"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void riptide$noBeaconBeams(CallbackInfo ci) {
      if (NoRenderState.noBeaconBeams()) {
         ci.cancel();
      }
   }
}
