package riptide.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import riptide.modules.RiptideModule;
import riptide.util.RiptideOverlayManager;

@Mixin({CreativeModeInventoryScreen.class})
public abstract class RiptideCreativeScreenMixin {
   @Unique
   private static final ThreadLocal<Boolean> riptide$inSafeRecall = ThreadLocal.withInitial(() -> Boolean.FALSE);

   @Shadow
   protected abstract void extractTabButton(GuiGraphicsExtractor var1, int var2, int var3, CreativeModeTab var4);

   @Inject(
      method = {"checkTabHovering"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$blockCoveredTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"extractTabButton"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$blockCoveredTabCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
      if (!riptide$inSafeRecall.get()) {
         RiptideModule module = RiptideModule.get();
         if (module != null && module.isActive()) {
            if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
               riptide$inSafeRecall.set(Boolean.TRUE);

               try {
                  this.extractTabButton(graphics, -10000, -10000, tab);
               } finally {
                  riptide$inSafeRecall.set(Boolean.FALSE);
               }

               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"mouseReleased"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$mouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"charTyped"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void riptide$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
      RiptideModule module = RiptideModule.get();
      if (module != null && module.isActive()) {
         if (RiptideOverlayManager.get().handleCharTyped((char)input.codepoint(), 0)) {
            cir.setReturnValue(true);
         }
      }
   }
}
