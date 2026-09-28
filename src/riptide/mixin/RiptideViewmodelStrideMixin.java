package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.ViewmodelState;

@Mixin({ClientAvatarState.class})
public abstract class RiptideViewmodelStrideMixin {
   @Shadow
   private float bob;

   @Inject(
      method = {"updateBob"},
      at = {@At("RETURN")},
      require = 0
   )
   private void riptide$airWalker(float movement, CallbackInfo ci) {
      if (ViewmodelState.airWalker()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.player != null) {
            this.bob = (float)Math.min(0.1, mc.player.getDeltaMovement().horizontalDistance());
         }
      }
   }
}
