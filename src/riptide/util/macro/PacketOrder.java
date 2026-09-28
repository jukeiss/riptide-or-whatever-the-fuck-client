package riptide.util.macro;

import java.util.Locale;

public enum PacketOrder {
   INSTANT,
   GRIM;

   public static PacketOrder parse(String raw, PacketOrder fallback) {
      if (raw == null) {
         return fallback;
      } else {
         String var2 = raw.trim().toUpperCase(Locale.ROOT);
         switch (var2) {
            case "INSTANT":
               return INSTANT;
            case "GRIM":
               return GRIM;
            default:
               return fallback;
         }
      }
   }
}
