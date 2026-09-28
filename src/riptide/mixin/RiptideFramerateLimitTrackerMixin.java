package riptide.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.gui.screen.RiptideModuleScreen;
import riptide.gui.screen.RiptideTitleScreen;
import riptide.util.RiptideLiteVariant;
import riptide.util.macro.FpsLimitController;

@Mixin({FramerateLimitTracker.class})
public abstract class RiptideFramerateLimitTrackerMixin {
   @Inject(
      method = {"getFramerateLimit"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void riptide$applyFpsAction(CallbackInfoReturnable<Integer> cir) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null) {
         int override = FpsLimitController.activeLimit();
         if (override >= 0) {
            cir.setReturnValue(Math.min(override, cir.getReturnValueI()));
            return;
         }
      }

      if (minecraft != null) {
         boolean ours = !RiptideLiteVariant.enabled()
            && (minecraft.gui.screen() instanceof RiptideTitleScreen || minecraft.gui.screen() instanceof RiptideModuleScreen screen && screen.isTitleSetup());
         if (ours) {
            FramerateLimitTracker tracker = (FramerateLimitTracker)this;
            if (tracker.getThrottleReason() == FramerateThrottleReason.OUT_OF_LEVEL_MENU) {
               cir.setReturnValue((Integer)minecraft.options.framerateLimit().get());
            }
         }
      }
   }
}
