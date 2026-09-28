package riptide.gui.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScreenPanel;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiSizing;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;
import riptide.gui.vanillaui.direct.DirectLayout;
import riptide.util.RiptideUiScale;

public class RiptideForceOpPreviewScreen extends RiptideScreen {
   private static final CompactTheme THEME = new CompactTheme();
   private static final int BG = -1442840576;
   private static final int PANEL = THEME.windowFill();
   private static final int BORDER = THEME.borderSoft();
   private static final int RED = THEME.headerAccent();
   private static final int TEXT = THEME.color(UiTone.BODY);
   private static final int MUTED = THEME.color(UiTone.MUTED);
   private static final int ROW_H = 18;
   private static final int HEADER_H = 26;
   private static final int PANEL_W = 340;
   private static final int PANEL_H = 260;
   private final Screen parent;
   private final String[] passwords;
   private final List<String> visiblePasswords;
   private int scroll = 0;
   private boolean draggingScroll = false;
   private double scrollGrabOffset = 0.0;
   private final List<RiptideForceOpPreviewScreen.Hit> hits = new ArrayList<>();

   public RiptideForceOpPreviewScreen(Screen parent, String[] passwords) {
      super(Component.literal("ForceOp Preview"));
      this.parent = parent;
      this.passwords = passwords;
      this.visiblePasswords = new ArrayList<>();
      int limit = Math.min(passwords.length, 500);

      for (int i = 0; i < limit; i++) {
         this.visiblePasswords.add(passwords[i]);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private int panelW() {
      return DirectLayout.fitPanelDimension(this.screenWidth(), 4, 340);
   }

   private int panelH() {
      return DirectLayout.fitPanelDimension(this.screenHeight(), 4, this.visiblePasswords.size() * 18 + 26 + 40);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      RiptideUiScale.pushOverlayScale(graphics);

      try {
         this.hits.clear();
         UiRenderer.rect(graphics, UiBounds.of(0, 0, this.screenWidth(), this.screenHeight()), -1442840576);
         int x = this.panelX();
         int y = this.panelY();
         int w = this.panelW();
         int h = this.panelH();
         this.drawTopBar(graphics, x, y, w, h, "Password Preview (First " + this.visiblePasswords.size() + ")", mx, my);
         if (w >= 100 && h >= 54) {
            int listX = x + 10;
            int listY = y + 26 + 10;
            int listW = w - 20;
            int listH = h - 26 - 20;
            this.frame(graphics, listX, listY, listW, listH, -1442248437, BORDER);
            int innerX = listX + 2;
            int innerY = listY + 2;
            int innerW = listW - 4;
            int innerH = listH - 4;
            int maxScroll = Math.max(0, this.visiblePasswords.size() * 18 - innerH);
            this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
            UiScissorStack.global().push(graphics, UiBounds.of(innerX, innerY, Math.max(0, innerW), Math.max(0, innerH)));

            try {
               int contentY = innerY - this.scroll;

               for (int i = 0; i < this.visiblePasswords.size(); i++) {
                  int rowY = contentY + i * 18;
                  if (rowY + 18 > innerY && rowY < innerY + innerH) {
                     int color = i % 2 == 0 ? -10046721 : -24474;
                     CompactSurfaces.row(graphics, innerX, rowY, innerW - 8, 18, false, false);
                     String text = UiText.trimToWidth(this.font, this.visiblePasswords.get(i), innerW - 14, THEME.fontFor(UiTone.BODY), color);
                     this.drawText(
                        graphics, text, innerX + 4, UiSizing.alignTextY(rowY, 18, THEME.fontHeight(UiTone.BODY), THEME.bodyTextNudge()), color, innerW - 14
                     );
                  }
               }
            } finally {
               UiScissorStack.global().pop(graphics);
            }

            if (maxScroll > 0) {
               CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
                  this.visiblePasswords.size() * 18, innerH, innerX + innerW - 6, innerY, 6, innerH, this.scroll
               );
               boolean overBar = metrics.contains(mx, my);
               CompactScrollbar.draw(graphics, metrics, overBar, this.draggingScroll);
               this.hits.add(new RiptideForceOpPreviewScreen.Hit(RiptideForceOpPreviewScreen.HitType.SCROLLBAR, innerX + innerW - 6, innerY, 6, innerH));
            }

            return;
         }
      } finally {
         RiptideUiScale.popOverlayScale(graphics);
      }
   }

   private void drawTopBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String title, int mx, int my) {
      UiBounds bounds = UiBounds.of(x, y, width, height);
      CompactScreenPanel.render(UiContexts.overlay(graphics, this.font, mx, my), bounds, 26, title, mx >= x && mx < x + width && my >= y && my < y + 26);
      UiBounds close = CompactScreenPanel.closeButton(bounds, 26);
      this.hits.add(new RiptideForceOpPreviewScreen.Hit(RiptideForceOpPreviewScreen.HitType.CLOSE, close.x(), close.y(), close.width(), close.height()));
   }

   private void drawCentered(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, int color) {
      String display = UiText.trimToWidth(this.font, text, Math.max(0, w - 8), THEME.fontFor(UiTone.LABEL), color);
      int tw = UiText.width(this.font, display, THEME.fontFor(UiTone.LABEL), color);
      int th = THEME.fontHeight(UiTone.LABEL);
      UiText.draw(graphics, this.font, display, THEME.fontFor(UiTone.LABEL), color, x + Math.max(2, (w - tw) / 2), y + Math.max(1, (h - th + 1) / 2 + 1), false);
   }

   private void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
      UiRenderer.frame(graphics, UiBounds.of(x, y, w, h), fill, border);
   }

   private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
      UiText.draw(graphics, this.font, text, THEME.fontFor(UiTone.BODY), color, x, y, false);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean allowBypass) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());

      for (RiptideForceOpPreviewScreen.Hit hit : this.hits) {
         if (hit.contains(mx, my)) {
            if (hit.type == RiptideForceOpPreviewScreen.HitType.CLOSE && event.button() == 0) {
               this.onClose();
               return true;
            }

            if (hit.type == RiptideForceOpPreviewScreen.HitType.SCROLLBAR && event.button() == 0) {
               int innerH = this.panelH() - 26 - 24;
               CompactScrollbar.Metrics metrics = CompactScrollbar.compute(this.visiblePasswords.size() * 18, innerH, hit.x, hit.y, hit.w, hit.h, this.scroll);
               if (metrics.overThumb(mx, my)) {
                  this.draggingScroll = true;
                  this.scrollGrabOffset = my - metrics.thumbY();
               } else {
                  this.scroll = CompactScrollbar.scrollFromThumb(metrics, my, metrics.thumbHeight() / 2);
               }

               return true;
            }
         }
      }

      if (mx >= this.panelX() && mx <= this.panelX() + this.panelW() && my >= this.panelY() && my <= this.panelY() + this.panelH()) {
         return super.mouseClicked(event, allowBypass);
      } else {
         this.onClose();
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.draggingScroll = false;
      return super.mouseReleased(event);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      int mx = RiptideUiScale.toVirtualInt(event.x());
      int my = RiptideUiScale.toVirtualInt(event.y());
      if (this.draggingScroll) {
         int innerH = this.panelH() - 26 - 24;
         int x = this.panelX() + 10;
         int y = this.panelY() + 26 + 10;
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            this.visiblePasswords.size() * 18, innerH, x + this.panelW() - 26, y + 2, 6, innerH, this.scroll
         );
         this.scroll = CompactScrollbar.scrollFromThumb(metrics, my, (int)this.scrollGrabOffset);
         return true;
      } else {
         return super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int mx = RiptideUiScale.toVirtualInt(mouseX);
      int my = RiptideUiScale.toVirtualInt(mouseY);
      int listX = this.panelX() + 10;
      int listY = this.panelY() + 26 + 10;
      int listW = this.panelW() - 20;
      int listH = this.panelH() - 26 - 20;
      if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
         this.scroll = (int)(this.scroll - scrollY * 18.0 * 2.0);
         int maxScroll = Math.max(0, this.visiblePasswords.size() * 18 - (listH - 4));
         this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (event.key() == 256) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   private int panelX() {
      return DirectLayout.centerPanel(this.screenWidth(), this.panelW(), 4);
   }

   private int panelY() {
      return DirectLayout.centerPanel(this.screenHeight(), this.panelH(), 4);
   }

   private record Hit(RiptideForceOpPreviewScreen.HitType type, int x, int y, int w, int h) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum HitType {
      CLOSE,
      SCROLLBAR;
   }
}
