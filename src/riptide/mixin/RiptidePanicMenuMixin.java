package riptide.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.modules.PackHideState;

@Mixin({Screen.class})
public abstract class RiptidePanicMenuMixin {
   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")}
   )
   private void riptide$hideForeignWidgetsWhileHidden(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (PackHideState.isActive()) {
         Screen self = (Screen)this;
         String screenClass = self.getClass().getName();
         boolean vanillaScreen = screenClass.startsWith("net.minecraft.") || screenClass.startsWith("com.mojang.");
         if (vanillaScreen) {
            for (GuiEventListener child : self.children()) {
               if (child instanceof AbstractWidget widget) {
                  String className = widget.getClass().getName();
                  if (!className.startsWith("net.minecraft.") && !className.startsWith("com.mojang.")) {
                     widget.visible = false;
                     widget.active = false;
                  }
               }
            }
         }
      }
   }
}
