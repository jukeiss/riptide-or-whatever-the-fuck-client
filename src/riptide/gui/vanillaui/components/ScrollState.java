package riptide.gui.vanillaui.components;

public final class ScrollState {
   private int targetOffset = 0;

   public void restore(int offset) {
      this.targetOffset = Math.max(0, offset);
   }

   public void setTarget(int offset, int maxScroll) {
      this.targetOffset = clamp(offset, maxScroll);
   }

   public void jumpTo(int offset, int maxScroll) {
      this.targetOffset = clamp(offset, maxScroll);
   }

   public void nudge(double amount, float stepPixels, int maxScroll) {
      this.setTarget(this.targetOffset - Math.round((float)amount * stepPixels), maxScroll);
   }

   public void setFromThumb(CompactScrollbar.Metrics metrics, double mouseY, int grabOffset) {
      if (metrics == null) {
         this.jumpTo(0, 0);
      } else {
         this.setTarget(CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset), metrics.maxScroll());
      }
   }

   public void setFromThumbStepped(CompactScrollbar.Metrics metrics, double mouseY, int grabOffset, int stepSize) {
      if (metrics == null) {
         this.jumpTo(0, 0);
      } else {
         int rawScroll = CompactScrollbar.scrollFromThumb(metrics, mouseY, grabOffset);
         int steppedScroll = rawScroll / stepSize * stepSize;
         this.setTarget(steppedScroll, metrics.maxScroll());
      }
   }

   public int tick(float delta, int maxScroll) {
      this.targetOffset = clamp(this.targetOffset, maxScroll);
      return this.targetOffset;
   }

   public int targetOffset() {
      return this.targetOffset;
   }

   public int visualOffsetInt() {
      return this.targetOffset;
   }

   private static int clamp(int value, int max) {
      return Math.max(0, Math.min(max, value));
   }
}
