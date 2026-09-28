package riptide.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.NoRenderState;

@Mixin({BossHealthOverlay.class})
public abstract class RiptideNoRenderBossHealthOverlayMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$noBossBar(CallbackInfo ci) {
      if (NoRenderState.noBossBar()) {
         ci.cancel();
      }
   }
}
