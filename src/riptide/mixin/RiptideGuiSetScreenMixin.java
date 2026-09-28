package riptide.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.screen.RiptidePanicTitleScreen;
import riptide.gui.screen.RiptidePauseScreen;
import riptide.gui.screen.RiptideTitleScreen;
import riptide.gui.screen.RiptideWelcomeScreen;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.modules.PackHideState;
import riptide.util.RiptideConfig;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideMenuPrefs;
import riptide.util.RiptideWelcomeGate;
import riptide.util.macro.MacroExecutor;

@Mixin({Gui.class})
public class RiptideGuiSetScreenMixin {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private boolean riptide$replacingScreen;

   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$replaceScreen(Screen screen, CallbackInfo ci) {
      CompactTextInput.clearFocusedInput();
      if (!this.riptide$replacingScreen) {
         MacroExecutor.recordRecentGuiScreen(screen);
      }

      if (!this.riptide$replacingScreen && screen != null) {
         if (PackHideState.isActive() && screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
            this.riptide$setScreen(new RiptidePauseScreen());
            ci.cancel();
         } else if (screen instanceof TitleScreen) {
            if (!PackHideState.isActive() && RiptideWelcomeGate.shouldShow(RiptideConfig.getGlobal())) {
               RiptideWelcomeGate.markShown(RiptideConfig.getGlobal());
               this.riptide$setScreen(new RiptideWelcomeScreen());
               ci.cancel();
            } else if (PackHideState.isActive()) {
               this.riptide$setScreen(new RiptidePanicTitleScreen());
               ci.cancel();
            } else {
               if (!RiptideLiteVariant.enabled() && RiptideMenuPrefs.customMainMenuEnabled()) {
                  this.riptide$setScreen(new RiptideTitleScreen());
                  ci.cancel();
               }
            }
         }
      }
   }

   @Unique
   private void riptide$setScreen(Screen screen) {
      this.riptide$replacingScreen = true;

      try {
         this.minecraft.gui.setScreen(screen);
      } finally {
         this.riptide$replacingScreen = false;
      }
   }
}
