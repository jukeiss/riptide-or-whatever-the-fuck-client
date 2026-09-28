package riptide.gui.vanillaui.direct;

import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiRenderer;
import riptide.gui.vanillaui.UiScissorStack;
import riptide.gui.vanillaui.components.CompactScrollbar;

public final class DirectScrollViewport {
   private final int x;
   private final int y;
   private final int width;
   private final int height;
   private final int rowHeight;
   private final int scrollbarWidth;
   private int contentHeight = 0;
   private int scrollOffset = 0;
   private boolean scrollbarDragging = false;
   private int scrollbarGrabOffset = 0;
   private int activeBorderColor = 0;
   private final int clipLeft;
   private final int clipTop;
   private final int clipRight;
   private final int clipBottom;

   public DirectScrollViewport(int x, int y, int width, int height, int rowHeight, int scrollbarWidth) {
      this.x = x;
      this.y = y;
      this.width = Math.max(4, width);
      this.height = Math.max(4, height);
      this.rowHeight = Math.max(1, rowHeight);
      this.scrollbarWidth = Math.max(0, scrollbarWidth);
      this.clipLeft = this.x;
      this.clipTop = this.y;
      this.clipRight = this.x + this.width;
      this.clipBottom = this.y + this.height;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public int getContentWidth() {
      return this.width - this.scrollbarWidth - 3;
   }

   public int getInnerWidth() {
      return this.width - 2;
   }

   public int getRowHeight() {
      return this.rowHeight;
   }

   public int getContentHeight() {
      return this.contentHeight;
   }

   public int getScrollOffset() {
      return this.scrollOffset;
   }

   private int getViewHeight() {
      return Math.max(0, this.clipBottom - this.clipTop);
   }

   private int getEffectiveViewHeight() {
      int viewHeight = this.getViewHeight();
      if (viewHeight > 0 && this.rowHeight > 0) {
         int fullRows = viewHeight / this.rowHeight;
         return fullRows > 0 ? fullRows * this.rowHeight : viewHeight;
      } else {
         return 0;
      }
   }

   private int getEffectiveClipBottom() {
      return this.clipTop + this.getEffectiveViewHeight();
   }

   public void setContentHeight(int height) {
      this.contentHeight = Math.max(0, height);
      this.clampScroll();
   }

   public int getMaxScroll() {
      int viewHeight = this.getEffectiveViewHeight();
      return Math.max(0, this.contentHeight - viewHeight);
   }

   public void jumpTo(int offset) {
      this.scrollOffset = this.quantizeToSteps(Math.max(0, Math.min(offset, this.getMaxScroll())));
   }

   public void scrollBy(int deltaRows) {
      int deltaPixels = deltaRows * this.rowHeight;
      this.jumpTo(this.scrollOffset + deltaPixels);
   }

   public void scrollToBottom() {
      this.jumpTo(this.getMaxScroll());
   }

   public void scrollToTop() {
      this.jumpTo(0);
   }

   private void clampScroll() {
      int max = this.getMaxScroll();
      if (this.scrollOffset > max) {
         this.scrollOffset = this.quantizeToSteps(max);
      }
   }

   private int quantizeToSteps(int offset) {
      return this.rowHeight <= 0 ? 0 : offset / this.rowHeight * this.rowHeight;
   }

   public int getVisibleRows() {
      int viewHeight = this.getEffectiveViewHeight();
      return viewHeight > 0 && this.rowHeight > 0 ? (viewHeight + this.rowHeight - 1) / this.rowHeight : 0;
   }

   public int getFirstVisibleRow() {
      return this.rowHeight <= 0 ? 0 : this.scrollOffset / this.rowHeight;
   }

   public int getLastVisibleRow() {
      int viewHeight = this.getEffectiveViewHeight();
      if (viewHeight > 0 && this.rowHeight > 0) {
         int lastVisiblePixel = viewHeight - 1;
         return this.scrollOffset >= 0 && this.scrollOffset <= Integer.MAX_VALUE - lastVisiblePixel
            ? (this.scrollOffset + lastVisiblePixel) / this.rowHeight
            : Integer.MAX_VALUE / this.rowHeight;
      } else {
         return -1;
      }
   }

   public int getRowScreenY(int rowIndex) {
      return this.y + rowIndex * this.rowHeight - this.scrollOffset;
   }

   public boolean isRowVisible(int rowY) {
      int effectiveClipBottom = this.getEffectiveClipBottom();
      return rowY + this.rowHeight > this.clipTop && rowY < effectiveClipBottom;
   }

   public int clampY(int y) {
      return Math.max(this.clipTop, Math.min(y, this.getEffectiveClipBottom()));
   }

   public int clampHeight(int y, int height) {
      int maxBottom = Math.min(y + height, this.getEffectiveClipBottom());
      return Math.max(0, maxBottom - y);
   }

   public DirectScrollViewport.DrawBounds getRowDrawBounds(int rowY) {
      if (!this.isRowVisible(rowY)) {
         return null;
      } else {
         int drawY = Math.max(rowY, this.clipTop);
         int drawBottom = Math.min(rowY + this.rowHeight, this.getEffectiveClipBottom());
         int drawHeight = drawBottom - drawY;
         return drawHeight <= 0 ? null : new DirectScrollViewport.DrawBounds(this.clipLeft, drawY, this.clipRight - this.clipLeft, drawHeight);
      }
   }

   public void beginRender(GuiGraphicsExtractor ctx, int borderColor, int fillColor) {
      this.activeBorderColor = borderColor;
      UiRenderer.rect(ctx, UiBounds.of(this.x, this.y, this.width, this.height), fillColor);
      UiScissorStack.global().push(ctx, UiBounds.of(this.clipLeft, this.clipTop, this.clipRight - this.clipLeft, this.clipBottom - this.clipTop));
   }

   public void renderContent(
      GuiGraphicsExtractor ctx, Font textRenderer, List<? extends DirectScrollViewport.ScrollRow> rows, DirectScrollViewport.RowRenderer renderer
   ) {
      int startRow = Math.max(0, this.getFirstVisibleRow() - 1);
      int endRow = Math.min(rows.size(), startRow + this.getVisibleRows() + 2);

      for (int i = startRow; i < endRow; i++) {
         int rowY = this.getRowScreenY(i);
         DirectScrollViewport.DrawBounds bounds = this.getRowDrawBounds(rowY);
         if (bounds != null) {
            DirectScrollViewport.ScrollRow row = rows.get(i);
            renderer.render(ctx, textRenderer, row, i, bounds.x, bounds.y, bounds.width, bounds.height, rowY);
         }
      }
   }

   public void renderSimple(GuiGraphicsExtractor ctx, int totalRows, BiConsumer<Integer, DirectScrollViewport.DrawBounds> rowRenderer) {
      int startRow = Math.max(0, this.getFirstVisibleRow() - 1);
      int endRow = Math.min(totalRows, startRow + this.getVisibleRows() + 2);

      for (int i = startRow; i < endRow; i++) {
         int rowY = this.getRowScreenY(i);
         DirectScrollViewport.DrawBounds bounds = this.getRowDrawBounds(rowY);
         if (bounds != null) {
            rowRenderer.accept(i, bounds);
         }
      }
   }

   public void endRender(GuiGraphicsExtractor ctx) {
      UiScissorStack.global().pop(ctx);
      UiRenderer.outline(ctx, UiBounds.of(this.x, this.y, this.width, this.height), this.activeBorderColor);
   }

   public void renderScrollbar(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
      if (this.getMaxScroll() > 0) {
         int trackX = this.x + this.width - this.scrollbarWidth - 2;
         int trackY = this.y + 2;
         int trackWidth = this.scrollbarWidth;
         int trackHeight = this.height - 4;
         CompactScrollbar.Metrics metrics = CompactScrollbar.compute(
            this.contentHeight, this.getEffectiveViewHeight(), trackX, trackY, trackWidth, trackHeight, this.scrollOffset
         );
         boolean hovered = metrics.contains(mouseX, mouseY);
         CompactScrollbar.draw(ctx, metrics, hovered, this.scrollbarDragging);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (!this.contains(mouseX, mouseY)) {
         return false;
      } else if (this.getMaxScroll() <= 0) {
         return false;
      } else {
         int deltaRows = amount > 0.0 ? -1 : 1;
         this.scrollBy(deltaRows);
         return true;
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.getMaxScroll() <= 0) {
         return false;
      } else {
         CompactScrollbar.Metrics metrics = this.getScrollbarMetrics();
         if (metrics == null) {
            return false;
         } else if (metrics.overThumb(mouseX, mouseY)) {
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = (int)Math.round(mouseY) - metrics.thumbY();
            return true;
         } else if (metrics.contains(mouseX, mouseY)) {
            int newScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, this.scrollbarGrabOffset);
            this.jumpTo(this.quantizeToSteps(newScroll));
            this.scrollbarDragging = true;
            this.scrollbarGrabOffset = metrics.thumbHeight() / 2;
            return true;
         } else {
            return false;
         }
      }
   }

   public void mouseReleased() {
      this.scrollbarDragging = false;
      this.scrollbarGrabOffset = 0;
   }

   public void mouseDragged(double mouseX, double mouseY) {
      if (this.scrollbarDragging) {
         CompactScrollbar.Metrics metrics = this.getScrollbarMetrics();
         if (metrics != null) {
            int newScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, this.scrollbarGrabOffset);
            this.jumpTo(this.quantizeToSteps(newScroll));
         }
      }
   }

   public boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }

   public CompactScrollbar.Metrics getScrollbarMetrics() {
      if (this.getMaxScroll() <= 0) {
         return null;
      } else {
         int trackX = this.x + this.width - this.scrollbarWidth - 2;
         int trackY = this.y + 2;
         int trackWidth = this.scrollbarWidth;
         int trackHeight = this.height - 4;
         return CompactScrollbar.compute(this.contentHeight, this.getEffectiveViewHeight(), trackX, trackY, trackWidth, trackHeight, this.scrollOffset);
      }
   }

   public boolean isScrollbarDragging() {
      return this.scrollbarDragging;
   }

   public boolean isScrollbarHovered(double mouseX, double mouseY) {
      CompactScrollbar.Metrics metrics = this.getScrollbarMetrics();
      return metrics != null && metrics.contains(mouseX, mouseY);
   }

   public static class DrawBounds {
      public final int x;
      public final int y;
      public final int width;
      public final int height;

      public DrawBounds(int x, int y, int width, int height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }
   }

   @FunctionalInterface
   public interface RowRenderer {
      void render(GuiGraphicsExtractor var1, Font var2, DirectScrollViewport.ScrollRow var3, int var4, int var5, int var6, int var7, int var8, int var9);
   }

   public interface ScrollRow {
      String getLabel();

      boolean isSelected();

      boolean isEnabled();
   }
}
