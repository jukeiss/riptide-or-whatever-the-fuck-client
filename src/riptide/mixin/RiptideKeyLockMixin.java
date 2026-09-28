package riptide.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.screen.RiptidePanicTitleScreen;
import riptide.gui.screen.RiptideTitleScreen;
import riptide.gui.screen.RiptideWelcomeScreen;
import riptide.util.RiptideKeyLock;
import riptide.util.RiptideKeyScreen;

@Mixin(
   value = {Gui.class},
   priority = 100
)
public abstract class RiptideKeyLockMixin {
   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$gateMenu(Screen var1, CallbackInfo var2) {
      boolean var3 = var1 instanceof TitleScreen
         || var1 instanceof RiptideTitleScreen
         || var1 instanceof RiptideWelcomeScreen
         || var1 instanceof RiptidePanicTitleScreen;
      if (var3 && !RiptideKeyLock.isPassed() && !RiptideKeyLock.tryAutoUnlock()) {
         System.out.println("[Riptide] locked - showing key screen");
         var2.cancel();
         ((Gui)this).setScreen(new RiptideKeyScreen());
      }
   }
}
