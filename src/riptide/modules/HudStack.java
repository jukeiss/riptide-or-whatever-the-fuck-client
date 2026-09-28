package riptide.modules;

import java.util.Arrays;

/**
 * Per-frame corner allocator for the HUD suite. Panels that share a corner stack
 * (in RiptideHudSuiteMixin's render order) instead of drawing over each other.
 * A panel's own margin still applies: it is a minimum distance from the edge, so a
 * bigger margin can push a panel further in but never back into one already placed.
 */
public final class HudStack {
   private static final int GAP = 2;
   // index 0 = left side, 1 = right side
   private static final int[] TOP = new int[2];
   private static final int[] BOTTOM = new int[2];

   static {
      reset();
   }

   private HudStack() {
   }

   public static void reset() {
      Arrays.fill(TOP, Integer.MIN_VALUE);
      Arrays.fill(BOTTOM, Integer.MAX_VALUE);
   }

   /** Reserves {@code height} pixels in {@code corner} and returns the panel's top y. */
   static int y(String corner, int margin, int height, int screenHeight) {
      int side = corner.contains("Right") ? 1 : 0;
      if (corner.contains("Bottom")) {
         int y = screenHeight - margin - height;
         if (BOTTOM[side] != Integer.MAX_VALUE) {
            y = Math.min(y, BOTTOM[side] - GAP - height);
         }

         BOTTOM[side] = y;
         return y;
      } else {
         int y = margin;
         if (TOP[side] != Integer.MIN_VALUE) {
            y = Math.max(y, TOP[side] + GAP);
         }

         TOP[side] = y + height;
         return y;
      }
   }
}
