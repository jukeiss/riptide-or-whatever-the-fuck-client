package riptide.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideFakeScoreboard;

@Mixin({Hud.class})
public abstract class RiptideFakeScoreboardMixin {
   @Inject(
      method = {"extractScoreboardSidebar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$replaceSidebar(GuiGraphicsExtractor var1, DeltaTracker var2, CallbackInfo var3) {
      if (RiptideFakeScoreboard.active()) {
         var3.cancel();
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void riptide$drawFakeSidebar(GuiGraphicsExtractor var1, DeltaTracker var2, CallbackInfo var3) {
      RiptideFakeScoreboard.render(var1);
   }
}
