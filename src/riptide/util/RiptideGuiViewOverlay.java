package riptide.util;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScrollbar;
import riptide.util.mm.MmBlobs;
import riptide.util.mm.msg.MmMessages;

public final class RiptideGuiViewOverlay extends RiptideOverlayBase {
   private static final int CELL = 18;
   private static final int PAD = 7;
   private static final int TITLE_H = 12;
   private static final int GRID_TOP_GAP = 6;
   private static final int GRID_INNER_TOP = 4;
   private static final int PANEL_BG = -3750202;
   private static final int SLOT_FILL = -7631989;
   private static final int SLOT_SHADOW = -13158601;
   private static final int SLOT_LIGHT = -1;
   private static final int SLOT_HOVER = -2130706433;
   private final Font font;
   private Component title = Component.literal("GUI");
   private List<MmBlobs.SlotView> slots = List.of();
   private int guiW;
   private int guiH;
   private int scrollY;
   private boolean isDragging;
   private boolean scrollbarDragging;
   private int scrollGrab;
   private double dragOffsetX;
   private double dragOffsetY;

   public RiptideGuiViewOverlay(Font font) {
      super("riptide-gui-view", 180, 180);
      this.font = font;
      this.panelX = 120;
      this.panelY = 50;
   }

   public boolean open(MmMessages.BlobOffer blob) {
      MmBlobs.GuiSnapshot snap = MmBlobs.decodeGui(blob);
      if (snap == null) {
         RiptideNotifications.show("Could not read shared GUI.", -42149);
         return false;
      } else {
         this.title = (Component)(snap.title() == null ? Component.literal("GUI") : snap.title());
         this.slots = snap.slots();
         this.guiW = Math.max(18, snap.width());
         this.guiH = Math.max(18, snap.height());
         this.scrollY = 0;
         RiptideOverlayManager.get().register(this);
         this.setVisible(true);
         int wantW = this.guiW + 2 + 14;
         int wantH = 34 + this.guiH + 2 + 7;
         this.setBounds(new RiptideWindowLayout(this.panelX, this.panelY, wantW, wantH, true, false));
         RiptideOverlayManager.get().bringToFront(this);
         return true;
      }
   }

   @Override
   public int getMinWidth() {
      return 68;
   }

   @Override
   public int getMinHeight() {
      return 59;
   }

   @Override
   public IRiptideOverlay.OverlayScope getDefaultOverlayScope() {
      return IRiptideOverlay.OverlayScope.BACKGROUND_STATUS;
   }

   @Override
   public boolean usesSharedHeaderClickCollapse() {
      return true;
   }

   private int gridLeft() {
      return this.panelX + 7 + 1;
   }

   private int gridTop() {
      return this.panelY + 16 + 12 + 6;
   }

   private int gridAreaH() {
      return Math.max(18, this.panelY + this.panelHeight - 7 - this.gridTop());
   }

   private int maxScroll() {
      return Math.max(0, this.guiH + 2 - this.gridAreaH());
   }

   private CompactScrollbar.Metrics scrollbarMetrics() {
      return CompactScrollbar.compute(this.guiH + 2, this.gridAreaH(), this.panelX + this.panelWidth - 5, this.gridTop(), 3, this.gridAreaH(), this.scrollY);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
      if (this.visible) {
         RiptideWindowLayout bounds = this.clampToScreen(this);
         this.panelX = bounds.x;
         this.panelY = bounds.y;
         this.panelWidth = bounds.width;
         this.panelHeight = bounds.height;
         this.renderWindowFrame(ctx, mx, my, this.getBounds(), "Shared GUI", this.collapsed, this.isDragging);
         if (!this.collapsed) {
            boolean clipped = this.beginWindowBodyClip(ctx, this.getBounds(), this.collapsed);
            ctx.text(this.font, this.title.getVisualOrderText(), this.panelX + 6, this.panelY + 16 + 3, -855310, false);
            this.scrollY = Math.max(0, Math.min(this.scrollY, this.maxScroll()));
            int gl = this.gridLeft();
            int gt = this.gridTop() - this.scrollY;
            UiBounds gridArea = UiBounds.of(this.panelX + 7 - 1, this.gridTop() - 4, this.guiW + 4, this.gridAreaH() + 4);
            UiRenderer.rect(ctx, gridArea, -3750202);
            UiScissorStack.global().push(ctx, gridArea);
            ItemStack hoveredStack = ItemStack.EMPTY;

            for (MmBlobs.SlotView slot : this.slots) {
               int itemX = gl + slot.x();
               int itemY = gt + slot.y();
               if (itemY + 18 >= this.gridTop() && itemY <= this.gridTop() + this.gridAreaH()) {
                  this.drawSlotCell(ctx, itemX - 1, itemY - 1);
                  ItemStack st = slot.item();
                  if (st != null && !st.isEmpty()) {
                     try {
                        ctx.item(st, itemX, itemY);
                        ctx.itemDecorations(this.font, st, itemX, itemY);
                     } catch (Throwable var17) {
                     }
                  }

                  boolean hover = mx >= itemX
                     && mx < itemX + 16
                     && my >= itemY
                     && my < itemY + 16
                     && my >= this.gridTop()
                     && my <= this.gridTop() + this.gridAreaH();
                  if (hover) {
                     UiRenderer.rect(ctx, UiBounds.of(itemX, itemY, 16, 16), -2130706433);
                     if (st != null && !st.isEmpty()) {
                        hoveredStack = st;
                     }
                  }
               }
            }

            UiScissorStack.global().pop(ctx);
            CompactScrollbar.Metrics sb = this.scrollbarMetrics();
            CompactScrollbar.draw(ctx, sb, sb.contains(mx, my), this.scrollbarDragging);
            this.endWindowBodyClip(ctx, clipped);
            if (!hoveredStack.isEmpty()) {
               this.renderItemTooltip(ctx, hoveredStack, mx, my);
            }
         }
      }
   }

   private void renderItemTooltip(GuiGraphicsExtractor ctx, ItemStack stack, int mx, int my) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null) {
            return;
         }

         List<Component> lines = Screen.getTooltipFromItem(mc, stack);
         if (lines == null || lines.isEmpty()) {
            return;
         }

         int w = 0;

         for (Component c : lines) {
            w = Math.max(w, this.font.width(c));
         }

         int h = lines.size() == 1 ? 8 : lines.size() * 10 - 2;
         int sw = RiptideUiScale.getVirtualScreenWidth();
         int sh = RiptideUiScale.getVirtualScreenHeight();
         int x = mx + 12;
         int y = my - 12;
         if (x + w + 4 > sw) {
            x = Math.max(4, mx - w - 16);
         }

         if (y + h + 4 > sh) {
            y = sh - h - 4;
         }

         if (y < 4) {
            y = 4;
         }

         ctx.nextStratum();
         UiRenderer.rect(ctx, UiBounds.of(x - 3, y - 3, w + 6, h + 6), -267386864);
         UiRenderer.frame(ctx, UiBounds.of(x - 3, y - 3, w + 6, h + 6), 0, 1347420320);
         int ty = y;

         for (Component c : lines) {
            ctx.text(this.font, c.getVisualOrderText(), x, ty, -1, true);
            ty += 10;
         }
      } catch (Throwable var16) {
      }
   }

   private void drawSlotCell(GuiGraphicsExtractor ctx, int cx, int cy) {
      UiRenderer.rect(ctx, UiBounds.of(cx, cy, 18, 18), -7631989);
      UiRenderer.rect(ctx, UiBounds.of(cx, cy, 18, 1), -13158601);
      UiRenderer.rect(ctx, UiBounds.of(cx, cy, 1, 18), -13158601);
      UiRenderer.rect(ctx, UiBounds.of(cx, cy + 18 - 1, 18, 1), -1);
      UiRenderer.rect(ctx, UiBounds.of(cx + 18 - 1, cy, 1, 18), -1);
   }

   private ItemStack slotAt(double mx, double my) {
      if (!(my < this.gridTop()) && !(my > this.gridTop() + this.gridAreaH())) {
         int gl = this.gridLeft();
         int gt = this.gridTop() - this.scrollY;

         for (MmBlobs.SlotView slot : this.slots) {
            int itemX = gl + slot.x();
            int itemY = gt + slot.y();
            if (mx >= itemX && mx < itemX + 16 && my >= itemY && my < itemY + 16) {
               return slot.item() == null ? ItemStack.EMPTY : slot.item();
            }
         }

         return ItemStack.EMPTY;
      } else {
         return ItemStack.EMPTY;
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
            } else if (this.collapsed) {
               return false;
            } else {
               ItemStack st = this.slotAt(mx, my);
               if (st != null && !st.isEmpty()) {
                  RiptideItemNbtInspectOverlay ov = RiptideItemNbtInspectOverlay.getSharedOverlay(this.font);
                  if (ov != null) {
                     ov.open(st, (int)mx + 8, (int)my);
                     RiptideOverlayManager.get().register(ov, IRiptideOverlay.OverlayScope.BACKGROUND_STATUS);
                     RiptideOverlayManager.get().bringToFront(ov);
                  }

                  return true;
               } else {
                  return this.isMouseOver(mx, my);
               }
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
         this.scrollY = Math.max(0, Math.min(this.maxScroll(), this.scrollY - (int)Math.signum(amount) * 18));
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
}
