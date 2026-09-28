package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiComponent;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiInputResult;
import riptide.util.IRiptideOverlay;
import riptide.util.RiptidePerf;
import riptide.util.RiptideWindow;
import riptide.util.RiptideWindowLayout;

public final class OperationalOverlayComponent implements UiComponent {
   private final IRiptideOverlay overlay;
   private boolean renderSuppressed;
   private boolean hoverBlocked;
   private boolean inputSuppressed;

   public OperationalOverlayComponent(IRiptideOverlay overlay) {
      this.overlay = overlay;
   }

   public void setRenderSuppressed(boolean renderSuppressed) {
      this.renderSuppressed = renderSuppressed;
   }

   public void setHoverBlocked(boolean hoverBlocked) {
      this.hoverBlocked = hoverBlocked;
   }

   public void setInputSuppressed(boolean inputSuppressed) {
      this.inputSuppressed = inputSuppressed;
   }

   @Override
   public UiBounds bounds() {
      RiptideWindowLayout bounds = this.overlay.getBounds();
      return UiBounds.of(bounds.x, bounds.y, bounds.width, bounds.collapsed ? RiptideWindow.sharedHeaderHeight() : bounds.height);
   }

   @Override
   public void setBounds(UiBounds bounds) {
      if (bounds != null) {
         RiptideWindowLayout current = this.overlay.getBounds();
         this.overlay.setBounds(new RiptideWindowLayout(bounds.x(), bounds.y(), bounds.width(), bounds.height(), current.visible, current.collapsed));
      }
   }

   @Override
   public UiBounds hitBounds() {
      return !this.inputSuppressed && this.overlay.isVisible() ? this.bounds() : null;
   }

   @Override
   public void render(UiContext context) {
      if (!this.renderSuppressed && this.overlay.isVisible()) {
         long perfStart = RiptidePerf.beginSampled();
         this.overlay.render(context.graphics(), this.hoverBlocked ? -10000 : context.mouseX(), this.hoverBlocked ? -10000 : context.mouseY(), context.delta());
         if (perfStart != 0L) {
            RiptidePerf.end("overlay." + this.overlay.getOverlayId(), perfStart);
         }
      }
   }

   @Override
   public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
      this.overlay.mouseClicked(mouseX, mouseY, button);
      return UiInputResult.HANDLED;
   }

   @Override
   public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
      return handled(this.overlay.mouseReleased(mouseX, mouseY, button));
   }

   @Override
   public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
      return handled(this.overlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
   }

   @Override
   public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
      this.overlay.mouseScrolled(mouseX, mouseY, amount);
      return UiInputResult.HANDLED;
   }

   @Override
   public UiInputResult keyPressed(int key, int scanCode, int modifiers) {
      return handled(this.overlay.keyPressed(key, scanCode, modifiers));
   }

   @Override
   public UiInputResult charTyped(char chr) {
      return this.charTyped(chr, 0);
   }

   public UiInputResult charTyped(char chr, int modifiers) {
      return handled(this.overlay.charTyped(chr, modifiers));
   }

   private static UiInputResult handled(boolean handled) {
      return handled ? UiInputResult.HANDLED : UiInputResult.IGNORED;
   }
}
