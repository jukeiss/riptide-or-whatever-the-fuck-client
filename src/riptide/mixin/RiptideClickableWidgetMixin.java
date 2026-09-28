package riptide.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import riptide.util.RiptideOverlayManager;

@Mixin({AbstractWidget.class})
public abstract class RiptideClickableWidgetMixin {
   @Shadow
   protected boolean isHovered;

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/gui/components/AbstractWidget;isHovered:Z",
         shift = Shift.AFTER
      )}
   )
   private void riptide$suppressHoverWhenOverlayBlocks(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (RiptideOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
         this.isHovered = false;
      }
   }
}
