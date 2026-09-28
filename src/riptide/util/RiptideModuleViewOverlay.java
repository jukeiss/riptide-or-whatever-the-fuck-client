package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.util.mm.MmBlobs;
import riptide.util.mm.msg.MmMessages;

public final class RiptideModuleViewOverlay extends RiptideOverlayBase {
   private static final int TITLE_H = 12;
   private static final int ROW = 11;
   private static final int PAD = 8;
   private static final int MAX_LIST_H = 220;
   private static final int HEADER_COL = -14249;
   private static final int VALUE_COL = -7429889;
   private static final int MUTED = -6645094;
   private final Font font;
   private String title = "Module Settings";
   private final List<RiptideModuleViewOverlay.Line> lines = new ArrayList<>();
   private int contentH;
   private int scrollY;
   private boolean isDragging;
   private boolean scrollbarDragging;
   private int scrollGrab;
   private double dragOffsetX;
   private double dragOffsetY;

   public RiptideModuleViewOverlay(Font font) {
      super("riptide-module-view", 220, 200);
      this.font = font;
      this.panelX = 130;
      this.panelY = 48;
   }

   public boolean open(MmMessages.BlobOffer blob) {
      MmBlobs.ModuleView mv = MmBlobs.decodeModule(blob);
      if (mv == null) {
         RiptideNotifications.show("Could not read shared module settings.", -42149);
         return false;
      } else {
         this.lines.clear();
         this.lines.add(new RiptideModuleViewOverlay.Line("Module: " + mv.name(), -14249));
         this.lines.add(new RiptideModuleViewOverlay.Line("", -6645094));
         this.lines.add(new RiptideModuleViewOverlay.Line("Settings  (" + mv.settings().size() + ")", -14249));
         if (mv.settings().isEmpty()) {
            this.lines.add(new RiptideModuleViewOverlay.Line("   (none)", -6645094));
         } else {
            for (String[] kv : mv.settings()) {
               this.lines.add(new RiptideModuleViewOverlay.Line("   " + kv[0] + " = " + kv[1], -7429889));
            }
         }

         this.title = "Module Settings  (" + mv.name() + ")";
         this.contentH = this.lines.size() * 11;
         this.scrollY = 0;
         RiptideOverlayManager.get().register(this);
         this.setVisible(true);
         int wantW = Math.max(this.getMinWidth(), this.maxLineWidth() + 16 + 6);
         int wantH = 32 + Math.min(this.contentH, 220) + 8;
         this.setBounds(new RiptideWindowLayout(this.panelX, this.panelY, wantW, wantH, true, false));
         RiptideOverlayManager.get().bringToFront(this);
         return true;
      }
   }

   private int maxLineWidth() {
      int w = this.font.width(this.title);

      for (RiptideModuleViewOverlay.Line l : this.lines) {
         w = Math.max(w, this.font.width(l.text()));
      }

      return w;
   }

   @Override
   public int getMinWidth() {
      return 170;
   }

   @Override
   public int getMinHeight() {
      return 73;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   private int listTop() {
      return this.panelY + 16 + 12 + 4;
   }

   private int listAreaH() {
      return Math.max(11, this.panelY + this.panelHeight - 8 - this.listTop());
   }

   private int maxScroll() {
      return Math.max(0, this.contentH - this.listAreaH());
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      return CompactScrollbar.compute(this.contentH, this.listAreaH(), this.panelX + this.panelWidth - 5, this.listTop(), 3, this.listAreaH(), this.scrollY);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         RiptideWindowLayout bounds = this.clampToScreen(this);
         this.panelX = bounds.x;
         this.panelY = bounds.y;
         this.panelWidth = bounds.width;
         this.panelHeight = bounds.height;
         this.renderWindowFrame(ctx, mx, my, this.getBounds(), "Shared Module", this.collapsed, this.isDragging);
         if (!this.collapsed) {
            boolean clipped = this.beginWindowBodyClip(ctx, this.getBounds(), this.collapsed);
            ctx.text(this.font, this.title, this.panelX + 8, this.panelY + 16 + 3, -855310, false);
            this.scrollY = Math.max(0, Math.min(this.scrollY, this.maxScroll()));
            int lt = this.listTop();
            UiScissorStack.global().push(ctx, UiBounds.of(this.panelX + 1, lt, Math.max(0, this.panelWidth - 2), this.listAreaH()));
            int y = lt - this.scrollY;

            for (RiptideModuleViewOverlay.Line l : this.lines) {
               if (y + 11 > lt && y < lt + this.listAreaH() && !l.text().isEmpty()) {
                  ctx.text(this.font, l.text(), this.panelX + 8, y, l.color(), false);
               }

               y += 11;
            }

            UiScissorStack.global().pop(ctx);
            CompactScrollbar.Metrics sb = this.scrollbarMetrics();
            CompactScrollbar.draw(ctx, sb, sb.contains(mx, my), this.scrollbarDragging);
            this.endWindowBodyClip(ctx, clipped);
         }
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.visible) {
         return false;
      } else {
         RiptideWindowLayout bounds = this.getBounds();
         if (this.isOverCloseButton(mx, my, bounds)) {
            this.setVisible(false);
            this.isDragging = false;
            return true;
         } else {
            CompactScrollbar.Metrics sb = this.scrollbarMetrics();
            if (button == 0 && sb.overThumb(mx, my)) {
               this.scrollbarDragging = true;
               this.scrollGrab = (int)Math.round(my) - sb.thumbY();
               return true;
            } else if (button == 0 && this.isOverDragBar(mx, my)) {
               this.isDragging = true;
               this.dragOffsetX = mx - this.panelX;
               this.dragOffsetY = my - this.panelY;
               return true;
            } else {
               return this.isMouseOver(mx, my);
            }
         }
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      if (this.scrollbarDragging) {
         this.scrollbarDragging = false;
         return true;
      } else if (this.isDragging) {
         this.isDragging = false;
         this.saveLayout();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.scrollbarDragging) {
         this.scrollY = CompactScrollbar.scrollFromThumb(this.scrollbarMetrics(), my, this.scrollGrab);
         return true;
      } else if (this.isDragging) {
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
         return false;
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amount) {
      if (this.visible && !this.collapsed && this.isMouseOver(mx, my)) {
         this.scrollY = Math.max(0, Math.min(this.maxScroll(), this.scrollY - (int)Math.signum(amount) * 11 * 2));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return false;
   }

   private record Line(String text, int color) {
   }
}
