package riptide.mixin;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.util.RiptideSpotifyBar;
import riptide.util.RiptideUiScale;

@Mixin({ContainerEventHandler.class})
public interface RiptideSpotifyBarClickMixin {
   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$spotifyBarClick(MouseButtonEvent var1, boolean var2, CallbackInfoReturnable<Boolean> var3) {
      if (this instanceof Screen var4
         && RiptideSpotifyBar.mouseClicked(RiptideUiScale.toVirtualInt(var1.x()), RiptideUiScale.toVirtualInt(var1.y()), var1.button(), var4)) {
         var3.setReturnValue(true);
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$spotifyBarDrag(MouseButtonEvent var1, double var2, double var4, CallbackInfoReturnable<Boolean> var6) {
      if (this instanceof Screen var7 && RiptideSpotifyBar.mouseDragged(RiptideUiScale.toVirtualInt(var1.x()), RiptideUiScale.toVirtualInt(var1.y()), var7)) {
         var6.setReturnValue(true);
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$spotifyBarRelease(MouseButtonEvent var1, CallbackInfoReturnable<Boolean> var2) {
      if (this instanceof Screen var3 && RiptideSpotifyBar.mouseReleased(var3)) {
         var2.setReturnValue(true);
      }
   }
}
