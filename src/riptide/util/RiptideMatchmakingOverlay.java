package riptide.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import riptide.gui.mm.MatchmakingPanel;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.Card;

public final class RiptideMatchmakingOverlay extends RiptideOverlayBase implements MatchmakingPanel.Host {
   private static final String OVERLAY_ID = "riptide-matchmaking";
   private final MatchmakingPanel panel;
   private final Font font;
   private boolean isDragging;
   private double dragOffsetX;
   private double dragOffsetY;
   private boolean mainMenuMode;
   private int hudX;
   private int hudY;
   private int hudW;
   private int hudH;
   private static final int MAX_H = 360;

   public RiptideMatchmakingOverlay(Font font) {
      super("riptide-matchmaking", 400, 230);
      this.font = font;
      this.panel = new MatchmakingPanel(this, font);
      this.panelX = 80;
      this.panelY = 36;
   }

   public void toggle() {
      this.setMainMenuMode(false);
      this.setVisible(!this.visible);
      if (this.visible) {
         RiptideOverlayManager.get().bringToFront(this);
      }
   }

   public void setMainMenuMode(boolean v) {
      if (v != this.mainMenuMode) {
         if (v) {
            this.hudX = this.panelX;
            this.hudY = this.panelY;
            this.hudW = this.panelWidth;
            this.hudH = this.panelHeight;
            this.collapsed = false;
         } else {
            this.panelX = this.hudX;
            this.panelY = this.hudY;
            this.panelWidth = this.hudW;
            this.panelHeight = this.hudH;
         }

         this.mainMenuMode = v;
      }
   }

   @Override
   public void setVisible(boolean v) {
      if (this.visible == v) {
         if (v) {
            this.panel.openDirectory();
         }
      } else {
         if (!v) {
            this.panel.closeDirectory();
         }

         if (!v && this.mainMenuMode) {
            this.panelX = this.hudX;
            this.panelY = this.hudY;
            this.panelWidth = this.hudW;
            this.panelHeight = this.hudH;
            this.mainMenuMode = false;
         }

         super.setVisible(v);
         if (v) {
            this.panel.openDirectory();
         }
      }
   }

   public void openInGameInteractive() {
      this.setMainMenuMode(false);
      this.setVisible(true);
   }

   @Override
   public int getMinWidth() {
      return 360;
   }

   @Override
   public int getMinHeight() {
      return 150;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return !this.mainMenuMode;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.panel.hasFocusedTextInput();
   }

   @Override
   public void clearTextFieldFocus() {
      this.panel.clearFocus();
   }

   @Override
   public Screen returnScreen() {
      return null;
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         if (this.mainMenuMode) {
            this.renderMainMenu(ctx, mx, my, delta);
         } else {
            RiptideWindowLayout bounds = this.clampToScreen(this);
            this.panelX = bounds.x;
            this.panelY = bounds.y;
            this.panelWidth = bounds.width;
            this.panelHeight = bounds.height;
            this.renderWindowFrame(ctx, mx, my, this.getBounds(), "Matchmaking", this.collapsed, this.isDragging);
            if (!this.collapsed) {
               boolean clipped = this.beginWindowBodyClip(ctx, this.getBounds(), this.collapsed);
               this.panel.render(ctx, this.panelX + 3, this.panelY + 16 + 1, this.panelWidth - 6, this.panelHeight - 16 - 4, mx, my, delta);
               this.endWindowBodyClip(ctx, clipped);
               if (!this.collapsed) {
                  int want = this.panel.desiredHeight() + 16 + 5;
                  int maxH = Math.min(RiptideUiScale.getVirtualScreenHeight() - 16, 360);
                  this.panelHeight = Math.max(this.getMinHeight(), Math.min(want, maxH));
               }
            }
         }
      }
   }

   private void renderMainMenu(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      int sw = RiptideUiScale.getVirtualScreenWidth();
      int sh = RiptideUiScale.getVirtualScreenHeight();
      UiRenderer.rect(ctx, UiBounds.of(0, 0, sw, sh), -1073741824);
      int pw = Math.min(sw - 40, 520);
      int px = (sw - pw) / 2;
      int py = 26;
      int ph = Math.max(this.getMinHeight(), sh - py - 44);
      this.panelX = px;
      this.panelY = py;
      this.panelWidth = pw;
      this.panelHeight = ph;
      String title = "Matchmaking";
      ctx.text(this.font, title, (sw - this.font.width(title)) / 2, 9, -1, true);
      Card.render(UiContexts.overlay(ctx, this.font, mx, my), UiBounds.of(px - 2, py - 2, pw + 4, ph + 4));
      UiScissorStack.global().push(ctx, UiBounds.of(px, py, pw, ph));
      this.panel.render(ctx, px, py, pw, ph, mx, my, delta);
      UiScissorStack.global().pop(ctx);
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (!this.mainMenuMode && this.isOverCloseButton(mx, my, bounds)) {
            this.setVisible(false);
            this.isDragging = false;
            return true;
         } else if (!this.mainMenuMode && button == 0 && this.isOverDragBar(mx, my)) {
            this.isDragging = true;
            this.dragOffsetX = mx - this.panelX;
            this.dragOffsetY = my - this.panelY;
            return true;
         } else if (this.collapsed) {
            return false;
         } else {
            return this.panel.mouseClicked((int)Math.round(mx), (int)Math.round(my), button) ? true : this.isMouseOver(mx, my);
         }
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      if (this.isDragging) {
         this.isDragging = false;
         this.saveLayout();
         return true;
      } else {
         return this.panel.mouseReleased((int)Math.round(mx), (int)Math.round(my), button);
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.isDragging) {
         RiptideWindowLayout c = this.clampToScreen(
            this,
            new RiptideWindowLayout(
               (int)Math.round(mx - this.dragOffsetX), (int)Math.round(my - this.dragOffsetY), this.panelWidth, this.panelHeight, this.visible, this.collapsed
            )
         );
         this.panelX = c.x;
         this.panelY = c.y;
         return true;
      } else {
         return this.collapsed ? false : this.panel.mouseDragged((int)Math.round(mx), (int)Math.round(my), button, dx, dy);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amount) {
      return this.visible && !this.collapsed && this.isMouseOver(mx, my) ? this.panel.mouseScrolled((int)Math.round(mx), (int)Math.round(my), amount) : false;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.visible && !this.collapsed && this.panel.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.visible && !this.collapsed && this.panel.charTyped(chr, modifiers);
   }
}
