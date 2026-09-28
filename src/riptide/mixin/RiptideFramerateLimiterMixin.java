package riptide.mixin;

import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.macro.FpsLimitController;

@Mixin({FramerateLimiter.class})
public abstract class RiptideFramerateLimiterMixin {
   private static final long FREEZE_RECHECK_NANOS = 20000000L;

   @Inject(
      method = {"limitDisplayFPS"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void riptide$freezeWhenZero(int framerateLimit, CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null && minecraft.gui.screen() == null && FpsLimitController.shouldFreeze()) {
         boolean logged = false;

         while (FpsLimitController.shouldFreeze() && Minecraft.getInstance().level != null && Minecraft.getInstance().gui.screen() == null) {
            if (!logged) {
               logged = true;
               riptide.RiptideClientAddon.LOG.warn("[FPS] Render freeze engaged (macro FPS 0); auto-releases on screen open or failsafe expiry");
            }

            LockSupport.parkNanos(20000000L);
            if (Thread.interrupted()) {
               break;
            }
         }

         ci.cancel();
      }
   }
}
