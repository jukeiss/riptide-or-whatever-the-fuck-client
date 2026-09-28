package riptide.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.gui.vanillaui.components.CompactTextInput;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.direct.DirectRenderContext;
import riptide.gui.vanillaui.direct.DirectViewport;

public final class RiptideSharePickerOverlay extends RiptideOverlayBase {
   private static final int ROW = 14;
   private static final int PAD = 8;
   private static final int SEARCH_H = 16;
   private static final int HEADER_COL = -14249;
   private static final int TEXT_COL = -855310;
   private static final int SUB_COL = -6645094;
   private static final int HOVER = 872415231;
   private final Font font;
   private final CompactTheme theme = new CompactTheme();
   private final CompactTextInput search = new CompactTextInput();
   private String title = "Share";
   private Function<String, List<RiptideSharePickerOverlay.Row>> builder = s -> List.of();
   private final List<RiptideSharePickerOverlay.Row> rows = new ArrayList<>();
   private final List<int[]> hits = new ArrayList<>();
   private int contentH;
   private int scrollY;
   private boolean isDragging;
   private boolean scrollbarDragging;
   private int scrollGrab;
   private double dragOffsetX;
   private double dragOffsetY;

   public RiptideSharePickerOverlay(Font font) {
      super("riptide-share-picker", 280, 260);
      this.font = font;
      this.panelX = 140;
      this.panelY = 44;
      this.search.setMaxLength(48).setOnChange(t -> {
         this.rebuild();
         this.scrollY = 0;
      });
   }

   public void open(String title, String placeholder, Function<String, List<RiptideSharePickerOverlay.Row>> builder) {
      this.title = title;
      this.builder = builder;
      this.search.setPlaceholder(placeholder).setText("");
      this.rebuild();
      this.scrollY = 0;
      RiptideOverlayManager.get().register(this);
      this.setVisible(true);
      this.setBounds(new RiptideWindowLayout(this.panelX, this.panelY, Math.max(this.getMinWidth(), 280), 260, true, false));
      RiptideOverlayManager.get().bringToFront(this);
      this.search.setFocused(true);
   }

   private void rebuild() {
      this.rows.clear();
      this.rows.addAll(this.builder.apply(this.search.text()));
      this.contentH = this.rows.size() * 14;
   }

   @Override
   public int getMinWidth() {
      return 220;
   }

   @Override
   public int getMinHeight() {
      return 88;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   @Override
   public boolean hasTextFieldFocused() {
      return this.search.isFocused();
   }

   @Override
   public void clearTextFieldFocus() {
      this.search.setFocused(false);
   }

   private int listTop() {
      return this.panelY + 16 + 3 + 16 + 4;
   }

   private int listAreaH() {
      return Math.max(14, this.panelY + this.panelHeight - 8 - this.listTop());
   }

   private int maxScroll() {
      return Math.max(0, this.contentH - this.listAreaH());
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      return CompactScrollbar.compute(this.contentH, this.listAreaH(), this.panelX + this.panelWidth - 5, this.listTop(), 3, this.listAreaH(), this.scrollY);
   }

   private DirectRenderContext ctx(GuiGraphicsExtractor g, double mx, double my, float delta) {
      return new DirectRenderContext(g, this.font, DirectViewport.current(1.0F), this.theme, (int)Math.round(mx), (int)Math.round(my), delta);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         RiptideWindowLayout bounds = this.clampToScreen(this);
         this.panelX = bounds.x;
         this.panelY = bounds.y;
         this.panelWidth = bounds.width;
         this.panelHeight = bounds.height;
         this.renderWindowFrame(ctx, mx, my, this.getBounds(), this.title, this.collapsed, this.isDragging);
         if (!this.collapsed) {
            boolean clipped = this.beginWindowBodyClip(ctx, this.getBounds(), this.collapsed);
            this.search.setBounds(this.panelX + 8, this.panelY + 16 + 3, this.panelWidth - 16, 16.0F);
            this.search.render(this.ctx(ctx, mx, my, delta));
            this.scrollY = Math.max(0, Math.min(this.scrollY, this.maxScroll()));
            int lt = this.listTop();
            int areaH = this.listAreaH();
            UiScissorStack.global().push(ctx, UiBounds.of(this.panelX + 1, lt, Math.max(0, this.panelWidth - 2), areaH));
            this.hits.clear();
            int y = lt - this.scrollY;

            for (int i = 0; i < this.rows.size(); i++) {
               RiptideSharePickerOverlay.Row r = this.rows.get(i);
               if (y + 14 > lt && y < lt + areaH) {
                  if (r.header()) {
                     ctx.text(this.font, r.text(), this.panelX + 8, y + 3, -14249, false);
                  } else {
                     boolean hover = mx >= this.panelX + 2 && mx < this.panelX + this.panelWidth - 6 && my >= y && my < y + 14 && my >= lt && my < lt + areaH;
                     if (hover) {
                        UiRenderer.rect(ctx, UiBounds.of(this.panelX + 2, y, Math.max(0, this.panelWidth - 8), 14), 872415231);
                     }

                     ctx.text(this.font, r.text(), this.panelX + 8, y + 3, -855310, false);
                     if (r.sub() != null && !r.sub().isEmpty()) {
                        int sw = this.font.width(r.sub());
                        ctx.text(this.font, r.sub(), this.panelX + this.panelWidth - 8 - 6 - sw, y + 3, -6645094, false);
                     }

                     this.hits.add(new int[]{y, i});
                  }
               }

               y += 14;
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
            } else if (this.search.mouseClicked(this.ctx(null, mx, my, 0.0F), (float)mx, (float)my, button)) {
               return true;
            } else {
               if (button == 0) {
                  int lt = this.listTop();
                  int areaH = this.listAreaH();

                  for (int[] h : this.hits) {
                     int ry = h[0];
                     if (my >= Math.max(lt, ry) && my < Math.min(lt + areaH, ry + 14)) {
                        RiptideSharePickerOverlay.Row r = this.rows.get(h[1]);
                        if (r.action() != null) {
                           try {
                              r.action().run();
                           } catch (Throwable var15) {
                           }

                           this.setVisible(false);
                           return true;
                        }
                     }
                  }
               }

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
         return this.search.mouseReleased(this.ctx(null, mx, my, 0.0F), (float)mx, (float)my, button);
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
         return this.search.mouseDragged(this.ctx(null, mx, my, 0.0F), (float)mx, (float)my, button, (float)dx, (float)dy);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double amount) {
      if (this.visible && !this.collapsed && this.isMouseOver(mx, my)) {
         this.scrollY = Math.max(0, Math.min(this.maxScroll(), this.scrollY - (int)Math.signum(amount) * 14 * 2));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.visible) {
         return false;
      } else if (keyCode == 256 && !this.search.isFocused()) {
         this.setVisible(false);
         return true;
      } else {
         return this.search.keyPressed(this.ctx(null, 0.0, 0.0, 0.0F), keyCode, scanCode, modifiers);
      }
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return !this.visible ? false : this.search.charTyped(this.ctx(null, 0.0, 0.0, 0.0F), chr, modifiers);
   }

   public record Row(boolean header, String text, String sub, Runnable action) {
      public static RiptideSharePickerOverlay.Row header(String text) {
         return new RiptideSharePickerOverlay.Row(true, text, null, null);
      }

      public static RiptideSharePickerOverlay.Row item(String text, String sub, Runnable action) {
         return new RiptideSharePickerOverlay.Row(false, text, sub, action);
      }
   }
}
