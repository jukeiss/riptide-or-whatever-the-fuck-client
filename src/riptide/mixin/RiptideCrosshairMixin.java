package riptide.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.CrosshairModule;

@Mixin({Hud.class})
public class RiptideCrosshairMixin {
   @Inject(
      method = {"extractCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$replaceCrosshair(GuiGraphicsExtractor var1, DeltaTracker var2, CallbackInfo var3) {
      if (CrosshairModule.replacesVanilla()) {
         CrosshairModule.draw(var1);
         var3.cancel();
      }
   }
}
