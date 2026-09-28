package riptide.util.macro;

import java.util.Locale;

public enum InteractTiming {
   BEFORE,
   WITH,
   AFTER,
   AFTER_PLUS,
   CUSTOM;

   public static InteractTiming parse(String raw, InteractTiming fallback) {
      if (raw == null) {
         return fallback;
      } else {
         String var2 = raw.trim().toUpperCase(Locale.ROOT);
         switch (var2) {
            case "BEFORE":
            case "TICK_BEFORE":
               return BEFORE;
            case "WITH":
            case "SAME_TICK":
               return WITH;
            case "AFTER":
               return AFTER;
            case "AFTER_PLUS":
            case "AFTER+":
            case "TICK_AFTER":
               return AFTER_PLUS;
            case "CUSTOM":
               return CUSTOM;
            default:
               return fallback;
         }
      }
   }
}
