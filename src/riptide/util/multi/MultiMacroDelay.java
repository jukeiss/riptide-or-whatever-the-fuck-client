package riptide.util.multi;

import java.util.regex.Pattern;
import riptide.util.RiptideConfig;

public final class MultiMacroDelay {
   public static final int MIN_MS = 0;
   public static final int MAX_MS = 10000;
   public static final int STEP_MS = 100;
   public static final int TYPED_MAX_LENGTH = 4;
   private static final Pattern TYPED_SECONDS = Pattern.compile("\\d{0,2}(\\.\\d?)?");

   private MultiMacroDelay() {
   }

   public static int currentMs() {
      return clamp(RiptideConfig.getGlobal().multiMacroStartDelayMs);
   }

   public static void setMs(int ms) {
      RiptideConfig.getGlobal().multiMacroStartDelayMs = clamp(ms);
   }

   public static void persist() {
      RiptideConfig.getGlobal().save();
   }

   public static int clamp(int ms) {
      int bounded = Math.max(0, Math.min(10000, ms));
      return Math.round(bounded / 100.0F) * 100;
   }

   public static double ratio(int ms) {
      return clamp(ms) / 10000.0;
   }

   public static int fromMouse(double mouseX, int trackX, int trackWidth) {
      if (trackWidth <= 0) {
         return 0;
      } else {
         double ratio = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
         return clamp((int)Math.round(ratio * 10000.0));
      }
   }

   public static int nudge(int ms, int direction) {
      return clamp(clamp(ms) + Integer.signum(direction) * 100);
   }

   public static void nudgeAndPersist(int direction) {
      if (direction != 0) {
         setMs(nudge(currentMs(), direction));
         persist();
      }
   }

   public static boolean typable(String text) {
      return text != null && TYPED_SECONDS.matcher(text).matches();
   }

   public static String editText(int ms) {
      int value = clamp(ms);
      return value % 1000 == 0 ? Integer.toString(value / 1000) : value / 1000 + "." + value % 1000 / 100;
   }

   public static int fromTyped(String typed, int fallback) {
      double seconds;
      try {
         seconds = Double.parseDouble(typed == null ? "" : typed);
      } catch (NumberFormatException var6) {
         return clamp(fallback);
      }

      if (Double.isNaN(seconds)) {
         return clamp(fallback);
      } else {
         double bounded = Math.max(0.0, Math.min(10.0, seconds));
         return clamp((int)Math.round(bounded * 1000.0));
      }
   }

   public static String valueText(int ms) {
      int value = clamp(ms);
      if (value <= 0) {
         return "Off";
      } else {
         return value % 1000 == 0 ? value / 1000 + "s" : value / 1000 + "." + value % 1000 / 100 + "s";
      }
   }

   public static String countdownText(long remainingMs) {
      long tenths = Math.max(0L, (remainingMs + 99L) / 100L);
      return tenths / 10L + "." + tenths % 10L + "s";
   }

   public static long startAt(long launchedAt, int index, int gapMs) {
      return launchedAt + (long)Math.max(0, index) * Math.max(0, gapMs);
   }
}
