package riptide.util;

import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.gui.vanillaui.components.CompactTheme;
import riptide.gui.vanillaui.components.UiText;
import riptide.gui.vanillaui.components.UiTone;

public class RiptideContextMenu<T> {
   private static final int PADDING_X = 4;
   private static final int PADDING_Y = 2;
   private static final int BORDER = 1;
   private final CompactTheme theme;
   private final Font font;
   private final Function<T, String[]> itemProvider;
   private final int lineHeight;
   private boolean open;
   private int x;
   private int y;
   private T target;
   private String[] cachedItems = new String[0];
   private int cachedWidth = -1;
   private int cachedHeight = -1;
   private int cachedScreenWidth = -1;
   private int cachedScreenHeight = -1;

   public RiptideContextMenu(CompactTheme theme, Font font, Function<T, String[]> itemProvider, int lineHeight) {
      this.theme = theme;
      this.font = font;
      this.itemProvider = itemProvider;
      this.lineHeight = lineHeight;
   }

   public boolean isOpen() {
      return this.open && this.target != null;
   }

   public T target() {
      return this.target;
   }

   public void open(int mouseX, int mouseY, T target) {
      this.target = target;
      this.x = mouseX;
      this.y = mouseY;
      this.open = true;
      String[] items = this.itemProvider.apply(target);
      this.cachedItems = items == null ? new String[0] : items;
      this.invalidateMetrics();
      this.clampToScreen();
   }

   public void close() {
      this.open = false;
      this.target = null;
      this.cachedItems = new String[0];
      this.invalidateMetrics();
   }

   private String[] currentItems() {
      return this.open && this.target != null ? this.cachedItems : new String[0];
   }

   private int width(String[] items) {
      this.ensureMetrics(items);
      return this.cachedWidth;
   }

   private int height(String[] items) {
      this.ensureMetrics(items);
      return this.cachedHeight;
   }

   private void ensureMetrics(String[] items) {
      int screenWidth = Math.max(1, RiptideUiScale.getVirtualScreenWidth());
      int screenHeight = Math.max(1, RiptideUiScale.getVirtualScreenHeight());
      if (this.cachedWidth < 0 || this.cachedHeight < 0 || this.cachedScreenWidth != screenWidth || this.cachedScreenHeight != screenHeight) {
         int maxW = 0;

         for (String s : items) {
            maxW = Math.max(maxW, UiText.width(this.font, s, this.theme.fontFor(UiTone.BODY), -1));
         }

         this.cachedWidth = Math.min(Math.max(1, screenWidth - 4), maxW + 8);
         this.cachedHeight = this.visibleItemCount(items) * this.lineHeight + 4;
         this.cachedScreenWidth = screenWidth;
         this.cachedScreenHeight = screenHeight;
      }
   }

   private void invalidateMetrics() {
      this.cachedWidth = -1;
      this.cachedHeight = -1;
      this.cachedScreenWidth = -1;
      this.cachedScreenHeight = -1;
   }

   private int visibleItemCount(String[] items) {
      int available = Math.max(1, RiptideUiScale.getVirtualScreenHeight() - 4 - 4);
      return Math.max(0, Math.min(items.length, available / Math.max(1, this.lineHeight)));
   }

   private void clampToScreen() {
      if (this.open) {
         String[] items = this.currentItems();
         int w = this.width(items);
         int h = this.height(items);
         int sw = Math.max(1, RiptideUiScale.getVirtualScreenWidth());
         int sh = Math.max(1, RiptideUiScale.getVirtualScreenHeight());
         this.x = Math.max(2, Math.min(this.x, sw - w - 2));
         this.y = Math.max(2, Math.min(this.y, sh - h - 2));
      }
   }

   public boolean isMouseOver(double mx, double my) {
      if (!this.isOpen()) {
         return false;
      } else {
         String[] items = this.currentItems();
         int w = this.width(items);
         int h = this.height(items);
         return mx >= this.x && mx < this.x + w && my >= this.y && my < this.y + h;
      }
   }

   public void render(GuiGraphicsExtractor ctx, int mx, int my) {
      if (this.isOpen()) {
         this.clampToScreen();
         String[] items = this.currentItems();
         int w = this.width(items);
         int h = this.height(items);
         UiRenderer.popup(ctx, UiBounds.of(this.x - 1, this.y - 1, w + 2, h + 2), this.theme.windowFill(), this.theme.borderColor(), this.theme.headerAccent());
         int visibleItems = this.visibleItemCount(items);

         for (int i = 0; i < visibleItems; i++) {
            int iy = this.y + 2 + i * this.lineHeight;
            boolean hov = mx >= this.x && mx < this.x + w && my >= iy && my < iy + this.lineHeight;
            if (hov) {
               CompactSurfaces.tintedRow(ctx, this.x + 1, iy, w - 2, this.lineHeight, RiptideColors.popupHover());
            }

            String display = UiText.trimToWidth(this.font, items[i], Math.max(1, w - 8), this.theme.fontFor(UiTone.BODY), RiptideColors.textLight());
            UiText.draw(ctx, this.font, display, this.theme.fontFor(UiTone.BODY), hov ? -1 : RiptideColors.textLight(), this.x + 4, iy + 2, false);
         }
      }
   }

   public boolean handleClick(double mouseX, double mouseY, int button, RiptideContextMenu.ItemPickedCallback<T> onPick) {
      if (!this.isOpen()) {
         return false;
      } else if (button != 0) {
         this.close();
         return true;
      } else {
         String[] items = this.currentItems();
         int w = this.width(items);
         boolean inX = mouseX >= this.x && mouseX < this.x + w;
         if (inX) {
            int visibleItems = this.visibleItemCount(items);

            for (int i = 0; i < visibleItems; i++) {
               int iy = this.y + 2 + i * this.lineHeight;
               if (mouseY >= iy && mouseY < iy + this.lineHeight) {
                  T t = this.target;
                  String label = items[i];
                  this.close();
                  if (onPick != null) {
                     onPick.onPicked(t, label, i);
                  }

                  return true;
               }
            }
         }

         this.close();
         return true;
      }
   }

   @FunctionalInterface
   public interface ItemPickedCallback<T> {
      void onPicked(T var1, String var2, int var3);
   }
}
