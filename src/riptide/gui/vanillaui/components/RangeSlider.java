package riptide.gui.vanillaui.components;

import riptide.gui.vanillaui.UiBounds;
import riptide.gui.vanillaui.UiColors;
import riptide.gui.vanillaui.UiContext;
import riptide.gui.vanillaui.UiRenderer;

public final class RangeSlider {
   private static final int KNOB_HALF = 2;
   public static final int THUMB_NONE = -1;
   public static final int THUMB_MIN = 0;
   public static final int THUMB_MAX = 1;

   private RangeSlider() {
   }

   public static void render(UiContext context, UiBounds bounds, double minRatio, double maxRatio, boolean hovered, int activeThumb) {
      UiColors colors = context.theme().colors();
      minRatio = clamp01(minRatio);
      maxRatio = clamp01(maxRatio);
      if (maxRatio < minRatio) {
         double swap = minRatio;
         minRatio = maxRatio;
         maxRatio = swap;
      }

      UiRenderer.frame(context.graphics(), bounds, colors.field, hovered ? colors.border : colors.borderSoft);
      UiBounds track = trackOf(bounds);
      UiRenderer.rect(context.graphics(), track, -869256388);
      int lowX = trackX(track, minRatio);
      int highX = trackX(track, maxRatio);
      if (highX > lowX) {
         UiRenderer.rect(context.graphics(), UiBounds.of(lowX, track.y(), highX - lowX, track.height()), colors.accent);
      }

      drawKnob(context, bounds, lowX, hovered || activeThumb == 0);
      drawKnob(context, bounds, highX, hovered || activeThumb == 1);
   }

   private static void drawKnob(UiContext context, UiBounds bounds, int centerX, boolean lit) {
      UiColors colors = context.theme().colors();
      int x = Math.max(bounds.x() + 2, Math.min(bounds.right() - 5, centerX - 2));
      UiRenderer.rect(context.graphics(), UiBounds.of(x, bounds.y() + 2, 5, Math.max(1, bounds.height() - 4)), lit ? colors.text : colors.muted);
   }

   public static UiBounds trackOf(UiBounds bounds) {
      int inset = Math.max(4, bounds.height() / 2 - 1);
      UiBounds track = bounds.inset(3, inset, 3, inset);
      return track.height() <= 0 ? UiBounds.of(bounds.x() + 3, bounds.y() + bounds.height() / 2, Math.max(1, bounds.width() - 6), 1) : track;
   }

   private static int trackX(UiBounds track, double ratio) {
      return track.x() + (int)Math.round(track.width() * clamp01(ratio));
   }

   public static int nearestThumb(double mouseX, UiBounds bounds, double minRatio, double maxRatio) {
      UiBounds track = trackOf(bounds);
      int lowX = trackX(track, minRatio);
      int highX = trackX(track, maxRatio);
      double toLow = Math.abs(mouseX - lowX);
      double toHigh = Math.abs(mouseX - highX);
      if (Math.abs(toLow - toHigh) < 0.5) {
         return mouseX < lowX ? 0 : 1;
      } else {
         return toLow <= toHigh ? 0 : 1;
      }
   }

   public static double ratio(double value, double min, double max) {
      return max <= min ? 0.0 : clamp01((value - min) / (max - min));
   }

   public static double valueFromMouse(double mouseX, UiBounds bounds, double min, double max, double step) {
      UiBounds track = trackOf(bounds);
      double ratio = track.width() <= 0 ? 0.0 : clamp01((mouseX - track.x()) / track.width());
      double value = min + ratio * (max - min);
      double safeStep = Math.max(1.0E-4, step);
      return min + Math.round((value - min) / safeStep) * safeStep;
   }

   private static double clamp01(double value) {
      return Math.max(0.0, Math.min(1.0, value));
   }
}
