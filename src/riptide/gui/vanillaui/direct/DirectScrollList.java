package riptide.gui.vanillaui.direct;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.components.CompactSurfaces;
import riptide.util.RiptideColors;
import riptide.util.RiptideText;

public final class DirectScrollList<T> {
   private final DirectScrollViewport viewport;
   private final Function<T, String> labelExtractor;
   private final BiConsumer<T, Boolean> selectionHandler;
   private final List<T> items = new ArrayList<>();
   private int selectedIndex = -1;
   private boolean enabled = true;
   private int borderColor = RiptideColors.secondary();
   private int backgroundColor = RiptideColors.listBg();
   private int selectedTextColor = RiptideColors.rowSelectedText();
   private int normalTextColor = -1;
   private int disabledTextColor = RiptideColors.textDim();
   private Function<T, String> badgeExtractor;
   private Function<T, Integer> badgeColorExtractor;
   private Function<T, String> secondaryTextExtractor;
   private int secondaryTextColor = RiptideColors.textDim();

   public DirectScrollList(
      int x, int y, int width, int height, int rowHeight, int scrollbarWidth, Function<T, String> labelExtractor, BiConsumer<T, Boolean> selectionHandler
   ) {
      this.viewport = new DirectScrollViewport(x, y, width, height, rowHeight, scrollbarWidth);
      this.labelExtractor = labelExtractor;
      this.selectionHandler = selectionHandler;
   }

   public void setItems(List<T> items) {
      this.items.clear();
      if (items != null) {
         this.items.addAll(items);
      }

      this.updateContentHeight();
   }

   public void setItems(T[] items) {
      this.items.clear();
      if (items != null) {
         for (T item : items) {
            this.items.add(item);
         }
      }

      this.updateContentHeight();
   }

   public void addItem(T item) {
      this.items.add(item);
      this.updateContentHeight();
   }

   public void clear() {
      this.items.clear();
      this.selectedIndex = -1;
      this.updateContentHeight();
   }

   private void updateContentHeight() {
      int contentHeight = this.items.size() * this.viewport.getRowHeight();
      this.viewport.setContentHeight(contentHeight);
   }

   public void setSelectedIndex(int index) {
      if (index >= -1 && index < this.items.size()) {
         this.selectedIndex = index;
         if (index >= 0 && this.selectionHandler != null) {
            this.selectionHandler.accept(this.items.get(index), true);
         }
      }
   }

   public int getSelectedIndex() {
      return this.selectedIndex;
   }

   public T getSelectedItem() {
      return this.selectedIndex >= 0 && this.selectedIndex < this.items.size() ? this.items.get(this.selectedIndex) : null;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setBadgeExtractor(Function<T, String> extractor, Function<T, Integer> colorExtractor) {
      this.badgeExtractor = extractor;
      this.badgeColorExtractor = colorExtractor;
   }

   public void setSecondaryTextExtractor(Function<T, String> extractor) {
      this.secondaryTextExtractor = extractor;
   }

   public void setColors(int border, int background, int selectedText, int normalText, int disabledText) {
      this.borderColor = border;
      this.backgroundColor = background;
      this.selectedTextColor = selectedText;
      this.normalTextColor = normalText;
      this.disabledTextColor = disabledText;
   }

   public void render(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY) {
      this.viewport.beginRender(ctx, this.borderColor, this.backgroundColor);

      try {
         this.renderContent(ctx, textRenderer, mouseX, mouseY);
      } finally {
         this.viewport.endRender(ctx);
      }

      this.viewport.renderScrollbar(ctx, mouseX, mouseY);
   }

   public void renderCustom(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY, DirectScrollList.CustomRowRenderer<T> customRenderer) {
      this.viewport.beginRender(ctx, this.borderColor, this.backgroundColor);

      try {
         this.viewport.renderSimple(ctx, this.items.size(), (index, bounds) -> {
            T item = this.items.get(index);
            boolean isSelected = index == this.selectedIndex;
            boolean isHovered = this.enabled && this.isHovered(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
            int originalY = this.viewport.getRowScreenY(index);
            customRenderer.render(ctx, textRenderer, item, index, isSelected, isHovered, bounds.x, bounds.y, bounds.width, bounds.height, originalY);
         });
      } finally {
         this.viewport.endRender(ctx);
      }

      this.viewport.renderScrollbar(ctx, mouseX, mouseY);
   }

   private void renderContent(GuiGraphicsExtractor ctx, Font textRenderer, double mouseX, double mouseY) {
      this.viewport.renderSimple(ctx, this.items.size(), (index, bounds) -> {
         T item = this.items.get(index);
         boolean isSelected = index == this.selectedIndex;
         boolean isHovered = this.enabled && this.isHovered(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height);
         CompactSurfaces.row(ctx, bounds.x, bounds.y, bounds.width, bounds.height, isHovered, isSelected);
         int textColor;
         if (!this.enabled) {
            textColor = this.disabledTextColor;
         } else if (isSelected) {
            textColor = this.selectedTextColor;
         } else {
            textColor = this.normalTextColor;
         }

         int availableWidth = bounds.width - 12;
         int textX = bounds.x + 6;
         int textY = bounds.y + 3;
         String label = this.labelExtractor.apply(item);
         if (label != null && !label.isEmpty()) {
            int reservedWidth = 0;
            String badge = this.badgeExtractor != null ? this.badgeExtractor.apply(item) : null;
            String secondary = this.secondaryTextExtractor != null ? this.secondaryTextExtractor.apply(item) : null;
            if (badge != null && !badge.isEmpty()) {
               reservedWidth += textRenderer.width(badge) + 8;
            }

            if (secondary != null && !secondary.isEmpty()) {
               reservedWidth += textRenderer.width(secondary) + 8;
            }

            int labelMaxWidth = Math.max(20, availableWidth - reservedWidth);
            String trimmedLabel = RiptideText.trimToWidth(textRenderer, label, labelMaxWidth, RiptideText.Tone.BODY);
            RiptideText.draw(ctx, textRenderer, trimmedLabel, textColor, textX, textY, false);
            if (badge != null && !badge.isEmpty()) {
               int labelWidth = RiptideText.width(textRenderer, trimmedLabel, RiptideText.Tone.BODY);
               int badgeX = textX + labelWidth + 4;
               int badgeColor = this.badgeColorExtractor != null ? this.badgeColorExtractor.apply(item) : -22016;
               RiptideText.draw(ctx, textRenderer, badge, badgeColor, badgeX, textY, false);
            }

            if (secondary != null && !secondary.isEmpty()) {
               int secondaryWidth = textRenderer.width(secondary);
               int secondaryX = bounds.x + bounds.width - secondaryWidth - 6;
               RiptideText.draw(ctx, textRenderer, secondary, this.secondaryTextColor, secondaryX, textY, false);
            }
         }
      });
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.enabled) {
         return false;
      } else if (this.viewport.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         if (this.viewport.contains(mouseX, mouseY)) {
            int relativeY = (int)Math.round(mouseY) - this.viewport.getY() - 1 + this.viewport.getScrollOffset();
            int rowIndex = relativeY / this.viewport.getRowHeight();
            if (rowIndex >= 0 && rowIndex < this.items.size()) {
               this.setSelectedIndex(rowIndex);
               return true;
            }
         }

         return false;
      }
   }

   public void mouseReleased() {
      this.viewport.mouseReleased();
   }

   public void mouseDragged(double mouseX, double mouseY) {
      this.viewport.mouseDragged(mouseX, mouseY);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      return this.viewport.mouseScrolled(mouseX, mouseY, amount);
   }

   public void scrollToSelected() {
      if (this.selectedIndex >= 0) {
         int rowTop = this.selectedIndex * this.viewport.getRowHeight();
         int rowBottom = rowTop + this.viewport.getRowHeight();
         int viewTop = this.viewport.getScrollOffset();
         int viewHeight = this.viewport.getHeight() - 2;
         int viewBottom = viewTop + viewHeight;
         if (rowTop < viewTop) {
            this.viewport.jumpTo(rowTop);
         } else if (rowBottom > viewBottom) {
            this.viewport.jumpTo(rowBottom - viewHeight);
         }
      }
   }

   public T getItem(int index) {
      return index >= 0 && index < this.items.size() ? this.items.get(index) : null;
   }

   public int size() {
      return this.items.size();
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   private boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
      return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
   }

   public int getX() {
      return this.viewport.getX();
   }

   public int getY() {
      return this.viewport.getY();
   }

   public int getWidth() {
      return this.viewport.getWidth();
   }

   public int getHeight() {
      return this.viewport.getHeight();
   }

   public int getContentWidth() {
      return this.viewport.getContentWidth();
   }

   public int getRowHeight() {
      return this.viewport.getRowHeight();
   }

   public int getScrollOffset() {
      return this.viewport.getScrollOffset();
   }

   public void jumpTo(int offset) {
      this.viewport.jumpTo(offset);
   }

   public void scrollBy(int rows) {
      this.viewport.scrollBy(rows);
   }

   public boolean contains(double x, double y) {
      return this.viewport.contains(x, y);
   }

   public boolean isScrollbarDragging() {
      return this.viewport.isScrollbarDragging();
   }

   public boolean isScrollbarHovered(double mouseX, double mouseY) {
      return this.viewport.isScrollbarHovered(mouseX, mouseY);
   }

   @FunctionalInterface
   public interface CustomRowRenderer<T> {
      void render(GuiGraphicsExtractor var1, Font var2, T var3, int var4, boolean var5, boolean var6, int var7, int var8, int var9, int var10, int var11);
   }
}
