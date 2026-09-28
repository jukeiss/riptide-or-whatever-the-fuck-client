package riptide.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.HudCleanerModule;

@Mixin({Hud.class})
public class RiptideHudCleanerMixin {
   @Inject(
      method = {"extractScoreboardSidebar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipScoreboard(CallbackInfo var1) {
      if (HudCleanerModule.suppressScoreboard()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"extractBossOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipBossBars(CallbackInfo var1) {
      if (HudCleanerModule.suppressBossBars()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"extractTitle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipTitles(CallbackInfo var1) {
      if (HudCleanerModule.suppressTitles()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"extractOverlayMessage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipActionBar(CallbackInfo var1) {
      if (HudCleanerModule.suppressActionBar()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"extractVignette"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$skipVignette(CallbackInfo var1) {
      if (HudCleanerModule.suppressVignette()) {
         var1.cancel();
      }
   }
}
