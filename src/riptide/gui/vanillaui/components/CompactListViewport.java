package riptide.gui.vanillaui.components;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.direct.DirectViewport;

public final class CompactListViewport {
   private CompactListViewport() {
   }

   public static CompactListViewport.Layout layout(
      int x, int y, int width, int height, int rowCount, int rowHeight, int rowStep, int scrollOffset, int scrollbarWidth, int scrollbarGutter
   ) {
      return layout(x, y, width, height, rowCount, rowHeight, rowStep, scrollOffset, scrollbarWidth, scrollbarGutter, 2, 5, true);
   }

   public static CompactListViewport.Layout layout(
      int x,
      int y,
      int width,
      int height,
      int rowCount,
      int rowHeight,
      int rowStep,
      int scrollOffset,
      int scrollbarWidth,
      int scrollbarGutter,
      int contentInset
   ) {
      return layout(x, y, width, height, rowCount, rowHeight, rowStep, scrollOffset, scrollbarWidth, scrollbarGutter, contentInset, 5, true);
   }

   public static CompactListViewport.Layout layout(
      int x,
      int y,
      int width,
      int height,
      int rowCount,
      int rowHeight,
      int rowStep,
      int scrollOffset,
      int scrollbarWidth,
      int scrollbarGutter,
      int contentInset,
      int scrollbarRightInset,
      boolean alignRows
   ) {
      int safeWidth = Math.max(1, width);
      int safeHeight = Math.max(1, height);
      int safeRowHeight = Math.max(1, rowHeight);
      int safeRowStep = Math.max(1, rowStep);
      int safeInset = Math.max(0, contentInset);
      int innerHeight = Math.max(1, safeHeight - safeInset * 2);
      int viewHeight = alignRows ? alignViewportHeight(innerHeight, safeRowStep) : innerHeight;
      int contentHeight = Math.max(0, rowCount) * safeRowStep;
      int maxScroll = Math.max(0, contentHeight - viewHeight);
      int clampedScroll = Math.max(0, Math.min(scrollOffset, maxScroll));
      CompactScrollbar.Metrics scrollbar = CompactScrollbar.compute(
         contentHeight,
         viewHeight,
         x + safeWidth - Math.max(1, scrollbarRightInset),
         y + safeInset,
         Math.max(1, scrollbarWidth),
         Math.max(1, safeHeight - safeInset * 2),
         clampedScroll
      );
      int contentWidth = Math.max(1, safeWidth - safeInset * 2 - (scrollbar.hasScroll() ? Math.max(0, scrollbarGutter) : 0));
      return new CompactListViewport.Layout(
         x, y, safeWidth, safeHeight, safeRowHeight, safeRowStep, viewHeight, contentHeight, maxScroll, clampedScroll, contentWidth, safeInset, scrollbar
      );
   }

   private static int alignViewportHeight(int innerHeight, int rowStep) {
      int safeHeight = Math.max(0, innerHeight);
      int safeStep = Math.max(1, rowStep);
      if (safeHeight != 0 && safeStep > 1) {
         int aligned = safeHeight / safeStep * safeStep;
         return aligned > 0 ? aligned : Math.min(safeHeight, safeStep);
      } else {
         return safeHeight;
      }
   }

   public record Layout(
      int x,
      int y,
      int width,
      int height,
      int rowHeight,
      int rowStep,
      int viewHeight,
      int contentHeight,
      int maxScroll,
      int scrollOffset,
      int contentWidth,
      int contentInset,
      CompactScrollbar.Metrics scrollbar
   ) {
      public void drawFrame(GuiGraphicsExtractor graphics, boolean focused) {
         CompactListRenderer.drawFrame(graphics, this.x, this.y, this.width, this.height, focused);
      }

      public void beginRows(GuiGraphicsExtractor graphics) {
         DirectViewport.current(1.0F)
            .enableScissor(
               graphics,
               this.x + this.contentInset,
               this.y + this.contentInset,
               this.x + this.contentInset + this.contentWidth,
               this.y + this.contentInset + this.viewHeight
            );
      }

      public void endRows(GuiGraphicsExtractor graphics) {
         DirectViewport.current(1.0F).disableScissor(graphics);
      }

      public void forEachVisibleRow(int rowCount, BiConsumer<Integer, Integer> consumer) {
         int firstVisible = this.scrollOffset / this.rowStep;
         int rowY = this.y + this.contentInset - this.scrollOffset % this.rowStep;
         int rowBottom = this.y + this.contentInset + this.viewHeight;

         for (int index = firstVisible; index < rowCount && rowY < rowBottom; rowY += this.rowStep) {
            if (rowY + this.rowHeight > this.y + this.contentInset) {
               consumer.accept(index, rowY);
            }

            index++;
         }
      }

      public void drawScrollbar(GuiGraphicsExtractor graphics, double mouseX, double mouseY, boolean dragging) {
         CompactScrollbar.draw(graphics, this.scrollbar, this.scrollbar.contains(mouseX, mouseY), dragging);
      }
   }
}
