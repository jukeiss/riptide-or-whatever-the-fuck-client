package riptide.gui.vanillaui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiContexts;

public final class CompactScrollbar {
   public static final int GUTTER = 6;

   private CompactScrollbar() {
   }

   public static CompactScrollbar.Metrics compute(int contentPixels, int viewPixels, int trackX, int trackY, int trackWidth, int trackHeight, int scrollOffset) {
      return compute(contentPixels, viewPixels, trackX, trackY, trackWidth, trackHeight, (float)scrollOffset);
   }

   public static CompactScrollbar.Metrics compute(
      int contentPixels, int viewPixels, int trackX, int trackY, int trackWidth, int trackHeight, float scrollOffset
   ) {
      int right = trackX + trackWidth;
      int var14 = 6;
      trackX = right - var14;
      if (contentPixels > 0 && viewPixels > 0 && trackHeight > 0) {
         int maxScroll = Math.max(0, contentPixels - viewPixels);
         if (maxScroll <= 0) {
            return new CompactScrollbar.Metrics(trackX, trackY, var14, trackHeight, trackY, trackHeight, 0);
         } else {
            int thumbHeight = Math.max(12, (int)Math.round(trackHeight * ((double)viewPixels / Math.max(viewPixels, contentPixels))));
            thumbHeight = Math.min(trackHeight, thumbHeight);
            int travel = Math.max(1, trackHeight - thumbHeight);
            float clampedScroll = Math.max(0.0F, Math.min(scrollOffset, (float)maxScroll));
            int thumbY = trackY + Math.round(clampedScroll / maxScroll * travel);
            return new CompactScrollbar.Metrics(trackX, trackY, var14, trackHeight, thumbY, thumbHeight, maxScroll);
         }
      } else {
         return new CompactScrollbar.Metrics(trackX, trackY, var14, trackHeight, trackY, trackHeight, 0);
      }
   }

   public static void draw(GuiGraphicsExtractor context, CompactScrollbar.Metrics metrics, boolean hovered, boolean dragging) {
      if (metrics != null && metrics.hasScroll()) {
         Scrollbar.render(
            UiContexts.overlay(context, Minecraft.getInstance().font, -1, -1),
            new Scrollbar.Metrics(
               UiBounds.of(metrics.trackX(), metrics.trackY(), metrics.trackWidth(), metrics.trackHeight()),
               UiBounds.of(metrics.trackX(), metrics.thumbY(), metrics.trackWidth(), metrics.thumbHeight()),
               metrics.maxScroll()
            ),
            hovered,
            dragging
         );
      }
   }

   public static int scrollFromThumb(CompactScrollbar.Metrics metrics, double mouseY, int grabOffset) {
      if (metrics != null && metrics.hasScroll()) {
         int travel = Math.max(1, metrics.trackHeight() - metrics.thumbHeight());
         int thumbTop = (int)Math.round(mouseY) - grabOffset;
         thumbTop = Math.max(metrics.trackY(), Math.min(metrics.trackY() + travel, thumbTop));
         float progress = (float)(thumbTop - metrics.trackY()) / travel;
         return Math.max(0, Math.min(metrics.maxScroll(), Math.round(progress * metrics.maxScroll())));
      } else {
         return 0;
      }
   }

   public record Metrics(int trackX, int trackY, int trackWidth, int trackHeight, int thumbY, int thumbHeight, int maxScroll) {
      public boolean hasScroll() {
         return this.maxScroll > 0;
      }

      public boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.trackX && mouseX < this.trackX + this.trackWidth && mouseY >= this.trackY && mouseY < this.trackY + this.trackHeight;
      }

      public boolean overThumb(double mouseX, double mouseY) {
         return this.hasScroll()
            && mouseX >= this.trackX
            && mouseX < this.trackX + this.trackWidth
            && mouseY >= this.thumbY
            && mouseY < this.thumbY + this.thumbHeight;
      }
   }
}
