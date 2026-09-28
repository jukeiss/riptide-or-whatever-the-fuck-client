package riptide.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.gui.screen.RiptideModuleScreen;
import riptide.gui.screen.RiptideOverlayHostScreen;
import riptide.modules.PackHideState;
import riptide.modules.RiptideModule;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptideMatchmakingOverlay;
import riptide.util.RiptideOverlayManager;
import riptide.util.RiptideProfilesOverlay;

@Mixin({TitleScreen.class})
public abstract class RiptideTitleScreenSupportMixin extends Screen {
   protected RiptideTitleScreenSupportMixin() {
      super(null);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void riptide$addSupportButtons(CallbackInfo var1) {
      if (!PackHideState.isActive()) {
         this.riptide$rightButton(Component.literal("Modules & Macros"), 4, var1x -> {
            if (!PackHideState.isHardLocked()) {
               this.minecraft.gui.setScreen(new RiptideModuleScreen(this, RiptideModuleScreen.Mode.TITLE_SETUP));
            }
         });
         this.riptide$rightButton(Component.literal("Profiles"), 28, var1x -> this.riptide$openMenuOverlay(false));
         this.riptide$leftButton(Component.literal("RIPTIDE"), 4, "");
         this.riptide$leftButton(Component.literal("Riptide Client"), 28, "");
      }
   }

   @Unique
   private void riptide$rightButton(Component var1, int var2, OnPress var3) {
      int var4 = this.riptide$buttonWidth(var1);
      this.addRenderableWidget(Button.builder(var1, var3).bounds(this.width - var4 - 4, var2, var4, 20).build());
   }

   @Unique
   private void riptide$leftButton(Component var1, int var2, String var3) {
   }

   @Unique
   private int riptide$buttonWidth(Component var1) {
      return Math.max(96, Math.min(this.width - 8, this.font.width(var1) + 20));
   }

   @Unique
   private void riptide$openMenuOverlay(boolean var1) {
      if (!PackHideState.isHardLocked()) {
         RiptideModule var2 = RiptideModule.get();
         if (var2 != null) {
            IRiptideOverlay var3 = var1 ? var2.getMatchmakingOverlay() : var2.getProfilesOverlay();
            if (var3 != null) {
               RiptideOverlayManager.get().register(var3);
               if (var3 instanceof RiptideMatchmakingOverlay var4) {
                  var4.setMainMenuMode(true);
               } else if (var3 instanceof RiptideProfilesOverlay var5) {
                  var5.setMainMenuMode(true);
               }

               var3.setVisible(true);
               this.minecraft.gui.setScreen(new RiptideOverlayHostScreen(var3, this, true));
            }
         }
      }
   }
}
