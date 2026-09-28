package riptide.mixin;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.util.RiptideHudManager;
import riptide.util.RiptideLiteVariant;
import riptide.util.RiptideUiScale;

@Mixin({ContainerEventHandler.class})
public interface RiptideContainerEventHandlerMixin {
   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$musicDisplayClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
      if (!RiptideLiteVariant.enabled()) {
         if (this instanceof Screen screen) {
            if (event.button() == 0) {
               if (RiptideHudManager.musicDisplayMouseClicked(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), screen)) {
                  cir.setReturnValue(true);
               }
            }
         }
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$musicDisplayDrag(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      if (!RiptideLiteVariant.enabled()) {
         if (this instanceof Screen screen) {
            if (RiptideHudManager.musicDisplayMouseDragged(RiptideUiScale.toVirtualInt(event.x()), RiptideUiScale.toVirtualInt(event.y()), screen)) {
               cir.setReturnValue(true);
            }
         }
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$musicDisplayRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (!RiptideLiteVariant.enabled()) {
         if (this instanceof Screen screen) {
            if (RiptideHudManager.musicDisplayMouseReleased(screen)) {
               cir.setReturnValue(true);
            }
         }
      }
   }
}
