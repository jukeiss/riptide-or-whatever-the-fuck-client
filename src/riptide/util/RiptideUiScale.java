package riptide.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RiptideUiScale {
   public static final int FIXED_GUI_SCALE = 2;
   private static final double[] ALLOWED_OVERLAY_SCALES = new double[]{0.5, 0.85, 1.0, 1.2, 1.5, 2.0};
   private static final double EPSILON = 1.0E-6;
   private static int overlayScaleDepth = 0;
   private static volatile RiptideUiScale.ScreenMetrics metrics;
   private static volatile RiptideUiScale.MultiplierCache multiplierCache;

   private RiptideUiScale() {
   }

   public static double toVirtual(double value) {
      return value / getOverlayDrawScale();
   }

   public static int toVirtualInt(double value) {
      double virtual = toVirtual(value);
      return virtual < 0.0 ? (int)Math.floor(virtual) : (int)Math.round(virtual);
   }

   private static RiptideUiScale.ScreenMetrics currentMetrics(Minecraft mc) {
      int rawW = mc.getWindow().getWidth();
      int rawH = mc.getWindow().getHeight();
      double multiplier = getOverlayScaleMultiplier();
      RiptideUiScale.ScreenMetrics m = metrics;
      if (m != null && m.rawW == rawW && m.rawH == rawH && m.multiplier == multiplier) {
         return m;
      } else {
         double scale = 2.0 * multiplier;
         int width = (int)(rawW / scale);
         if (rawW / scale > width) {
            width++;
         }

         int height = (int)(rawH / scale);
         if (rawH / scale > height) {
            height++;
         }

         m = new RiptideUiScale.ScreenMetrics(rawW, rawH, multiplier, width, height);
         metrics = m;
         return m;
      }
   }

   public static int getVirtualScreenWidth() {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.getWindow() != null ? currentMetrics(mc).virtualW : 0;
   }

   public static int getVirtualScreenHeight() {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.getWindow() != null ? currentMetrics(mc).virtualH : 0;
   }

   public static float getOverlayDrawScale() {
      Minecraft mc = Minecraft.getInstance();
      return mc != null && mc.getWindow() != null && mc.getWindow().getGuiScale() > 0 ? (float)getFixedGuiScale() / mc.getWindow().getGuiScale() : 1.0F;
   }

   public static double getOverlayScaleMultiplier() {
      try {
         double configScale = RiptideConfig.getGlobal().overlayScale;
         RiptideUiScale.MultiplierCache cached = multiplierCache;
         if (cached == null || cached.overlayScale != configScale) {
            cached = new RiptideUiScale.MultiplierCache(configScale, nearestAllowedOverlayScale(configScale));
            multiplierCache = cached;
         }

         return cached.multiplier;
      } catch (Throwable var3) {
         return 1.0;
      }
   }

   public static String getOverlayScaleLabel() {
      return formatOverlayScale(getOverlayScaleMultiplier());
   }

   public static List<Double> overlayScaleOptions() {
      List<Double> options = new ArrayList<>(ALLOWED_OVERLAY_SCALES.length);

      for (double scale : ALLOWED_OVERLAY_SCALES) {
         options.add(scale);
      }

      return List.copyOf(options);
   }

   public static double nearestAllowedOverlayScale(double multiplier) {
      if (!(Math.abs(multiplier - 0.75) < 1.0E-6) && !(Math.abs(multiplier - 0.9) < 1.0E-6)) {
         double best = 1.0;
         double bestDelta = Double.MAX_VALUE;

         for (double scale : ALLOWED_OVERLAY_SCALES) {
            double delta = Math.abs(multiplier - scale);
            if (delta < bestDelta) {
               best = scale;
               bestDelta = delta;
            }
         }

         return best;
      } else {
         return 0.85;
      }
   }

   public static double nextOverlayScaleMultiplier() {
      return adjacentOverlayScaleMultiplier(1);
   }

   public static double previousOverlayScaleMultiplier() {
      return adjacentOverlayScaleMultiplier(-1);
   }

   private static double adjacentOverlayScaleMultiplier(int delta) {
      double current = getOverlayScaleMultiplier();

      for (int i = 0; i < ALLOWED_OVERLAY_SCALES.length; i++) {
         if (Math.abs(current - ALLOWED_OVERLAY_SCALES[i]) < 1.0E-6) {
            return ALLOWED_OVERLAY_SCALES[Math.floorMod(i + delta, ALLOWED_OVERLAY_SCALES.length)];
         }
      }

      return 1.0;
   }

   public static String formatOverlayScale(double multiplier) {
      double normalized = nearestAllowedOverlayScale(multiplier);
      return Math.abs(normalized - Math.rint(normalized)) < 1.0E-6 ? Integer.toString((int)Math.rint(normalized)) + "x" : Double.toString(normalized) + "x";
   }

   public static void setOverlayScaleMultiplier(double multiplier) {
      double normalized = nearestAllowedOverlayScale(multiplier);
      RiptideConfig config = RiptideConfig.getGlobal();
      if (!(Math.abs(config.overlayScale - normalized) < 1.0E-6)) {
         config.overlayScale = normalized;
         config.save();

         try {
            RiptideOverlayManager.get().reclampAllOverlays();
         } catch (Throwable var6) {
         }
      }
   }

   private static double getFixedGuiScale() {
      return 2.0 * getOverlayScaleMultiplier();
   }

   public static boolean isOverlayScaleActive() {
      return overlayScaleDepth > 0;
   }

   public static boolean isFixedOverlayScaleActive() {
      return isOverlayScaleActive() && Math.abs(getOverlayDrawScale() - 1.0F) > 0.001F;
   }

   public static int virtualToFramebufferX(int x) {
      return (int)Math.floor(x * getFixedGuiScale());
   }

   public static int virtualToFramebufferY(int y) {
      return (int)Math.floor(y * getFixedGuiScale());
   }

   public static int virtualToFramebufferSize(int size) {
      return Math.max(0, (int)Math.ceil(size * getFixedGuiScale()));
   }

   public static void pushOverlayScale(GuiGraphicsExtractor context) {
      if (context != null) {
         context.pose().pushMatrix();
         if (overlayScaleDepth == 0) {
            float scale = getOverlayDrawScale();
            context.pose().scale(scale, scale);
         }

         overlayScaleDepth++;
      }
   }

   public static void popOverlayScale(GuiGraphicsExtractor context) {
      if (context != null) {
         if (overlayScaleDepth > 0) {
            overlayScaleDepth--;
         }

         context.pose().popMatrix();
      }
   }

   public static void enableOverlayScissor(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2) {
      if (context != null) {
         if (x2 > x1 && y2 > y1) {
            int expandRight = overlayScissorExpansionForEndpoint(x1, x2);
            int expandBottom = overlayScissorExpansionForEndpoint(y1, y2);
            int screenW = Math.max(0, getVirtualScreenWidth());
            int screenH = Math.max(0, getVirtualScreenHeight());
            int left = clamp(x1, 0, Math.max(0, screenW));
            int top = clamp(y1, 0, Math.max(0, screenH));
            int right = clamp(x2 + expandRight, left, Math.max(left, screenW + expandRight));
            int bottom = clamp(y2 + expandBottom, top, Math.max(top, screenH + expandBottom));
            context.enableScissor(left, top, right, bottom);
         } else {
            context.enableScissor(x1, y1, x1, y1);
         }
      }
   }

   private static int overlayScissorExpansionForEndpoint(int virtualStart, int virtualEnd) {
      int virtualLength = virtualEnd - virtualStart;
      if (isOverlayScaleActive() && virtualLength > 0) {
         double scale = getOverlayDrawScale();
         if (scale <= 0.0) {
            return 0;
         } else {
            int transformedStart = (int)Math.floor(virtualStart * scale + 1.0E-6);
            int desiredEnd = (int)Math.ceil(virtualEnd * scale - 1.0E-6);
            int desiredWidth = Math.max(0, desiredEnd - transformedStart);
            int currentWidth = (int)Math.floor(virtualLength * scale + 1.0E-6);
            if (currentWidth >= desiredWidth) {
               return 0;
            } else {
               int requiredVirtualLength = (int)Math.ceil((desiredWidth - 1.0E-6) / scale);
               return Math.max(1, requiredVirtualLength - virtualLength);
            }
         }
      } else {
         return 0;
      }
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }

   private record MultiplierCache(double overlayScale, double multiplier) {
   }

   private record ScreenMetrics(int rawW, int rawH, double multiplier, int virtualW, int virtualH) {
   }
}
