package riptide.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.util.RiptideOverlayManager;

@Mixin({AbstractRecipeBookScreen.class})
public abstract class RiptideRecipeBookScreenMixin {
   @Unique
   private boolean riptide$isActive() {
      RiptideModule module = RiptideModule.get();
      return module != null && module.isActive();
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$handleOverlayClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (this.riptide$isActive()) {
         RiptideOverlayManager manager = RiptideOverlayManager.get();
         if (manager.handleMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$handleOverlayDrag(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      if (this.riptide$isActive()) {
         if (RiptideOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$handleOverlayKeys(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (this.riptide$isActive()) {
         if (RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"charTyped"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void riptide$handleOverlayChars(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (this.riptide$isActive()) {
         if (RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0)) {
            cir.setReturnValue(true);
         }
      }
   }
}
